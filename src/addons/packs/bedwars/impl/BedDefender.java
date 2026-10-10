import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BedDefender
 *
 * Finds the nearest bed and walls it in with blocks from your hotbar, strongest first:
 *   obsidian > end stone > hardened clay > planks > logs > glass > anything else > wool
 * Layer 1 is every block touching the bed (sides, ends, roof).
 *
 * Placement works the way AutoBlockIn does it:
 *  - the rotation is set in EventSilentRotation and the block is placed in EventSilentRotation.Post from
 *    event.getRayTrace(), i.e. the ray of the rotation that is actually being sent this tick
 *  - the aim point is the middle of the biggest visible patch of the face, and when that patch is smaller
 *    than the mouse GCD allows, the aim is dithered by GCD steps until the ray lands
 *  - blocks that keep missing or take too long are skipped for a while
 *  - roof blocks that have to be placed against the bed are placed while really holding the sneak key
 */
@ModuleInfo(name = "BedDefender", description = "Walls your nearest bed in with the strongest blocks in your hotbar", category = ModuleCategory.PLAYER)
public class BedDefender extends Module {

    public final DoubleProperty layers = new DoubleProperty("Layers", new DoubleValue(1, 3, 1, 1));

    // Fixed settings
    private static final double BED_RANGE = 5;
    private static final double REACH = 4.5;
    private static final double FOV = 360;
    private static final int DELAY = 2;
    private static final int DELAY_JITTER = 1;
    private static final double ROT_SPEED = 30;
    private static final SilentRotationManager.MovementFix MOVEMENT_FIX = SilentRotationManager.MovementFix.SILENT;

    private static final int BED_RESCAN_TICKS = 10;
    private static final int STEP_TIMEOUT_TICKS = 40;
    private static final int SKIP_TICKS = 60;
    private static final int RESOLVE_AFTER_MISSES = 4;
    private static final int MAX_RECOVERIES = 3;
    private static final int GRID = 5;
    private static final double INSET = 0.12;

    private static final double[][] DITHER = {
            {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}
    };

    private BlockPos bedA;
    private BlockPos bedB;
    private int rescanTimer;
    private int cooldown;
    private int tickCount;

    private Plan plan;
    private BlockPos locked;
    private int stepTicks;
    private int missTicks;
    private int recoveries;
    private final Map<BlockPos, Integer> skipUntil = new HashMap<>();

    private final Set<BlockPos> noBedSupport = new HashSet<>();
    private BlockPos verifyTarget;
    private int verifyTicks;

    private boolean sneakHeld;
    private int sneakTicks;

    private int savedSlot = -1;
    private int lastSetSlot = -1;
    private int lastActiveTick;
    private int pauseUntil;

    private static class Aim {
        final Vec3 point;
        final float yaw, pitch;
        final double clearance;

        Aim(Vec3 point, float yaw, float pitch, double clearance) {
            this.point = point;
            this.yaw = yaw;
            this.pitch = pitch;
            this.clearance = clearance;
        }
    }

    /** One planned placement: aim at `aim` on `face` of `support` to fill `target` with the block in `slot`. */
    private static class Plan {
        final BlockPos target;
        final BlockPos support;
        final EnumFacing face;
        final Aim aim;
        final int slot;
        final float turn;
        final boolean stable;

        Plan(BlockPos target, BlockPos support, EnumFacing face, Aim aim, int slot, float turn, boolean stable) {
            this.target = target;
            this.support = support;
            this.face = face;
            this.aim = aim;
            this.slot = slot;
            this.turn = turn;
            this.stable = stable;
        }
    }

    @Override
    protected void onDisable() {
        if (sneakHeld)
            releaseSneak();
        restoreSlot();
        plan = null;
        locked = null;
        bedA = null;
        bedB = null;
        cooldown = 0;
        stepTicks = 0;
        missTicks = 0;
        recoveries = 0;
        skipUntil.clear();
        noBedSupport.clear();
        verifyTarget = null;
    }

    @Override
    public boolean isSwappingHotbar() {
        return savedSlot != -1;
    }

    // ------------------------------------------------------------------ events

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        tickCount++;
        if (mc.thePlayer == null || mc.theWorld == null)
            return;
        if (cooldown > 0)
            cooldown--;

        // did the last bed-supported block actually appear? if not, stop using the bed for that spot
        if (verifyTarget != null && --verifyTicks <= 0) {
            if (mc.theWorld.getBlockState(verifyTarget).getBlock().getMaterial().isReplaceable())
                noBedSupport.add(verifyTarget);
            verifyTarget = null;
        }

        // the player took over the hotbar: leave it alone for a moment
        if (savedSlot != -1 && mc.thePlayer.inventory.currentItem != lastSetSlot) {
            savedSlot = -1;
            lastSetSlot = -1;
            pauseUntil = tickCount + 20;
        }
        // nothing left to place for a while: go back to the item you were holding
        if (savedSlot != -1 && tickCount - lastActiveTick > 8)
            restoreSlot();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        plan = null;
        if (mc.currentScreen != null || cooldown > 0 || tickCount < pauseUntil) {
            if (sneakHeld)
                releaseSneak();
            return;
        }

        SilentRotationManager srm = Arsenic.getArsenic().getSilentRotationManager();
        plan = findPlan(srm.yaw, srm.pitch);

        // a bed is only a valid support while sneaking, so hold the real sneak key for those
        boolean needSneak = plan != null && mc.theWorld.getBlockState(plan.support).getBlock() instanceof BlockBed;
        if (needSneak) {
            if (!sneakHeld) {
                sneakHeld = true;
                sneakTicks = 0;
                setSneakKey(true);
            } else {
                sneakTicks++;
            }
        } else if (sneakHeld) {
            releaseSneak();
        }

        if (plan == null) {
            stepTicks = 0;
            return;
        }

        ensureSlot(plan.slot);

        stepTicks++;
        if (stepTicks > STEP_TIMEOUT_TICKS) {
            skip(plan.target);
            plan = null;
            return;
        }

        event.setBlockUserInput(true);
        // SILENT remaps your WASD so you still walk where your camera points
        SilentRotationManager.MovementFix fix = MOVEMENT_FIX;
        event.setMovementFix(fix);
        event.setJumpFix(fix != SilentRotationManager.MovementFix.OFF);

        float reqYaw = plan.aim.yaw;
        float reqPitch = plan.aim.pitch;
        if (plan.aim.clearance < settledFloorRad()) {
            // the patch is smaller than what the mouse GCD can hit reliably: wiggle by GCD steps until the ray lands
            int i = stepTicks % DITHER.length;
            float g = RotationUtils.getGCD();
            reqYaw += (float) (DITHER[i][0] * g);
            reqPitch += (float) (DITHER[i][1] * g);
        }
        event.setYaw(reqYaw);
        event.setPitch(reqPitch);
        event.setSpeed(Math.max(1f, (float) ROT_SPEED * (0.95f + (float) Math.random() * 0.1f)));
        event.setPreventDuplicateLook(true);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onRotationPost = event -> {
        if (plan == null)
            return;

        MovingObjectPosition mop = event.getRayTrace();
        if (!hitsTarget(mop)) {
            onMiss(event.getYaw(), event.getPitch());
            return;
        }
        missTicks = 0;

        BlockPos neighbor = mop.getBlockPos();
        EnumFacing facing = mop.sideHit;
        boolean bedHit = mc.theWorld.getBlockState(neighbor).getBlock() instanceof BlockBed;
        // right clicking a bed opens the sleep prompt unless the client has really been sneaking for a moment
        if (bedHit && (sneakTicks < 2 || !mc.thePlayer.isSneaking()))
            return;

        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held == null || strength(held) < 0 || mc.thePlayer.inventory.currentItem != plan.slot)
            return;
        if (!((ItemBlock) held.getItem()).canPlaceBlockOnSide(mc.theWorld, neighbor, facing, mc.thePlayer, held))
            return;

        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, held, neighbor, facing, mop.hitVec)) {
            mc.thePlayer.swingItem();
            if (bedHit) {
                verifyTarget = plan.target;
                verifyTicks = 8;
            }
            cooldown = DELAY + (int) (Math.random() * (DELAY_JITTER + 1));
            lastActiveTick = tickCount;
            locked = null;
            plan = null;
            stepTicks = 0;
            missTicks = 0;
            recoveries = 0;
        }
    };

    private boolean hitsTarget(MovingObjectPosition mop) {
        return mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && plan != null && plan.target.equals(mop.getBlockPos().offset(mop.sideHit));
    }

    /** The rotation is where it should be but the ray keeps missing: re-solve the aim, and give up after a few tries. */
    private void onMiss(float yaw, float pitch) {
        double ang = angularDist(yaw, pitch, plan.aim.yaw, plan.aim.pitch);
        if (ang > Math.max(plan.aim.clearance, settledFloorRad())) {
            missTicks = 0;
            return;
        }
        if (++missTicks < RESOLVE_AFTER_MISSES)
            return;
        missTicks = 0;
        if (++recoveries > MAX_RECOVERIES) {
            recoveries = 0;
            skip(plan.target);
        }
        locked = null;
    }

    private void skip(BlockPos target) {
        skipUntil.put(target, tickCount + SKIP_TICKS);
        locked = null;
        stepTicks = 0;
        missTicks = 0;
    }

    // ------------------------------------------------------------------ hotbar and sneak

    private void ensureSlot(int slot) {
        int current = mc.thePlayer.inventory.currentItem;
        if (current != slot) {
            if (savedSlot == -1)
                savedSlot = current;
            mc.thePlayer.inventory.currentItem = slot;
        }
        lastSetSlot = slot;
        lastActiveTick = tickCount;
    }

    private void restoreSlot() {
        if (savedSlot != -1 && mc.thePlayer != null && mc.thePlayer.inventory.currentItem == lastSetSlot)
            mc.thePlayer.inventory.currentItem = savedSlot;
        savedSlot = -1;
        lastSetSlot = -1;
    }

    private void setSneakKey(boolean down) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), down);
    }

    /** Lets go of the sneak key, but keeps it down if you are physically holding it. */
    private void releaseSneak() {
        sneakHeld = false;
        sneakTicks = 0;
        if (mc.gameSettings == null)
            return;
        int code = mc.gameSettings.keyBindSneak.getKeyCode();
        boolean physical = code < 0 ? Mouse.isButtonDown(code + 100) : Keyboard.isKeyDown(code);
        KeyBinding.setKeyBindState(code, physical);
    }

    // ------------------------------------------------------------------ planning

    private Plan findPlan(float yaw, float pitch) {
        int slot = bestBlockSlot();
        if (slot == -1) {
            locked = null;
            return null;
        }
        if (!updateBed()) {
            locked = null;
            return null;
        }

        Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);

        // keep working on the same block until it is placed (no flicking between targets)
        if (locked != null) {
            Plan kept = planFor(locked, slot, stack, eyes, yaw, pitch);
            if (kept != null)
                return kept;
            locked = null;
            stepTicks = 0;
        }

        Plan best = null;
        int bestRing = -1;
        for (BlockPos target : candidates(eyes)) {
            int ring = ringOf(target);
            if (best != null && ring > bestRing)
                break; // inner ring first; within a ring pick the best aim
            Plan p = planFor(target, slot, stack, eyes, yaw, pitch);
            if (p != null && (best == null || better(p, best))) {
                best = p;
                bestRing = ring;
            }
        }
        if (best != null)
            locked = best.target;
        return best;
    }

    /** Stable aims first (smallest turn), otherwise the one with the most room for error. */
    private static boolean better(Plan a, Plan b) {
        if (a.stable != b.stable)
            return a.stable;
        if (a.stable)
            return a.turn < b.turn;
        return a.aim.clearance > b.aim.clearance;
    }

    /** Best way to fill `target` right now, or null. */
    private Plan planFor(BlockPos target, int slot, ItemStack stack, Vec3 eyes, float yaw, float pitch) {
        Integer until = skipUntil.get(target);
        if (until != null) {
            if (until > tickCount)
                return null;
            skipUntil.remove(target);
        }
        if (!mc.theWorld.getBlockState(target).getBlock().getMaterial().isReplaceable())
            return null;

        ItemBlock itemBlock = (ItemBlock) stack.getItem();
        double halfFov = FOV / 2;
        Plan best = null;

        for (EnumFacing side : EnumFacing.values()) {
            BlockPos support = target.offset(side);
            IBlockState supportState = mc.theWorld.getBlockState(support);
            Block supportBlock = supportState.getBlock();
            if (supportBlock.getMaterial().isReplaceable() || !supportBlock.canCollideCheck(supportState, false))
                continue;
            if (supportBlock instanceof BlockBed && noBedSupport.contains(target))
                continue;

            EnumFacing clicked = side.getOpposite();
            if (!itemBlock.canPlaceBlockOnSide(mc.theWorld, support, clicked, mc.thePlayer, stack))
                continue;

            Aim aim = aimFor(support, clicked, eyes);
            if (aim == null)
                continue;
            if (Math.abs(RotationUtils.getYawDifference(aim.yaw, yaw)) > halfFov)
                continue;

            float turn = angleBetween(aim.yaw, aim.pitch, yaw, pitch);
            Plan p = new Plan(target, support, clicked, aim, slot, turn, aim.clearance >= minStableRad());
            if (best == null || better(p, best))
                best = p;
        }
        return best;
    }

    /**
     * Samples a grid on the face, keeps the points a real ray can hit, and aims at the one furthest from anything
     * blocked or the face edge (the middle of the biggest visible patch). Clearance is how far, in radians of
     * rotation, that point is from the nearest blocked spot.
     */
    private Aim aimFor(BlockPos support, EnumFacing face, Vec3 eyes) {
        double maxReach = REACH;
        Vec3[][] pts = new Vec3[GRID][GRID];
        boolean[][] vis = new boolean[GRID][GRID];
        boolean any = false;

        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                Vec3 p = facePoint(support, face, cell(i), cell(j));
                pts[i][j] = p;
                if (p == null || eyes.distanceTo(p) > maxReach)
                    continue;
                MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyes, extend(eyes, p), false, false, true);
                if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                        && support.equals(mop.getBlockPos()) && mop.sideHit == face) {
                    vis[i][j] = true;
                    any = true;
                }
            }
        }
        if (!any)
            return null;

        int bi = -1, bj = -1;
        double bestScore = -1;
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                if (!vis[i][j])
                    continue;
                double d = Double.MAX_VALUE;
                for (int i2 = -1; i2 <= GRID; i2++) {
                    for (int j2 = -1; j2 <= GRID; j2++) {
                        boolean blocked = i2 < 0 || i2 >= GRID || j2 < 0 || j2 >= GRID || !vis[i2][j2];
                        if (blocked)
                            d = Math.min(d, Math.hypot(i - i2, j - j2));
                    }
                }
                double score = d + Math.random() * 0.35; // small random spread so it never lands on the same spot
                if (score > bestScore) {
                    bestScore = score;
                    bi = i;
                    bj = j;
                }
            }
        }

        Vec3 point = pts[bi][bj];
        float[] rots = RotationUtils.rotationsTo(eyes, point);
        double clearance = Double.MAX_VALUE;
        for (int i2 = -1; i2 <= GRID; i2++) {
            for (int j2 = -1; j2 <= GRID; j2++) {
                boolean blocked = i2 < 0 || i2 >= GRID || j2 < 0 || j2 >= GRID || !vis[i2][j2];
                if (!blocked)
                    continue;
                Vec3 q = facePoint(support, face, cell(i2), cell(j2));
                if (q == null)
                    continue;
                float[] rq = RotationUtils.rotationsTo(eyes, q);
                clearance = Math.min(clearance, angularDist(rots[0], rots[1], rq[0], rq[1]));
            }
        }
        if (clearance == Double.MAX_VALUE)
            clearance = 1.0;
        return new Aim(point, rots[0], rots[1], clearance);
    }

    private static double cell(int i) {
        return 0.1 + 0.8 * i / (GRID - 1.0);
    }

    private static double settledFloorRad() {
        return Math.toRadians(RotationUtils.getGCD()) * 3.0 + 0.0015;
    }

    private static double minStableRad() {
        return settledFloorRad() * 2.0;
    }

    /** Positions around the bed, closest ring first, then nearest to the player. */
    private List<BlockPos> candidates(Vec3 eyes) {
        int maxLayer = (int) layers.getValue().getInput();
        // if the bed can't be used as a support, the roof needs the blocks beside it first (ring 2)
        if (!noBedSupport.isEmpty())
            maxLayer = Math.max(maxLayer, 2);
        int baseY = bedA.getY();

        int minX = Math.min(bedA.getX(), bedB.getX()) - maxLayer;
        int maxX = Math.max(bedA.getX(), bedB.getX()) + maxLayer;
        int minZ = Math.min(bedA.getZ(), bedB.getZ()) - maxLayer;
        int maxZ = Math.max(bedA.getZ(), bedB.getZ()) + maxLayer;

        List<BlockPos> list = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = baseY; y <= baseY + maxLayer; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    int d = ringOf(p);
                    if (d >= 1 && d <= maxLayer)
                        list.add(p);
                }
            }
        }

        Collections.sort(list, (a, b) -> {
            int ra = ringOf(a), rb = ringOf(b);
            if (ra != rb)
                return Integer.compare(ra, rb);
            return Double.compare(distSq(a, eyes), distSq(b, eyes));
        });
        return list;
    }

    private int ringOf(BlockPos p) {
        return Math.min(manhattan(p, bedA), manhattan(p, bedB));
    }

    private static int manhattan(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    private static double distSq(BlockPos p, Vec3 v) {
        double dx = p.getX() + 0.5 - v.xCoord;
        double dy = p.getY() + 0.5 - v.yCoord;
        double dz = p.getZ() + 0.5 - v.zCoord;
        return dx * dx + dy * dy + dz * dz;
    }

    // ------------------------------------------------------------------ bed lookup

    private boolean updateBed() {
        if (bedA != null && !(mc.theWorld.getBlockState(bedA).getBlock() instanceof BlockBed)) {
            bedA = null;
            bedB = null;
        }
        if (bedA != null && --rescanTimer > 0)
            return true;
        rescanTimer = BED_RESCAN_TICKS;

        double range = BED_RANGE;
        int r = (int) Math.ceil(range);
        BlockPos origin = new BlockPos(mc.thePlayer);
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (!(mc.theWorld.getBlockState(pos).getBlock() instanceof BlockBed))
                        continue;
                    double d = mc.thePlayer.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (d <= range * range && d < bestDist) {
                        bestDist = d;
                        best = pos;
                    }
                }
            }
        }

        if (best == null) {
            bedA = null;
            bedB = null;
            return false;
        }

        IBlockState state = mc.theWorld.getBlockState(best);
        EnumFacing facing = state.getValue(BlockBed.FACING);
        boolean foot = state.getValue(BlockBed.PART) == BlockBed.EnumPartType.FOOT;
        BlockPos newB = foot ? best.offset(facing) : best.offset(facing.getOpposite());
        if (bedA == null || (!best.equals(bedA) && !best.equals(bedB))) {
            noBedSupport.clear(); // different bed
            skipUntil.clear();
        }
        bedA = best;
        bedB = newB;
        return true;
    }

    // ------------------------------------------------------------------ blocks

    /** Hotbar slot holding the strongest usable block, or -1. */
    private int bestBlockSlot() {
        int bestSlot = -1;
        int bestScore = -1;
        int bestCount = -1;
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            int score = strength(stack);
            if (score < 0)
                continue;
            if (score > bestScore || (score == bestScore && stack.stackSize > bestCount)) {
                bestScore = score;
                bestCount = stack.stackSize;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    /** Higher is stronger. -1 means "never place this". */
    private int strength(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof ItemBlock))
            return -1;
        Block b = ((ItemBlock) stack.getItem()).getBlock();
        if (b instanceof BlockFalling || b instanceof ITileEntityProvider || b instanceof BlockBed
                || b == Blocks.tnt || b == Blocks.web || b == Blocks.slime_block)
            return -1;
        if (!b.isFullCube() || !b.getMaterial().isSolid())
            return -1;

        if (b == Blocks.obsidian) return 1000;
        if (b == Blocks.end_stone) return 900;
        if (b == Blocks.hardened_clay || b == Blocks.stained_hardened_clay) return 800;
        if (b == Blocks.planks) return 700;
        if (b == Blocks.log || b == Blocks.log2) return 600;
        if (b == Blocks.glass || b == Blocks.stained_glass) return 300;
        if (b == Blocks.wool) return 100;

        // anything else: rank by blast resistance, still above wool
        return 110 + (int) Math.min(b.getExplosionResistance(null) * 10f, 180f);
    }

    // ------------------------------------------------------------------ geometry helpers

    /**
     * A point on the `face` of the block at `pos`, kept INSET away from the edges for a and b in 0..1.
     * Values outside 0..1 land just past the inset area and are used to probe for the edge.
     */
    private Vec3 facePoint(BlockPos pos, EnumFacing face, double a, double b) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        block.setBlockBoundsBasedOnState(mc.theWorld, pos);
        AxisAlignedBB box = block.getSelectedBoundingBox(mc.theWorld, pos);
        if (box == null)
            return null;

        double x, y, z;
        switch (face) {
            case UP:
            case DOWN:
                x = lerp(box.minX, box.maxX, a);
                z = lerp(box.minZ, box.maxZ, b);
                y = face == EnumFacing.UP ? box.maxY : box.minY;
                break;
            case NORTH:
            case SOUTH:
                x = lerp(box.minX, box.maxX, a);
                y = lerp(box.minY, box.maxY, b);
                z = face == EnumFacing.SOUTH ? box.maxZ : box.minZ;
                break;
            default: // WEST, EAST
                z = lerp(box.minZ, box.maxZ, a);
                y = lerp(box.minY, box.maxY, b);
                x = face == EnumFacing.EAST ? box.maxX : box.minX;
                break;
        }
        return new Vec3(x, y, z);
    }

    private static double lerp(double min, double max, double t) {
        if (max - min <= INSET * 2)
            return (min + max) / 2 + (t - 0.5) * (max - min);
        return (min + INSET) + ((max - INSET) - (min + INSET)) * t;
    }

    private static Vec3 extend(Vec3 eyes, Vec3 point) {
        Vec3 dir = point.subtract(eyes).normalize();
        return point.addVector(dir.xCoord * 0.1, dir.yCoord * 0.1, dir.zCoord * 0.1);
    }

    private static Vec3 lookVector(float yaw, float pitch) {
        float f = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3((double) (f1 * f2), (double) f3, (double) (f * f2));
    }

    /** Angle in radians between two look directions. */
    private static double angularDist(float y1, float p1, float y2, float p2) {
        Vec3 a = lookVector(y1, p1);
        Vec3 b = lookVector(y2, p2);
        double dot = a.xCoord * b.xCoord + a.yCoord * b.yCoord + a.zCoord * b.zCoord;
        return Math.acos(MathHelper.clamp_double(dot, -1.0, 1.0));
    }

    private static float angleBetween(float yawA, float pitchA, float yawB, float pitchB) {
        float dYaw = RotationUtils.getYawDifference(yawA, yawB);
        float dPitch = pitchA - pitchB;
        return (float) Math.sqrt(dYaw * dYaw + dPitch * dPitch);
    }
}

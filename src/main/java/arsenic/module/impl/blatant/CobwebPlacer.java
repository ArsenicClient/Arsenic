package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.gui.themes.ThemeManager;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.Group;
import arsenic.module.property.PropertyGroup;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.ContainerUtils;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Places cobwebs. Manual mode is a one-press macro that puts a web on the block you look at and turns itself off; Auto
 * mode predicts where the nearest enemy will land and silently aims a web at that spot.
 */
@ModuleInfo(name = "CobwebPlacer", description = "Places cobwebs on your crosshair or under a nearby enemy", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class CobwebPlacer extends Module {

    private static final double GRAVITY = 0.08, AIR_DRAG = 0.91, VERTICAL_DRAG = 0.98, GROUND_SLIDE = 0.999;
    private static final float AIM_TOLERANCE = 4f;

    public enum Mode { Manual, Auto }
    public enum SwitchBack { Slot, Original }
    public enum Swap { None, Swap }
    public enum AutoSwitchBack { Off, Slot, Original }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Manual);
    public final RangeProperty delay = new RangeProperty("Delay (ms)", new RangeValue(0, 200, 30, 50, 1));

    @ShowWhen(property = "Mode", is = "Manual")
    public final BooleanProperty silent = new BooleanProperty("Silent", false);
    @ShowWhen(property = "Mode", is = "Manual")
    @ShowWhen(property = "Silent", is = "false")
    public final EnumProperty<SwitchBack> switchBack = new EnumProperty<>("Switch Back", SwitchBack.Slot);
    @ShowWhen(property = "Mode", is = "Manual")
    @ShowWhen(property = "Silent", is = "false")
    @ShowWhen(property = "Switch Back", is = "Slot")
    public final DoubleProperty switchSlot = new DoubleProperty("Switch Slot", new DoubleValue(1, 9, 1, 1));

    @ShowWhen(property = "Mode", is = "Auto")
    public final DoubleProperty targetRange = new DoubleProperty("Target Range", new DoubleValue(1, 16, 6, 0.5));
    @ShowWhen(property = "Mode", is = "Auto")
    public final DoubleProperty leadTicks = new DoubleProperty("Lead Ticks", new DoubleValue(1, 30, 10, 1));
    @ShowWhen(property = "Mode", is = "Auto")
    public final BooleanProperty onlyWhenFalling = new BooleanProperty("Only When Falling", false);
    @ShowWhen(property = "Mode", is = "Auto")
    @ShowWhen(property = "Only When Falling", is = "true")
    public final DoubleProperty maxWebsPerFall = new DoubleProperty("Max Webs Per Fall", new DoubleValue(1, 10, 1, 1));
    @ShowWhen(property = "Mode", is = "Auto")
    public final EnumProperty<Swap> swapMode = new EnumProperty<>("Swap", Swap.Swap);
    @ShowWhen(property = "Mode", is = "Auto")
    public final BooleanProperty fromInventory = new BooleanProperty("From Inventory", true);
    @ShowWhen(property = "Mode", is = "Auto")
    public final BooleanProperty headTrap = new BooleanProperty("Head Trap", false);
    @ShowWhen(property = "Mode", is = "Auto")
    public final EnumProperty<AutoSwitchBack> autoSwitchBack = new EnumProperty<>("Auto Switch Back", AutoSwitchBack.Off);
    @ShowWhen(property = "Mode", is = "Auto")
    @ShowWhen(property = "Auto Switch Back", is = "Slot")
    public final DoubleProperty autoSwitchSlot = new DoubleProperty("Auto Switch Slot", new DoubleValue(1, 9, 1, 1));

    @PropertyGroup(Group.VISUAL)
    @ShowWhen(property = "Mode", is = "Auto")
    public final BooleanProperty render = new BooleanProperty("Render", true);

    private final HotbarUse swap = new HotbarUse();
    private final MSTimer timer = new MSTimer();
    private double nextDelay;

    // Manual mode
    private int step;
    private int originalSlot = -1;

    // Auto mode
    private BlockHitResult placeHit;
    private Vec3 aim;
    /** Main inventory menu slot pulled into the hand last tick, put back on the next one. */
    private int inventorySwapBack = -1;
    private int inventorySwapHotbar = -1;
    /** Hotbar slot to go to on the next free tick (Auto Switch Back: Slot), or -1. */
    private int pendingSlot = -1;
    private Player fallTarget;
    private int websThisFall;
    private BlockPos renderPos;
    private boolean renderHead;

    @Override
    public boolean isSwappingHotbar() {
        return swap.active() || inventorySwapBack != -1 || (mode.getValue() == Mode.Manual && originalSlot != -1);
    }

    @Override
    protected void onEnable() {
        step = 0;
        originalSlot = -1;
        nextDelay = 0;
        timer.setTime(0);
        fallTarget = null;
        websThisFall = 0;
        pendingSlot = -1;
        clearPlan();
        renderPos = null;
    }

    @Override
    protected void onDisable() {
        swap.release();
        restoreInventory();
        pendingSlot = -1;
        // turned off halfway through the manual macro: put the hand back where it was
        if (originalSlot != -1 && mc.player != null && mc.player.getMainHandItem().is(Items.COBWEB))
            mc.player.getInventory().setSelectedSlot(originalSlot);
        originalSlot = -1;
        step = 0;
        fallTarget = null;
        clearPlan();
        renderPos = null;
    }

    private void clearPlan() {
        placeHit = null;
        aim = null;
    }

    private boolean delayPassed() {
        return timer.hasTimeElapsed((long) nextDelay);
    }

    private void rollDelay() {
        nextDelay = delay.getValue().getRandomInRange();
        timer.reset();
    }

    private boolean canAct() {
        return mc.gui.screen() == null && mc.isWindowActive() && !mc.player.isUsingItem();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (mode.getValue() == Mode.Manual) {
            manualTick();
            return;
        }
        clearPlan();
        swap.tick();
        restoreInventory();
        if (pendingSlot != -1) {
            if (!swap.active())
                mc.player.getInventory().setSelectedSlot(pendingSlot);
            pendingSlot = -1;
        }
        if (!canAct() || !delayPassed()) {
            if (!canAct())
                renderPos = null;
            return;
        }
        planAuto();
        if (aim == null)
            return;
        float[] rots = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), aim);
        event.setYaw(rots[0]);
        event.setPitch(rots[1]);
        event.setSpeed(180f);
        event.setPreventDuplicateLook(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (mode.getValue() != Mode.Auto || placeHit == null || aim == null)
            return;
        float[] want = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), aim);
        if (Math.abs(RotationUtils.getYawDifference(event.getYaw(), want[0])) > AIM_TOLERANCE
                || Math.abs(event.getPitch() - want[1]) > AIM_TOLERANCE) {
            clearPlan();
            return;
        }
        // the rotation that actually went out must land on the support face we picked
        BlockHitResult hit = event.getRayTrace();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(placeHit.getBlockPos())
                || hit.getDirection() != placeHit.getDirection()
                || mc.player.getEyePosition(1f).distanceTo(hit.getLocation()) > mc.player.blockInteractionRange()) {
            clearPlan();
            return;
        }
        if (placeAuto(hit)) {
            if (onlyWhenFalling.getValue())
                websThisFall++;
            rollDelay();
        }
        clearPlan();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        if (mode.getValue() != Mode.Auto || !render.getValue() || renderPos == null)
            return;
        int colour = RenderUtils.withAlpha(ThemeManager.getMainColor(), 140);
        RenderUtils.renderBlock(renderPos, colour, true, true);
        if (renderHead)
            RenderUtils.renderBlock(renderPos.above(), RenderUtils.withAlpha(ThemeManager.getDarkerColor(), 140), true, true);
    };

    // ---------------------------------------------------------------
    //  Manual mode
    // ---------------------------------------------------------------

    private void manualTick() {
        if (!canAct() || !delayPassed())
            return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            setEnabled(false);
            return;
        }
        int web = HotbarUse.find(s -> s.is(Items.COBWEB));
        if (silent.getValue()) {
            if (mc.player.getMainHandItem().is(Items.COBWEB)) {
                place(InteractionHand.MAIN_HAND, hit);
            } else if (mc.player.getOffhandItem().is(Items.COBWEB)) {
                place(InteractionHand.OFF_HAND, hit);
            } else if (web != -1) {
                // switch, place and switch back inside one tick
                int prev = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(web);
                place(InteractionHand.MAIN_HAND, hit);
                mc.player.getInventory().setSelectedSlot(prev);
            }
            setEnabled(false);
            return;
        }
        switch (step) {
            case 0 -> {
                if (web == -1) {
                    setEnabled(false);
                    return;
                }
                originalSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(web);
            }
            case 1 -> {
                if (mc.player.getMainHandItem().is(Items.COBWEB))
                    place(InteractionHand.MAIN_HAND, hit);
            }
            default -> {
                int back = switchBack.getValue() == SwitchBack.Slot ? (int) switchSlot.getValue().getInput() - 1 : originalSlot;
                if (back >= 0 && back <= 8)
                    mc.player.getInventory().setSelectedSlot(back);
                originalSlot = -1;
                setEnabled(false);
                return;
            }
        }
        step++;
        rollDelay();
    }

    private void place(InteractionHand hand, BlockHitResult hit) {
        mc.gameMode.useItemOn(mc.player, hand, hit);
        PlayerUtils.swingItem();
    }

    // ---------------------------------------------------------------
    //  Auto mode
    // ---------------------------------------------------------------

    private void planAuto() {
        Player target = nearestEnemy();
        if (target == null || webBetween(target)) {
            renderPos = null;
            return;
        }
        boolean held = mc.player.getMainHandItem().is(Items.COBWEB) || mc.player.getOffhandItem().is(Items.COBWEB);
        int hotbar = HotbarUse.find(s -> s.is(Items.COBWEB));
        int inventory = hotbar == -1 && fromInventory.getValue() ? inventoryWeb() : -1;
        if (!held && hotbar == -1 && inventory == -1
                || swapMode.getValue() == Swap.None && !held && inventory == -1) {
            renderPos = null;
            return;
        }

        Vec3 landing;
        if (onlyWhenFalling.getValue()) {
            if (fallTarget != target || heightAboveGround(target) < 0.3) {
                fallTarget = target;
                websThisFall = 0;
            }
            if (websThisFall >= (int) maxWebsPerFall.getValue().getInput()) {
                renderPos = null;
                return;
            }
            landing = fallLanding(target);
            if (landing == null || landing.y >= target.getY() - 0.5) {
                renderPos = null;
                return;
            }
        } else {
            landing = heightAboveGround(target) < 0.3 ? groundSlide(target) : airborne(target);
        }
        if (landing == null) {
            renderPos = null;
            return;
        }

        BlockPos feet = BlockPos.containing(landing);
        renderPos = feet;
        renderHead = headTrap.getValue();
        Vec3 eyes = mc.player.getEyePosition(1f);
        double reach = mc.player.blockInteractionRange();
        BlockPos self = mc.player.blockPosition();
        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        double bestDelta = Double.MAX_VALUE;
        for (int i = 0; i < (headTrap.getValue() ? 2 : 1); i++) {
            BlockPos pos = i == 0 ? feet : feet.above();
            if (pos.equals(self) || pos.equals(self.above()))
                continue;
            BlockState state = mc.level.getBlockState(pos);
            if (!state.canBeReplaced() || !state.getFluidState().isEmpty())
                continue;
            for (Direction dir : Direction.values()) {
                BlockPos support = pos.relative(dir);
                BlockState supportState = mc.level.getBlockState(support);
                if (supportState.isAir() || supportState.canBeReplaced() || !supportState.getFluidState().isEmpty())
                    continue;
                Direction face = dir.getOpposite();
                Vec3 point = new Vec3(support.getX() + 0.5 + face.getStepX() * 0.5, support.getY() + 0.5 + face.getStepY() * 0.5,
                        support.getZ() + 0.5 + face.getStepZ() * 0.5);
                if (eyes.distanceTo(point) > reach)
                    continue;
                BlockHitResult hit = mc.level.clip(new ClipContext(eyes, point.add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(-0.01)),
                        ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
                if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(support) || hit.getDirection() != face
                        || eyes.distanceTo(hit.getLocation()) > reach)
                    continue;
                float[] rots = RotationUtils.rotationsTo(eyes, point);
                float dy = Mth.wrapDegrees(rots[0] - yaw), dp = rots[1] - pitch;
                double delta = Math.sqrt(dy * dy + dp * dp);
                if (delta < bestDelta) {
                    bestDelta = delta;
                    placeHit = hit;
                    aim = point;
                }
            }
        }
    }

    /** Puts a web on the hit, swapping to it the way the settings ask. Returns whether it was placed. */
    private boolean placeAuto(BlockHitResult hit) {
        InteractionHand hand;
        if (mc.player.getMainHandItem().is(Items.COBWEB)) {
            hand = InteractionHand.MAIN_HAND;
        } else if (mc.player.getOffhandItem().is(Items.COBWEB)) {
            hand = InteractionHand.OFF_HAND;
        } else {
            hand = InteractionHand.MAIN_HAND;
            int hotbar = HotbarUse.find(s -> s.is(Items.COBWEB));
            if (hotbar != -1 && swapMode.getValue() == Swap.Swap) {
                if (autoSwitchBack.getValue() == AutoSwitchBack.Original)
                    swap.switchOnce(hotbar);
                else
                    mc.player.getInventory().setSelectedSlot(hotbar);
            } else if (fromInventory.getValue()) {
                int inventory = inventoryWeb();
                if (inventory == -1)
                    return false;
                // pulled into the held slot for this tick, put back on the next one
                int held = mc.player.getInventory().getSelectedSlot();
                ContainerUtils.swap(inventory, held);
                inventorySwapBack = inventory;
                inventorySwapHotbar = held;
            } else {
                return false;
            }
            if (!mc.player.getMainHandItem().is(Items.COBWEB))
                return false;
        }
        place(hand, hit);
        if (autoSwitchBack.getValue() == AutoSwitchBack.Slot && inventorySwapBack == -1) {
            int slot = (int) autoSwitchSlot.getValue().getInput() - 1;
            if (slot != mc.player.getInventory().getSelectedSlot())
                pendingSlot = slot;
        }
        return true;
    }

    private void restoreInventory() {
        if (inventorySwapBack != -1 && mc.player != null && mc.gameMode != null && inventorySwapHotbar >= 0)
            ContainerUtils.swap(inventorySwapBack, inventorySwapHotbar);
        inventorySwapBack = -1;
        inventorySwapHotbar = -1;
    }

    /** A main inventory menu slot (9-35) holding cobweb, or -1. */
    private static int inventoryWeb() {
        for (int i = 9; i <= 35; i++) {
            var stack = ContainerUtils.getItemStack(i);
            if (stack != null && stack.is(Items.COBWEB))
                return i;
        }
        return -1;
    }

    private Player nearestEnemy() {
        double range = targetRange.getValue().getInput();
        Player best = null;
        for (Player p : mc.level.players()) {
            if (!p.isAlive() || !TargetManager.isValidTarget(p) || mc.player.distanceTo(p) > range)
                continue;
            if (best == null || mc.player.distanceToSqr(p) < mc.player.distanceToSqr(best))
                best = p;
        }
        return best;
    }

    /** Whether a cobweb already sits on the line from our eyes to theirs. */
    private static boolean webBetween(Player target) {
        Vec3 from = mc.player.getEyePosition(1f);
        Vec3 to = target.getEyePosition(1f);
        Vec3 dir = to.subtract(from).normalize();
        double length = from.distanceTo(to);
        AABB box = target.getBoundingBox();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (double d = 0; d < length; d += 0.1) {
            double x = from.x + dir.x * d, y = from.y + dir.y * d, z = from.z + dir.z * d;
            if (box.contains(x, y, z))
                return false;
            pos.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
            if (mc.level.getBlockState(pos).is(Blocks.COBWEB))
                return true;
        }
        return false;
    }

    /** How far the player is above the first block straight down. */
    private static double heightAboveGround(Player p) {
        Vec3 pos = p.position();
        BlockHitResult hit = mc.level.clip(new ClipContext(pos, pos.add(0, -256, 0), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK ? p.getY() - hit.getLocation().y : 256;
    }

    private static Vec3 motion(Player p) {
        return new Vec3(p.getX() - p.xo, Math.min(p.getY() - p.yo, p.getDeltaMovement().y), p.getZ() - p.zo);
    }

    /** Lead Ticks ahead along an airborne arc, stopping on the first block it would land on. */
    private Vec3 airborne(Player target) {
        Vec3 pos = target.position();
        Vec3 last = pos;
        Vec3 vel = motion(target);
        int bottom = mc.level.getMinY();
        int ticks = (int) leadTicks.getValue().getInput();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        for (int i = 0; i < ticks; i++) {
            last = pos;
            pos = pos.add(vel);
            below.set(Mth.floor(pos.x), Mth.floor(pos.y - 0.1), Mth.floor(pos.z));
            if (!mc.level.getBlockState(below).isAir())
                return new Vec3(pos.x, below.getY() + 1.0, pos.z);
            if (pos.y <= bottom)
                return null;
            vel = new Vec3(vel.x * AIR_DRAG, (vel.y - GRAVITY) * VERTICAL_DRAG, vel.z * AIR_DRAG);
        }
        return last;
    }

    /** Lead Ticks ahead for a grounded target, sliding along the ground until it runs out of floor. */
    private Vec3 groundSlide(Player target) {
        Vec3 pos = target.position();
        double vx = target.getX() - target.xo, vz = target.getZ() - target.zo;
        int ticks = (int) leadTicks.getValue().getInput();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        for (int i = 0; i < ticks; i++) {
            pos = pos.add(vx, 0, vz);
            below.set(Mth.floor(pos.x), Mth.floor(pos.y - 0.1), Mth.floor(pos.z));
            if (mc.level.getBlockState(below).isAir())
                break;
            vx *= GROUND_SLIDE;
            vz *= GROUND_SLIDE;
            if (Math.abs(vx) < 0.005 && Math.abs(vz) < 0.005)
                break;
        }
        return pos;
    }

    /** Only When Falling: walks the fall in quarter ticks until the target's footprint meets ground, and returns the landing. */
    private static Vec3 fallLanding(Player target) {
        Vec3 vel = motion(target);
        AABB box = target.getBoundingBox();
        double half = Math.max((box.maxX - box.minX) / 2.0, 0.15);
        int bottom = mc.level.getMinY();
        double x = target.getX(), y = target.getY(), z = target.getZ();
        double vx = vel.x, vy = vel.y, vz = vel.z;
        for (int tick = 0; tick < 80; tick++) {
            double sx = vx / 4, sy = vy / 4, sz = vz / 4;
            for (int sub = 0; sub < 4; sub++) {
                double nx = x + sx, ny = y + sy, nz = z + sz;
                if (vy <= 0 && groundUnder(nx, ny, nz, half))
                    return new Vec3(nx, Mth.floor(ny - 1.0E-4) + 1.0, nz);
                x = nx;
                y = ny;
                z = nz;
            }
            if (y <= bottom)
                return null;
            vx *= AIR_DRAG;
            vy = (vy - GRAVITY) * VERTICAL_DRAG;
            vz *= AIR_DRAG;
        }
        return null;
    }

    private static boolean groundUnder(double x, double y, double z, double half) {
        int minX = Mth.floor(x - half + 0.001), maxX = Mth.floor(x + half - 0.001);
        int minZ = Mth.floor(z - half + 0.001), maxZ = Mth.floor(z + half - 0.001);
        int by = Mth.floor(y - 0.001);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                pos.set(bx, by, bz);
                BlockState state = mc.level.getBlockState(pos);
                if (!state.isAir() && !state.getCollisionShape(mc.level, pos).isEmpty())
                    return true;
            }
        }
        return false;
    }
}

package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Respawn anchor macro: on the block you look at it places an anchor, charges an empty anchor with glowstone and
 * detonates a charged one with a totem (or any non-glowstone item) in hand. Silent Aim picks the nearest spot inside
 * the FOV and aims at it server-side when the crosshair is not already on one.
 */
@ModuleInfo(name = "AnchorPlacer", description = "Places, charges and detonates respawn anchors for you", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class AnchorPlacer extends Module {

    private static final int SCAN_RADIUS = 5;
    private static final double AIM_REACH = 4.4;
    private static final float AIM_TOLERANCE = 6f;
    /** The same block is not touched again this soon, so a late block update cannot cause a double interaction. */
    private static final long SAME_BLOCK_MS = 120;

    public enum FireMode {
        /** Turns itself off after one action, for a bind pressed once per step. */
        OneShot,
        /** Keeps going until turned off. */
        Persistent
    }

    public enum SwapMode {
        /** Only uses what is already in your hand. */
        None,
        /** Switches the hotbar to the anchor, glowstone or totem it needs. */
        Swap
    }

    public final EnumProperty<FireMode> fireMode = new EnumProperty<>("Fire Mode", FireMode.OneShot);
    public final RangeProperty delay = new RangeProperty("Delay (ms)", new RangeValue(0, 200, 30, 50, 1));
    public final EnumProperty<SwapMode> swapMode = new EnumProperty<>("Swap", SwapMode.Swap);
    @ShowWhen(property = "Swap", is = "Swap")
    public final BooleanProperty swapBack = new BooleanProperty("Swap Back", false);
    public final BooleanProperty silentAim = new BooleanProperty("Silent Aim", false);
    @ShowWhen(property = "Silent Aim", is = "true")
    public final DoubleProperty silentAimFov = new DoubleProperty("Silent Aim FOV", new DoubleValue(0, 180, 180, 1));

    private final HotbarUse swap = new HotbarUse();
    private final MSTimer timer = new MSTimer();
    private final BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
    private double nextDelay;
    private boolean finishing;
    private BlockPos lastPos;
    private long lastTime;
    /** The block Silent Aim is turning towards this tick, and the point on it. */
    private BlockPos aimPos;
    private Vec3 aim;

    @Override
    public boolean isSwappingHotbar() {
        return swap.active();
    }

    @Override
    protected void onEnable() {
        finishing = false;
        nextDelay = 0;
        lastPos = null;
        aimPos = null;
        aim = null;
    }

    @Override
    protected void onDisable() {
        swap.release();
        finishing = false;
        aimPos = null;
        aim = null;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        swap.tick();
        aimPos = null;
        aim = null;
        if (finishing) {
            setEnabled(false);
            return;
        }
        if (mc.gui.screen() != null || !mc.isWindowActive() || mc.player.isUsingItem())
            return;
        if (!timer.hasTimeElapsed(nextDelay))
            return;

        BlockHitResult crosshair = mc.hitResult instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK ? b : null;
        if (crosshair != null && actionable(crosshair)) {
            if (!recentlyTouched(crosshair.getBlockPos()))
                act(crosshair);
            return;
        }
        if (!silentAim.getValue()) {
            // Nothing to do where you are looking: a one-shot press is spent.
            done();
            return;
        }
        planSilentAim();
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
        if (aim == null || aimPos == null)
            return;
        float[] want = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), aim);
        boolean onTarget = Math.abs(RotationUtils.getYawDifference(event.getYaw(), want[0])) <= AIM_TOLERANCE
                && Math.abs(event.getPitch() - want[1]) <= AIM_TOLERANCE;
        BlockPos planned = aimPos;
        aimPos = null;
        aim = null;
        if (!onTarget)
            return;
        BlockHitResult hit = event.getRayTrace();
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(planned) || !actionable(hit)
                || recentlyTouched(planned))
            return;
        act(hit);
    };

    /** Detonates a charged anchor, otherwise charges an empty one or places a new one against the block. */
    private void act(BlockHitResult hit) {
        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        boolean anchor = state.is(Blocks.RESPAWN_ANCHOR);
        int charge = anchor ? state.getValue(RespawnAnchorBlock.CHARGE) : 0;
        if (anchor && charge > 0)
            detonate(hit);
        else
            place(hit, anchor);
    }

    private void detonate(BlockHitResult hit) {
        boolean holdingGlowstone = mc.player.getMainHandItem().is(Items.GLOWSTONE);
        if (swapMode.getValue() == SwapMode.Swap) {
            int slot = HotbarUse.find(s -> s.is(Items.TOTEM_OF_UNDYING));
            if (slot == -1 && holdingGlowstone)
                slot = HotbarUse.find(s -> !s.is(Items.GLOWSTONE));
            if (slot == -1 && holdingGlowstone)
                return;
            if (slot != -1)
                select(slot);
        } else if (holdingGlowstone) {
            return;
        }
        interact(hit);
        done();
    }

    private void place(BlockHitResult hit, boolean charge) {
        if (swapMode.getValue() == SwapMode.None) {
            ItemStack held = mc.player.getMainHandItem();
            if (!held.is(charge ? Items.GLOWSTONE : Items.RESPAWN_ANCHOR))
                return;
        } else {
            int slot = HotbarUse.find(s -> s.is(charge ? Items.GLOWSTONE : Items.RESPAWN_ANCHOR));
            if (slot == -1) {
                done();
                return;
            }
            select(slot);
        }
        interact(hit);
        done();
    }

    private void select(int slot) {
        if (mc.player.getInventory().getSelectedSlot() == slot)
            return;
        if (swapBack.getValue())
            swap.switchOnce(slot);
        else
            mc.player.getInventory().setSelectedSlot(slot);
    }

    private void interact(BlockHitResult hit) {
        if (mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit).consumesAction())
            PlayerUtils.swingItem();
        lastPos = hit.getBlockPos().immutable();
        lastTime = System.currentTimeMillis();
        timer.reset();
        nextDelay = delay.getValue().getRandomInRange();
    }

    /** One-Shot turns off on the next tick, after any swap back has gone out. */
    private void done() {
        if (fireMode.getValue() == FireMode.OneShot)
            finishing = true;
    }

    private boolean recentlyTouched(BlockPos pos) {
        return pos.equals(lastPos) && System.currentTimeMillis() - lastTime < SAME_BLOCK_MS;
    }

    /** An anchor, or a solid block whose placement spot has no entity in it. */
    private static boolean actionable(BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        if (mc.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR))
            return true;
        return solid(pos) && !occupied(placeSpot(hit));
    }

    /** Where an anchor placed on this hit ends up: in the block itself when it is fire or grass, else next to it. */
    private static BlockPos placeSpot(BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (state.is(Blocks.FIRE) || state.is(Blocks.SHORT_GRASS))
            return pos;
        return pos.relative(hit.getDirection());
    }

    /** A block an anchor can be placed against (counting fire, short grass and single snow layers). */
    private static boolean solid(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return !state.canBeReplaced() || state.is(Blocks.FIRE) || state.is(Blocks.SHORT_GRASS)
                || (state.is(Blocks.SNOW) && state.getValue(BlockStateProperties.LAYERS) == 1);
    }

    private static boolean occupied(BlockPos pos) {
        return !mc.level.getEntities((Entity) null, new AABB(pos), e -> !(e instanceof ItemEntity)).isEmpty();
    }

    /** The anchor spot nearest the crosshair inside the FOV that a straight look at its top would hit. */
    private void planSilentAim() {
        Vec3 eyes = mc.player.getEyePosition(1f);
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        double fov = silentAimFov.getValue().getInput();
        BlockPos origin = mc.player.blockPosition();
        double best = Double.MAX_VALUE;
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    scan.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!solid(scan))
                        continue;
                    Vec3 top = new Vec3(scan.getX() + 0.5, scan.getY() + 1, scan.getZ() + 0.5);
                    if (eyes.distanceTo(top) > AIM_REACH || eyes.y <= top.y)
                        continue;
                    float[] rots = RotationUtils.rotationsTo(eyes, top);
                    float dYaw = RotationUtils.getYawDifference(rots[0], yaw);
                    float dPitch = rots[1] - pitch;
                    if (Math.abs(dYaw) > fov)
                        continue;
                    double angle = Math.sqrt(dYaw * dYaw + dPitch * dPitch);
                    if (angle >= best)
                        continue;
                    BlockPos pos = scan.immutable();
                    if (occupied(pos.above()))
                        continue;
                    BlockHitResult hit = mc.level.clip(new ClipContext(eyes,
                            eyes.add(Entity.calculateViewVector(rots[1], rots[0]).scale(4.5)),
                            ClipContext.Block.OUTLINE,
                            ClipContext.Fluid.NONE, mc.player));
                    if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos) || !actionable(hit))
                        continue;
                    best = angle;
                    aimPos = pos;
                    aim = top;
                }
            }
        }
    }
}

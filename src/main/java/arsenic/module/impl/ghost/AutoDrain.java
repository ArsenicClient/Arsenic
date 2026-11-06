package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventPlayerJoinWorld;
import arsenic.event.impl.EventSilentRotation;
import arsenic.injection.accessor.IMixinMultiPlayerGameMode;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.TargetManager;
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
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Picks up water or lava near you with an empty bucket while an enemy is close, so they cannot use it against you. It
 * can swap to the bucket for you, put your slot back afterwards, and plug the liquid with a cobweb when you have no
 * bucket. Legit uses the block under your crosshair; Silent Aim finds a source block itself and aims at it silently.
 * Liquid you placed yourself is left alone.
 */
@ModuleInfo(name = "AutoDrain", description = "Scoops up nearby water and lava with an empty bucket in fights", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class AutoDrain extends Module {

    private static final double ENEMY_RANGE = 10;
    private static final float AIM_TOLERANCE = 6f;

    public enum Mode {
        Legit("Legit"), SilentAim("Silent Aim");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum SwapMode {
        None, Normal
    }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Legit);
    public final BooleanProperty water = new BooleanProperty("Water", true);
    public final BooleanProperty lava = new BooleanProperty("Lava", true);
    public final BooleanProperty waterOnWeb = new BooleanProperty("Water On Web", true);
    public final BooleanProperty lavaOnWeb = new BooleanProperty("Lava On Web", true);
    public final RangeProperty delay = new RangeProperty("Delay (ms)", new RangeValue(0, 600, 50, 100, 1));
    public final EnumProperty<SwapMode> swap = new EnumProperty<>("Swap", SwapMode.None);
    public final BooleanProperty cobwebFallback = new BooleanProperty("Fallback To Cobweb", true);
    public final BooleanProperty interact = new BooleanProperty("Interact", true);
    public final BooleanProperty swapBack = new BooleanProperty("Swap Back", true);
    public final DoubleProperty silentFov = new DoubleProperty("Silent Aim FOV", new DoubleValue(0, 180, 180, 1));

    /** Liquid this client placed with a bucket; never drained. Written from the packet hook. */
    private final Set<BlockPos> ownLiquid = ConcurrentHashMap.newKeySet();
    /** Liquid we already scooped or plugged, so it is not tried twice. */
    private final Set<BlockPos> drained = ConcurrentHashMap.newKeySet();

    private final MSTimer timer = new MSTimer();
    private double nextDelay;
    /** The slot to go back to after a swap, or -1. */
    private int restore = -1;
    /** Whether the swapped-to item has been used, so the slot can go back. */
    private boolean used;

    /** Silent Aim plan for this tick. */
    private BlockPos planPos;
    private Vec3 aim;
    private boolean planWeb;

    public AutoDrain() {
        swapBack.setVisible(() -> swap.getValue() == SwapMode.Normal && interact.getValue());
        silentFov.setVisible(() -> mode.getValue() == Mode.SilentAim);
    }

    @Override
    public boolean isSwappingHotbar() {
        return restore != -1;
    }

    @Override
    protected void onEnable() {
        restore = -1;
        used = false;
        timer.reset();
        nextDelay = delay.getValue().getRandomInRange();
        clearPlan();
    }

    @Override
    protected void onDisable() {
        if (swapBack.getValue() && restore != -1 && mc.player != null)
            selectSlot(restore);
        restore = -1;
        used = false;
        drained.clear();
        clearPlan();
    }

    @EventLink
    public final Listener<EventPlayerJoinWorld> onJoin = event -> {
        if (event.getEntity() != mc.player)
            return;
        ownLiquid.clear();
        drained.clear();
        restore = -1;
        used = false;
        clearPlan();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.OutGoing> onPacket = event -> {
        if (!(event.getPacket() instanceof ServerboundUseItemOnPacket packet))
            return;
        ItemStack held = mc.player.getItemInHand(packet.hand());
        if (held.is(Items.WATER_BUCKET) || held.is(Items.LAVA_BUCKET)) {
            BlockHitResult hit = packet.hitResult();
            ownLiquid.add(hit.getBlockPos().relative(hit.getDirection()));
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        clearPlan();
        ownLiquid.removeIf(p -> !isLiquid(mc.level.getBlockState(p).getBlock()));
        drained.removeIf(p -> !isLiquid(mc.level.getBlockState(p).getBlock()));
        if (mc.gui.screen() != null || mc.player.isUsingItem())
            return;
        if (!timer.finished((long) nextDelay))
            return;

        // the step after a scoop: put the slot back once the delay has passed
        if (used && restore != -1) {
            if (swapBack.getValue() && interact.getValue())
                selectSlot(restore);
            restore = -1;
            used = false;
            rearm();
            return;
        }

        if (!hasBucket() && !(cobwebFallback.getValue() && hasWeb()))
            return;
        if (!enemyNearby())
            return;

        if (mode.getValue() == Mode.Legit) {
            BlockHitResult hit = crosshairLiquid();
            if (hit == null || !candidate(hit.getBlockPos()))
                return;
            if (act(hit.getBlockPos(), mc.hitResult instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK ? b : null))
                rearm();
            return;
        }

        BlockHitResult hit = nearbyLiquid();
        if (hit == null)
            return;
        BlockPos pos = hit.getBlockPos().immutable();
        boolean web = !hasBucket() && cobwebFallback.getValue();
        Vec3 point = web ? Vec3.atCenterOf(pos.below()).add(0, 0.5, 0) : hit.getLocation();
        float[] rots = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), point);
        if (Math.abs(RotationUtils.getYawDifference(mc.player.getYRot(), rots[0])) > silentFov.getValue().getInput())
            return;
        planPos = pos;
        aim = point;
        planWeb = web;
        event.setYaw(rots[0]);
        event.setPitch(rots[1]);
        event.setSpeed(180f);
        event.setPreventDuplicateLook(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (aim == null || planPos == null)
            return;
        float[] want = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), aim);
        if (Math.abs(RotationUtils.getYawDifference(event.getYaw(), want[0])) > AIM_TOLERANCE
                || Math.abs(event.getPitch() - want[1]) > AIM_TOLERANCE)
            return;
        BlockHitResult webHit = null;
        if (planWeb) {
            Vec3 eyes = mc.player.getEyePosition(1f);
            Vec3 end = eyes.add(Entity.calculateViewVector(event.getPitch(), event.getYaw()).scale(mc.player.blockInteractionRange()));
            BlockHitResult hit = mc.level.clip(new ClipContext(eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.BLOCK)
                return;
            webHit = hit;
        }
        // the bucket rays along the player's look, so use it while looking where the server is told we look
        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        mc.player.setYRot(event.getYaw());
        mc.player.setXRot(event.getPitch());
        boolean acted;
        try {
            acted = act(planPos, webHit);
        } finally {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        }
        if (acted)
            rearm();
        clearPlan();
    };

    /** One step on this liquid: swap to the bucket (or cobweb), or use it. Returns whether anything was done. */
    private boolean act(BlockPos pos, BlockHitResult webHit) {
        if (drained.contains(pos))
            return false;
        if (hasBucket()) {
            if (!mc.player.getMainHandItem().is(Items.BUCKET))
                return swapTo(Items.BUCKET);
            if (interact.getValue()) {
                InteractionResult result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                if (result instanceof InteractionResult.Success s && s.swingSource() == InteractionResult.SwingSource.PREDICTED)
                    PlayerUtils.swingItem();
                used = true;
            } else {
                restore = -1; // swap only: the bucket stays in hand for the player to use
            }
            drained.add(pos.immutable());
            return true;
        }
        return cobwebFallback.getValue() && plug(pos, webHit);
    }

    /** Places a cobweb into the liquid. */
    private boolean plug(BlockPos pos, BlockHitResult hit) {
        if (!interact.getValue())
            return false;
        if (!mc.player.getMainHandItem().is(Items.COBWEB))
            return hasWeb() && swapTo(Items.COBWEB);
        if (hit == null)
            return false;
        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        if (result.consumesAction())
            PlayerUtils.swingItem();
        drained.add(pos.immutable());
        used = restore != -1;
        return true;
    }

    private boolean swapTo(Item item) {
        if (swap.getValue() == SwapMode.None)
            return false;
        int slot = HotbarUse.find(s -> s.is(item));
        if (slot == -1)
            return false;
        if (restore == -1)
            restore = mc.player.getInventory().getSelectedSlot();
        selectSlot(slot);
        return true;
    }

    private void selectSlot(int slot) {
        if (mc.player.getInventory().getSelectedSlot() == slot)
            return;
        mc.player.getInventory().setSelectedSlot(slot);
        ((IMixinMultiPlayerGameMode) mc.gameMode).syncCurrentPlayItem();
    }

    private void rearm() {
        timer.reset();
        nextDelay = delay.getValue().getRandomInRange();
    }

    private void clearPlan() {
        planPos = null;
        aim = null;
        planWeb = false;
    }

    private static boolean hasBucket() {
        return HotbarUse.find(s -> s.is(Items.BUCKET)) != -1;
    }

    private static boolean hasWeb() {
        return HotbarUse.find(s -> s.is(Items.COBWEB)) != -1;
    }

    private static boolean isLiquid(Block block) {
        return block == Blocks.WATER || block == Blocks.LAVA;
    }

    private boolean enemyNearby() {
        for (Player p : mc.level.players()) {
            if (TargetManager.isValidTarget(p) && mc.player.distanceTo(p) <= ENEMY_RANGE)
                return true;
        }
        return false;
    }

    /** A still source block this module is allowed to drain. */
    private boolean candidate(BlockPos pos) {
        Block block = mc.level.getBlockState(pos).getBlock();
        if (!isLiquid(block) || !mc.level.getFluidState(pos).isSource())
            return false;
        if (drained.contains(pos) || ownLiquid.contains(pos))
            return false;
        if (block == Blocks.WATER)
            return water.getValue() || (waterOnWeb.getValue() && nextToWeb(pos));
        return lava.getValue() || (lavaOnWeb.getValue() && nextToWeb(pos));
    }

    private boolean nextToWeb(BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (mc.level.getBlockState(pos.relative(d)).is(Blocks.COBWEB))
                return true;
        }
        return false;
    }

    /** The liquid source block under the crosshair, within reach. */
    private BlockHitResult crosshairLiquid() {
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 end = eyes.add(mc.player.getViewVector(1f).scale(mc.player.blockInteractionRange()));
        BlockHitResult hit = mc.level.clip(new ClipContext(eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, mc.player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /** The closest drainable source block in reach that a ray from the eyes reaches first. */
    private BlockHitResult nearbyLiquid() {
        Vec3 eyes = mc.player.getEyePosition(1f);
        double reach = mc.player.blockInteractionRange();
        BlockPos origin = mc.player.blockPosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockHitResult best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    pos.setWithOffset(origin, dx, dy, dz);
                    double dist = eyes.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (dist > reach * reach || dist >= bestDist || !candidate(pos))
                        continue;
                    Vec3 centre = Vec3.atCenterOf(pos);
                    BlockHitResult hit = mc.level.clip(new ClipContext(eyes, centre, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, mc.player));
                    if (hit.getType() != HitResult.Type.MISS && hit.getBlockPos().equals(pos)) {
                        best = hit;
                        bestDist = dist;
                    }
                }
            }
        }
        return best;
    }
}

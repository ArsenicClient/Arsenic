package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventPlayerJoinWorld;
import arsenic.event.impl.EventSilentRotation;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.AntiBot;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Charges the respawn anchor you look at with glowstone and then blows it up with the item in your explode slot
 * (a totem, so the blast cannot pop you without one). Silent Aim also does it for anchors near your crosshair.
 */
@ModuleInfo(name = "AnchorExploder", description = "Charges and explodes respawn anchors for you", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class AnchorExploder extends Module {

    public enum SwapMode { None, Swap }

    public enum SilentAim { Off, Glowstone, Explode, Both }

    private static final double SEARCH_RADIUS = 5;
    private static final double ENEMY_RANGE = 10;
    private static final long EXPLODE_COOLDOWN_MS = 100;

    public final BooleanProperty onlyOwn = new BooleanProperty("Only Own Anchors", true);
    public final RangeProperty delay = new RangeProperty("Delay (ms)", new RangeValue(0, 200, 30, 50, 1));
    public final BooleanProperty glowstone = new BooleanProperty("Glowstone", true);
    public final EnumProperty<SwapMode> glowstoneSwap = new EnumProperty<>("Glowstone Swap", SwapMode.Swap);
    public final BooleanProperty glowstoneSwapBack = new BooleanProperty("Glowstone Swap Back", false);
    public final BooleanProperty explode = new BooleanProperty("Explode", true);
    public final EnumProperty<SwapMode> explodeSwap = new EnumProperty<>("Explode Swap", SwapMode.Swap);
    public final DoubleProperty explodeSlot = new DoubleProperty("Explode Slot", new DoubleValue(1, 9, 8, 1));
    public final BooleanProperty explodeSwapBack = new BooleanProperty("Explode Swap Back", false);
    public final BooleanProperty damageTick = new BooleanProperty("Explode Damage Tick", true);
    public final BooleanProperty disableAfter = new BooleanProperty("Disable After Explode", false);
    public final EnumProperty<SilentAim> silentAim = new EnumProperty<>("Silent Aim", SilentAim.Off);
    public final DoubleProperty silentFov = new DoubleProperty("Silent Aim FOV", new DoubleValue(0, 180, 180, 1));

    /** Anchors we placed (only filled while Only Own Anchors is on). Touched from the network thread too. */
    private final Set<BlockPos> ownAnchors = ConcurrentHashMap.newKeySet();

    private long nextActionAt;
    private int restoreSlot = -1;
    private long lastExplodeAt;
    private BlockPos lastExplodePos;

    // silent aim plan for this tick
    private BlockPos planPos;
    private Vec3 planAim;
    private boolean planCharge;

    public AnchorExploder() {
        glowstoneSwap.setVisible(glowstone::getValue);
        glowstoneSwapBack.setVisible(() -> glowstone.getValue() && glowstoneSwap.getValue() == SwapMode.Swap);
        explodeSwap.setVisible(explode::getValue);
        explodeSlot.setVisible(explode::getValue);
        explodeSwapBack.setVisible(() -> explode.getValue() && explodeSwap.getValue() == SwapMode.Swap);
        damageTick.setVisible(explode::getValue);
        silentFov.setVisible(() -> silentAim.getValue() != SilentAim.Off);
    }

    @Override
    public boolean isSwappingHotbar() {
        return restoreSlot != -1;
    }

    @Override
    protected void onDisable() {
        if (restoreSlot != -1 && mc.player != null)
            mc.player.getInventory().setSelectedSlot(restoreSlot);
        restoreSlot = -1;
        ownAnchors.clear();
        clearPlan();
    }

    private void clearPlan() {
        planPos = null;
        planAim = null;
    }

    @EventLink
    public final Listener<EventPlayerJoinWorld> onJoinWorld = event -> {
        ownAnchors.clear();
        restoreSlot = -1;
        clearPlan();
    };

    /** Runs before the movement packet, so swaps and right-clicks land inside the tick (Grim Post). */
    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        clearPlan();
        if (mc.gui.screen() != null || !mc.isWindowActive() || mc.player.isUsingItem() || mc.gameMode == null)
            return;
        if (System.currentTimeMillis() < nextActionAt)
            return;

        if (onlyOwn.getValue())
            ownAnchors.removeIf(pos -> !mc.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR));

        // a swap back from the last action takes its own step, as openprestige does
        if (restoreSlot != -1) {
            mc.player.getInventory().setSelectedSlot(restoreSlot);
            restoreSlot = -1;
            scheduleNext();
            return;
        }

        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && mc.level.getBlockState(hit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)) {
            if (!mc.player.isShiftKeyDown() && usable(hit.getBlockPos()) && !recentlyExploded(hit.getBlockPos()))
                act(hit, charge(hit.getBlockPos()) == 0);
            return;
        }

        if (silentAim.getValue() != SilentAim.Off)
            planSilent(event);
    };

    /** The aim has settled: right-click the planned anchor if the rotation the server gets is on it. */
    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (planPos == null)
            return;
        BlockPos pos = planPos;
        boolean wantCharge = planCharge;
        clearPlan();
        BlockHitResult hit = event.getRayTrace();
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos))
            return;
        BlockState state = mc.level.getBlockState(pos);
        if (!state.is(Blocks.RESPAWN_ANCHOR) || (state.getValue(RespawnAnchorBlock.CHARGE) == 0) != wantCharge)
            return;
        act(hit, wantCharge);
    };

    @EventLink
    public final Listener<EventPacket.OutGoing> onOutgoing = event -> {
        if (!onlyOwn.getValue() || mc.player == null || mc.level == null
                || !(event.getPacket() instanceof ServerboundUseItemOnPacket packet))
            return;
        ItemStack stack = packet.hand() == InteractionHand.MAIN_HAND ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
        if (!stack.is(Items.RESPAWN_ANCHOR))
            return;
        BlockHitResult hit = packet.hitResult();
        BlockPos clicked = hit.getBlockPos();
        ownAnchors.add(mc.level.getBlockState(clicked).canBeReplaced() ? clicked.immutable() : clicked.relative(hit.getDirection()).immutable());
    };

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onIncoming = event -> {
        if (event.getPacket() instanceof ClientboundBlockUpdatePacket packet && packet.getBlockState() != null
                && !packet.getBlockState().is(Blocks.RESPAWN_ANCHOR))
            ownAnchors.remove(packet.getPos());
    };

    /** Charges (empty anchor) or explodes (charged anchor) through this hit. Returns whether it right-clicked. */
    private boolean act(BlockHitResult hit, boolean charge) {
        if (charge ? !glowstone.getValue() : !explode.getValue())
            return false;
        int previous = mc.player.getInventory().getSelectedSlot();
        if (charge) {
            if (glowstoneSwap.getValue() == SwapMode.None) {
                if (!mc.player.getMainHandItem().is(Items.GLOWSTONE))
                    return false;
            } else {
                int slot = HotbarUse.find(s -> s.is(Items.GLOWSTONE));
                if (slot == -1)
                    return false;
                if (slot != previous) {
                    mc.player.getInventory().setSelectedSlot(slot);
                    if (glowstoneSwapBack.getValue())
                        restoreSlot = previous;
                }
            }
        } else {
            if (damageTick.getValue() && enemyInHurtTime())
                return false;
            int slot = (int) explodeSlot.getValue().getInput() - 1;
            // the explode slot must not be glowstone, or the click would add a charge instead
            if (mc.player.getInventory().getItem(slot).is(Items.GLOWSTONE))
                return false;
            if (explodeSwap.getValue() == SwapMode.None) {
                if (previous != slot)
                    return false;
            } else if (slot != previous) {
                mc.player.getInventory().setSelectedSlot(slot);
                if (explodeSwapBack.getValue())
                    restoreSlot = previous;
            }
        }

        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        PlayerUtils.swingItem();
        scheduleNext();

        if (!charge) {
            lastExplodeAt = System.currentTimeMillis();
            lastExplodePos = hit.getBlockPos().immutable();
            ownAnchors.remove(hit.getBlockPos());
            if (disableAfter.getValue())
                setEnabled(false);
        }
        return true;
    }

    private void planSilent(EventSilentRotation event) {
        SilentAim mode = silentAim.getValue();
        boolean doCharge = glowstone.getValue() && (mode == SilentAim.Glowstone || mode == SilentAim.Both);
        boolean doExplode = explode.getValue() && (mode == SilentAim.Explode || mode == SilentAim.Both);
        if (!doCharge && !doExplode)
            return;

        Vec3 eyes = mc.player.getEyePosition(1f);
        double reach = mc.player.blockInteractionRange();
        float fov = (float) silentFov.getValue().getInput();
        int r = (int) Math.ceil(SEARCH_RADIUS);
        BlockPos origin = mc.player.blockPosition();
        double best = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    cursor.setWithOffset(origin, dx, dy, dz);
                    BlockState state = mc.level.getBlockState(cursor);
                    if (!state.is(Blocks.RESPAWN_ANCHOR))
                        continue;
                    double dist = eyes.distanceTo(Vec3.atCenterOf(cursor));
                    if (dist > reach || dist >= best || !usable(cursor) || recentlyExploded(cursor))
                        continue;
                    int charges = state.getValue(RespawnAnchorBlock.CHARGE);
                    boolean wantCharge = doCharge && charges == 0;
                    if (!wantCharge && !(doExplode && charges > 0))
                        continue;
                    Vec3 aim = visiblePoint(eyes, cursor, reach);
                    if (aim == null)
                        continue;
                    float[] rots = RotationUtils.rotationsTo(eyes, aim);
                    if (Math.abs(RotationUtils.getYawDifference(rots[0], mc.player.getYRot())) > fov)
                        continue;
                    best = dist;
                    planPos = cursor.immutable();
                    planAim = aim;
                    planCharge = wantCharge;
                }
            }
        }
        if (planAim == null)
            return;
        float[] rots = RotationUtils.rotationsTo(eyes, planAim);
        event.setYaw(rots[0]);
        event.setPitch(rots[1]);
        event.setSpeed(180f);
        event.setPreventDuplicateLook(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    }

    /** A point on the anchor the eyes can see within reach: the centre, else the centre of an exposed face. */
    private static Vec3 visiblePoint(Vec3 eyes, BlockPos pos, double reach) {
        Vec3 centre = Vec3.atCenterOf(pos);
        if (sees(eyes, centre, pos))
            return centre;
        for (Direction dir : Direction.values()) {
            Vec3 face = centre.add(dir.getStepX() * 0.49, dir.getStepY() * 0.49, dir.getStepZ() * 0.49);
            if (eyes.distanceTo(face) <= reach && sees(eyes, face, pos))
                return face;
        }
        return null;
    }

    private static boolean sees(Vec3 eyes, Vec3 point, BlockPos pos) {
        BlockHitResult hit = mc.level.clip(new ClipContext(eyes, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    private boolean usable(BlockPos pos) {
        return !onlyOwn.getValue() || ownAnchors.contains(pos);
    }

    /** Skips anchors right after a blast nearby, while the server is still removing the last one. */
    private boolean recentlyExploded(BlockPos pos) {
        return lastExplodePos != null && System.currentTimeMillis() - lastExplodeAt <= EXPLODE_COOLDOWN_MS
                && lastExplodePos.distSqr(pos) <= 42.25;
    }

    private static int charge(BlockPos pos) {
        return mc.level.getBlockState(pos).getValue(RespawnAnchorBlock.CHARGE);
    }

    /** The nearest enemy is still in hurt time, so a blast now would do nothing to them. */
    private boolean enemyInHurtTime() {
        Player nearest = null;
        double best = ENEMY_RANGE;
        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || AntiBot.isBot(p) || Arsenic.getArsenic().getFriendManager().isFriend(p))
                continue;
            double d = mc.player.distanceTo(p);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        return nearest != null && nearest.hurtTime != 0;
    }

    private void scheduleNext() {
        nextActionAt = System.currentTimeMillis() + (long) delay.getValue().getRandomInRange();
    }
}

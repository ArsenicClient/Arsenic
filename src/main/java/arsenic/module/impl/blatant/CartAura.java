package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventPlayerJoinWorld;
import arsenic.event.impl.EventSilentRotation;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.minecraft.ItemUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Silently aims at TNT minecarts near you and hits them with a sword, after a human-like reaction delay. */
@ModuleInfo(name = "CartAura", description = "Automatically hits TNT minecarts near you with human-like delays", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class CartAura extends Module {

    private static final double ACQUIRE_RANGE = 5.0;
    private static final double ATTACK_RANGE = 3.0;
    private static final long OWN_CART_MS = 5000L;

    private enum State {
        IDLE, SWAPPED, SWAP_BACK
    }

    public final RangeProperty reactionDelay = new RangeProperty("Reaction Delay (ms)", new RangeValue(0, 1000, 100, 250, 5));
    public final DoubleProperty swapDelay = new DoubleProperty("Swap Delay (ms)", new DoubleValue(0, 300, 50, 5));
    public final BooleanProperty swapBack = new BooleanProperty("Swap Back", true);
    public final DoubleProperty swapBackDelay = new DoubleProperty("Swap Back Delay (ms)", new DoubleValue(0, 300, 50, 5));
    public final RangeProperty attackDelay = new RangeProperty("Attack Delay (ms)", new RangeValue(0, 1000, 50, 150, 5));
    public final DoubleProperty targetFov = new DoubleProperty("Target FOV", new DoubleValue(30, 360, 180, 5));
    public final BooleanProperty humanizedAim = new BooleanProperty("Humanized Aim", true);

    /** Carts we placed ourselves (where the TNT cart right-click went), so we never blow up our own trap. */
    private final List<PlacedCart> ownCarts = new ArrayList<>();

    private final MSTimer reactionTimer = new MSTimer();
    private final MSTimer swapTimer = new MSTimer();
    private final MSTimer swapBackTimer = new MSTimer();
    private final MSTimer attackTimer = MSTimer.expired();

    private MinecartTNT target;
    private State state = State.IDLE;
    private int restoreSlot = -1;
    private double rolledReaction, rolledAttack;
    /** Offset from the box centre the aim is on, re-rolled after each hit when Humanized Aim is on. */
    private double jitterX, jitterY, jitterZ;
    /** Set by the pre-rotation hook when the aim is on the cart and close enough to hit this tick. */
    private boolean planned;

    private record PlacedCart(BlockPos pos, long time) {}

    public CartAura() {
        swapBackDelay.setVisible(swapBack::getValue);
    }

    @Override
    public boolean isSwappingHotbar() {
        return state != State.IDLE;
    }

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        restore();
        target = null;
        planned = false;
        ownCarts.clear();
    }

    private void reset() {
        target = null;
        ownCarts.clear();
        restoreSlot = -1;
        state = State.IDLE;
        planned = false;
    }

    @EventLink
    public final Listener<EventPlayerJoinWorld> onJoinWorld = event -> reset();

    /** Runs before the movement packet: picks the cart, aims at it, and handles the swap back. */
    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        planned = false;
        if (mc.gameMode == null)
            return;

        if (state == State.SWAP_BACK) {
            if (swapBackTimer.hasTimeElapsed((long) swapBackDelay.getValue().getInput()))
                restore();
            return;
        }
        if (mc.gui.screen() != null || mc.player.isUsingItem())
            return;

        if (target != null && (!target.isAlive() || isOwn(target)
                || mc.player.position().distanceTo(target.getBoundingBox().getCenter()) > ACQUIRE_RANGE))
            target = null;

        if (target == null) {
            target = findCart((float) targetFov.getValue().getInput());
            if (target != null) {
                rolledReaction = reactionDelay.getValue().getRandomInRange();
                reactionTimer.reset();
                rollJitter();
            }
        }

        if (target == null) {
            if (state == State.SWAPPED)
                restore();
            return;
        }

        if (!mc.player.hasLineOfSight(target) || RotationUtils.getDistanceToEntityBox(target) > ATTACK_RANGE)
            return;

        AABB box = target.getBoundingBox();
        Vec3 centre = box.getCenter();
        Vec3 aim = humanizedAim.getValue()
                ? new Vec3(centre.x + jitterX * box.getXsize(), centre.y + jitterY * box.getYsize(), centre.z + jitterZ * box.getZsize())
                : centre;
        float[] rots = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), aim);
        event.setYaw(rots[0]);
        event.setPitch(rots[1]);
        event.setSpeed(180f);
        event.setPreventDuplicateLook(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
        planned = true;
    };

    /** The rotation the server gets this tick is settled: swap to the sword, or hit the cart if the aim is on it. */
    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (!planned || target == null)
            return;
        planned = false;
        if (!reactionTimer.hasTimeElapsed((long) rolledReaction))
            return;

        if (state == State.SWAPPED) {
            // the swap always settles for at least one tick: the swap happened in an earlier Post
            if (!swapTimer.hasTimeElapsed((long) swapDelay.getValue().getInput()))
                return;
            if (!ItemUtils.isSword(mc.player.getMainHandItem())) {
                restore();
                return;
            }
        } else if (!ItemUtils.isSword(mc.player.getMainHandItem())) {
            int sword = HotbarUse.find(ItemUtils::isSword);
            if (sword == -1)
                return;
            restoreSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(sword);
            state = State.SWAPPED;
            swapTimer.reset();
            return;
        }

        if (!attackTimer.hasTimeElapsed((long) rolledAttack))
            return;
        HitResult hit = event.getRayTraceEntity();
        if (!(hit instanceof EntityHitResult entityHit) || entityHit.getEntity() != target)
            return;

        // attack before swing, as a 1.9+ client sends them
        mc.gameMode.attack(mc.player, target);
        PlayerUtils.swingItem();
        attackTimer.reset();
        rolledAttack = attackDelay.getValue().getRandomInRange();
        rollJitter();

        if (state == State.SWAPPED) {
            if (swapBack.getValue()) {
                state = State.SWAP_BACK;
                swapBackTimer.reset();
            } else {
                restoreSlot = -1;
                state = State.IDLE;
            }
        }
    };

    /** Remembers where our own TNT carts go, so they are not hit. */
    @EventLink
    public final Listener<EventPacket.OutGoing> onOutgoing = event -> {
        if (mc.player == null || !(event.getPacket() instanceof ServerboundUseItemOnPacket packet))
            return;
        ItemStack stack = packet.hand() == InteractionHand.MAIN_HAND ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
        if (!stack.is(Items.TNT_MINECART))
            return;
        long now = System.currentTimeMillis();
        BlockPos clicked = packet.hitResult().getBlockPos();
        ownCarts.add(new PlacedCart(clicked.immutable(), now));
        ownCarts.add(new PlacedCart(clicked.relative(packet.hitResult().getDirection()).immutable(), now));
    };

    private MinecartTNT findCart(float fov) {
        long now = System.currentTimeMillis();
        ownCarts.removeIf(c -> now - c.time() > OWN_CART_MS);
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 look = mc.player.getViewVector(1f);
        double halfFov = Math.toRadians(fov / 2.0);
        MinecartTNT best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof MinecartTNT cart) || !cart.isAlive() || isOwn(cart))
                continue;
            Vec3 centre = cart.getBoundingBox().getCenter();
            double distSq = mc.player.position().distanceToSqr(centre);
            if (distSq > ACQUIRE_RANGE * ACQUIRE_RANGE)
                continue;
            if (fov < 360f) {
                Vec3 dir = centre.subtract(eyes);
                double len = dir.length();
                if (len > 1.0E-4) {
                    double cos = Math.max(-1.0, Math.min(1.0, look.dot(dir) / len));
                    if (Math.acos(cos) > halfFov)
                        continue;
                }
            }
            if (distSq < bestDist) {
                best = cart;
                bestDist = distSq;
            }
        }
        return best;
    }

    private boolean isOwn(MinecartTNT cart) {
        if (ownCarts.isEmpty())
            return false;
        Vec3 centre = cart.getBoundingBox().getCenter();
        long now = System.currentTimeMillis();
        for (PlacedCart placed : ownCarts) {
            if (now - placed.time() <= OWN_CART_MS && centre.closerThan(Vec3.atCenterOf(placed.pos()), 1.5))
                return true;
        }
        return false;
    }

    private void rollJitter() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        jitterX = r.nextDouble(-0.2, 0.2);
        jitterY = r.nextDouble(-0.2, 0.2);
        jitterZ = r.nextDouble(-0.2, 0.2);
    }

    private void restore() {
        if (restoreSlot != -1 && mc.player != null)
            mc.player.getInventory().setSelectedSlot(restoreSlot);
        restoreSlot = -1;
        state = State.IDLE;
    }
}

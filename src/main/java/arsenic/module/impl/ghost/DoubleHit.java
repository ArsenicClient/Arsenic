package arsenic.module.impl.ghost;

import arsenic.module.property.impl.SliderScale;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.bus.Priorities;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventUpdate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.lag.LagManager;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;

@ModuleInfo(name = "DoubleHit", category = ModuleCategory.COMBAT)
public class DoubleHit extends Module {

    public final DoubleProperty maxHold = new DoubleProperty("Max Hold (ms)", new DoubleValue(50, 1500, 400, 10), SliderScale.LOG);

    public final DoubleProperty delay = new DoubleProperty("Delay (ms)", new DoubleValue(0, 5000, 900, 50));


    private static final double ATTACK_RANGE = 3.0;
    private static final double RELEASE_RANGE = 4.0;
    private static final int LOOKAHEAD_TICKS = 4;
    private static final double RELEASE_HURT_TIME = 0.0;
    private static final long HIT_MEMORY_MS = 600L;

    private Player target;
    private volatile boolean holding;

    private double lastDistance = -1;
    private double closureRate;

    private final MSTimer hitTimer = new MSTimer();
    private final MSTimer holdTimer = new MSTimer();
    private final MSTimer idleTimer = new MSTimer();

    private boolean hasHit;

    private boolean awaitingConfirmation;
    private boolean pendingCleared;
    private Player pendingTarget;
    private final MSTimer confirmTimer = new MSTimer();

    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        if (!(event.getTarget() instanceof Player))
            return;

        target = (Player) event.getTarget();
        hasHit = true;
        hitTimer.reset();
    };

    @RequiresPlayer
    @EventLink(Priorities.VERY_HIGH)
    public final Listener<EventPacket.OutGoing> onOutgoingAttack = event -> {
        if (holding || target == null || mc.player == null)
            return;

        ClientLevel world = mc.level;
        if (world == null || !(event.getPacket() instanceof ServerboundAttackPacket use))
            return;

        if (world.getEntity(use.entityId()) != target)
            return;

        if (canStartHold())
            startHold();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventUpdate.Pre> onUpdate = event -> {
        if (mc.player == null || mc.level == null) {
            stopHold();
            reset();
            return;
        }

        checkConfirmation();

        if (!isTargetValid()) {
            stopHold();
            reset();
            return;
        }

        if (hitTimer.hasTimeElapsed(HIT_MEMORY_MS))
            hasHit = false;

        trackClosure();

        if (holding) {
            if (shouldRelease())
                stopHold(true);
            else if (holdTimer.hasTimeElapsed(maxHold.getValue().getInput()))
                stopHold(false);
        }
    };

    @Override
    public String getHudInfo() {
        if (holding)
            return "witholding";
        if (hasHit && target != null)
            return "armed";
        return null;
    }

    private void trackClosure() {
        double distance = RotationUtils.getDistanceToEntityBox(target);
        closureRate = lastDistance < 0 ? 0 : distance - lastDistance;
        lastDistance = distance;
    }

    private double predictedDistance() {
        return lastDistance + closureRate * LOOKAHEAD_TICKS;
    }

    private double hurtTimeOnArrival() {
        float now = TargetManager.getServerHurtTimeOnPacketArrival(target);
        if (now == Float.MAX_VALUE)
            return 0;

        return Math.max(0, now - (LagManager.getPingAsTicks() / 2.0));
    }

    private boolean canStartHold() {
        if (!hasHit)
            return false;

        if (!idleTimer.hasTimeElapsed(delay.getValue().getInput()))
            return false;

        if (LagManager.isLagging() && !LagManager.isHolding(DoubleHit.class))
            return false;

        if (hurtTimeOnArrival() <= RELEASE_HURT_TIME)
            return false;

        return closureRate > 0 && predictedDistance() > ATTACK_RANGE;
    }

    private boolean shouldRelease() {
        if (hurtTimeOnArrival() > RELEASE_HURT_TIME)
            return false;

        return lastDistance >= RELEASE_RANGE;
    }

    private boolean isTargetValid() {
        return target != null
                && target != mc.player
                && target.isAlive()
                && !target.isRemoved()
                && mc.level.getEntity(target.getId()) == target;
    }

    private void startHold() {
        holding = true;
        holdTimer.reset();
        LagManager.acquire(DoubleHit.class, LagManager.ALL_PACKETS);
    }

    private void stopHold() {
        stopHold(false);
    }

    private void stopHold(boolean released) {
        if (!holding)
            return;

        holding = false;
        idleTimer.reset();

        LagManager.release(DoubleHit.class);

        if (released && target != null) {
            awaitingConfirmation = true;
            pendingCleared = false;
            pendingTarget = target;
            confirmTimer.reset();
        }
    }

    private void checkConfirmation() {
        if (!awaitingConfirmation)
            return;

        if (pendingTarget == null || pendingTarget.isRemoved() || mc.level == null) {
            awaitingConfirmation = false;
            return;
        }

        if (!pendingCleared) {
            if (pendingTarget.hurtTime == 0)
                pendingCleared = true;
        } else if (pendingTarget.hurtTime > 0) {
            awaitingConfirmation = false;
            SoundUtils.hitConfirm();
            idleTimer.reset();
            pendingTarget = null;
            return;
        }

        if (confirmTimer.hasTimeElapsed(LagManager.getPing() * 2L + 300L)) {
            awaitingConfirmation = false;
            pendingTarget = null;
        }
    }

    private void reset() {
        target = null;
        hasHit = false;
        lastDistance = -1;
        closureRate = 0;
    }

    @Override
    protected void onEnable() {
        reset();
        idleTimer.reset();
    }

    @Override
    protected void onDisable() {
        stopHold(false);
        reset();
        awaitingConfirmation = false;
        pendingTarget = null;
    }
}

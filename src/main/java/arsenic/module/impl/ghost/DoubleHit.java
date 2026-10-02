package arsenic.module.impl.ghost;

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

/**
 * Turns the target's invulnerability window into a free extra hit.
 * <p>
 * After a hit lands the target is invulnerable server side for ten ticks. Any attack thrown inside
 * that window is eaten by the server, so the usual outcome when you are disengaging is that your
 * second swing does nothing and by the time the target is hittable again you are out of reach.
 * <p>
 * DoubleHit blinks through that window instead. When an attack packet is on its way out and all
 * three of the following hold:
 * <ol>
 *     <li>we have actually landed a hit on the target (there is something to wait out),</li>
 *     <li>the target is still invulnerable as of the moment that packet would arrive, and</li>
 *     <li>we are moving away and will be out of reach shortly,</li>
 * </ol>
 * every outgoing packet is parked in {@link LagManager}'s holder buffer - starting with the swing
 * that triggered it. The server keeps our old, in-range position for as long as we hold. We keep
 * swinging locally; those attack packets pile up in the buffer with the movement that justified them.
 * <p>
 * The buffer is flushed once we are physically out of range <i>and</i> the target's server-side
 * hurt time will have decayed to zero by the time the flush actually arrives (one-way ping ahead of
 * now). The server then replays the whole burst against a target that is freshly vulnerable, from a
 * position that was still legal - a second hit landed from somewhere we no longer are.
 * <p>
 * Everything here is intentionally conservative: the hold ends on a timeout, on losing the target,
 * on re-entering range without a valid release, and on disable, because a holder left acquired is a
 * client that has stopped talking to the server.
 *
 * @see LagManager#acquire(Class)
 * @see TargetManager#getServerHurtTimeOnPacketArrival(Player)
 */
@ModuleInfo(name = "DoubleHit", category = ModuleCategory.COMBAT)
public class DoubleHit extends Module {

    /**
     * Hard cap on a hold. The only reason to touch this is server tolerance: longer holds recover
     * more hits and look worse to a movement-simulating anticheat.
     */
    public final DoubleProperty maxHold = new DoubleProperty("Max Hold (ms)", new DoubleValue(50, 1500, 400, 10));

    /** Minimum gap between one flush and the next hold, so this stays an opportunity, not a state. */
    public final DoubleProperty delay = new DoubleProperty("Delay (ms)", new DoubleValue(0, 5000, 900, 50));

    // ---------------------------------------------------------------
    //  Everything below used to be a property. None of them are decisions a person can make well
    //  without reading the implementation, and a client whose point is that it just works should not
    //  be asking. They are the previous defaults, which is what everyone was running anyway.
    // ---------------------------------------------------------------

    /** Reach we assume the server will honour, and the line the disengage prediction is measured against. */
    private static final double ATTACK_RANGE = 3.0;
    /** How far past the attack range we must have travelled before the burst is allowed out. */
    private static final double RELEASE_RANGE = 4.0;
    /** How many ticks ahead the "will I be out of range next swing" prediction looks. */
    private static final int LOOKAHEAD_TICKS = 4;
    /** Release once the estimated server hurt time at packet arrival has decayed to this. */
    private static final double RELEASE_HURT_TIME = 0.0;
    /** A hit older than this no longer counts as "we are inside their invuln window". */
    private static final long HIT_MEMORY_MS = 600L;

    private Player target;
    /** Written from the netty thread when a hold starts, read from the tick thread. */
    private volatile boolean holding;

    private double lastDistance = -1;
    private double closureRate;

    private final MSTimer hitTimer = new MSTimer();   // time since we last attacked the tracked target
    private final MSTimer holdTimer = new MSTimer();  // time since the current hold started
    private final MSTimer idleTimer = new MSTimer();  // time since the last flush (cooldown)

    private boolean hasHit;

    // ---------------------------------------------------------------
    //  Landing confirmation
    //
    //  Flushing the buffer is not the same as landing the hit - the server can
    //  still reject it. So a release only arms a pending check; the hit counts
    //  as successful once the target is actually seen to flinch (hurtTime rises
    //  from zero) inside one round trip. Release is the condition for that
    //  check, never the announcement itself.
    // ---------------------------------------------------------------
    private boolean awaitingConfirmation;
    /**
     * Set once the target's client-side hurtTime has been seen at zero after a release. The release
     * condition is expressed in arrival time, which already discounts a ping, so at the moment we
     * flush the target can still be visibly flinching from the <em>first</em> hit. Counting a rise
     * before that has decayed would report the hit we already knew about.
     */
    private boolean pendingCleared;
    private Player pendingTarget;
    private final MSTimer confirmTimer = new MSTimer();

    /** Remember who we hit and when: the hold is only ever justified by our own landed hit. */
    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        if (!(event.getTarget() instanceof Player))
            return;

        target = (Player) event.getTarget();
        hasHit = true;
        hitTimer.reset();
    };

    /**
     * The trigger. A hold now starts on the outgoing attack packet that would be wasted, rather
     * than on a tick where the geometry happens to look right.
     * <p>
     * The old tick-based start fired on the single tick where we crossed the reach line, so the
     * window in which a swing could land in the buffer was one or two ticks wide - miss it and the
     * flush carried movement and nothing else. Worse, a hold that captures no attack cannot possibly
     * produce a double hit; it is desync for nothing. Starting here inverts that: the packet is the
     * reason to hold, so every hold contains at least the swing that justified it.
     * <p>
     * Priority is deliberately {@link Priorities#VERY_HIGH}. The bus dispatches highest-first, and
     * {@link LagManager}'s own outgoing listener sits at the default MEDIUM - so acquiring here
     * takes effect before it tests its holders, and this very packet is the first thing buffered.
     * <p>
     * Runs on the netty thread: read client state defensively and keep the work small.
     */
    @RequiresPlayer
    @EventLink(Priorities.VERY_HIGH)
    public final Listener<EventPacket.OutGoing> onOutgoingAttack = event -> {
        if (holding || target == null || mc.player == null)
            return;

        ClientLevel world = mc.level;
        if (world == null || !(event.getPacket() instanceof ServerboundAttackPacket use))
            return;

        // Only our own tracked target matters; a swing at anything else is not the one being eaten.
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

        // Runs before the target checks below: the target is usually cleared the moment we flush,
        // and the confirmation still has to be resolved after that.
        checkConfirmation();

        if (!isTargetValid()) {
            stopHold();
            reset();
            return;
        }

        // Our own hit is what makes the target invulnerable; once it is stale there is nothing
        // left to wait out and holding would just be blinking for its own sake.
        if (hitTimer.hasTimeElapsed(HIT_MEMORY_MS))
            hasHit = false;

        trackClosure();

        // Only a release on our own terms is a candidate for a successful double hit; the
        // timeout is a bail-out, and the packets it flushes were never timed to land. Starting a
        // hold is not this loop's job any more - that happens on the attack packet above.
        if (holding) {
            if (shouldRelease())
                stopHold(true);
            else if (holdTimer.hasTimeElapsed(maxHold.getValue().getInput()))
                stopHold(false);
        }
    };

    /**
     * "witholding" while the buffer is parked, "armed" once a hit has landed and we are waiting for
     * the disengage that makes holding worthwhile.
     */
    @Override
    public String getHudInfo() {
        if (holding)
            return "witholding";
        if (hasHit && target != null)
            return "armed";
        return null;
    }

    /**
     * Distance is sampled every tick so the difference between samples is the closure rate - how
     * fast the gap is opening. Extrapolating it is what tells us the next swing is already lost.
     */
    private void trackClosure() {
        double distance = RotationUtils.getDistanceToEntityBox(target);
        closureRate = lastDistance < 0 ? 0 : distance - lastDistance;
        lastDistance = distance;
    }

    private double predictedDistance() {
        return lastDistance + closureRate * LOOKAHEAD_TICKS;
    }

    /** Server-side hurt time of the target as it will read when our packets actually arrive. */
    private double hurtTimeOnArrival() {
        float now = TargetManager.getServerHurtTimeOnPacketArrival(target);
        if (now == Float.MAX_VALUE)
            return 0;

        // getServerHurtTimeOnPacketArrival already discounts a full ping; the flush still has to
        // travel one way, so take another half ping off to land on arrival-time truth.
        return Math.max(0, now - (LagManager.getPingAsTicks() / 2.0));
    }

    /**
     * Whether this is a swing worth holding for. Evaluated on the attack packet, so the geometry
     * test asks "am I on my way out of reach" rather than "am I exactly on the reach line" - which
     * is what lets a hold begin while there is still time to buffer the swing.
     */
    private boolean canStartHold() {
        if (!hasHit)
            return false;

        if (!idleTimer.hasTimeElapsed(delay.getValue().getInput()))
            return false;

        // Someone else may already be blinking (LagRange, Blink). Two holders fighting over the
        // same buffer produce a flush neither of them timed, so stay out of it.
        if (LagManager.isLagging() && !LagManager.isHolding(DoubleHit.class))
            return false;

        // Nothing to gain unless the target is genuinely still inside its invuln window...
        if (hurtTimeOnArrival() <= RELEASE_HURT_TIME)
            return false;

        // ...and unless we are actually disengaging. Holding while closing on someone gains
        // nothing: the next swing would have landed by itself.
        return closureRate > 0 && predictedDistance() > ATTACK_RANGE;
    }

    /**
     * The whole point: flush only once we are past the reach line <i>and</i> the target has become
     * hittable again as of arrival. Either condition alone throws the hit away.
     */
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

        // release() drops our holder and flushes everything no other holder still wants.
        LagManager.release(DoubleHit.class);

        if (released && target != null) {
            awaitingConfirmation = true;
            pendingCleared = false;
            pendingTarget = target;
            confirmTimer.reset();
        }
    }

    /**
     * Resolves a pending release. The burst counts as landed the moment the target flinches; if a
     * full round trip plus a margin passes with no reaction, the server ate it and nothing is said.
     * <p>
     * This watches {@code hurtTime} rather than health, so it still fires against an armoured or
     * absorbing target. The trade-off is that damage from someone else inside the same window would
     * also read as a hit - unlikely in the disengage this module fires on, but not impossible.
     */
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
            // Sound only. A chat line every time this fires is noise in the middle of a fight,
            // and it was the kind of thing that needed a setting to turn off - so it is gone.
            SoundUtils.hitConfirm();
            // Confirmed landing: restart the gap from now, not from the flush.
            idleTimer.reset();
            pendingTarget = null;
            return;
        }

        // One round trip for the burst to arrive plus one for the damage packet to come back,
        // and a margin for the server tick it lands on.
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

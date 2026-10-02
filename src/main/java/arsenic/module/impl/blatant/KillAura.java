package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.impl.ghost.Hitflick;
import arsenic.injection.accessor.IMixinEntity;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.minecraft.ServerInfo;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.lag.LagManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(name = "KillAura", category = ModuleCategory.COMBAT)
public class KillAura extends Module {

    public RangeProperty speed = new RangeProperty("speed", new RangeValue(1, 360, 20, 50,1));
    public RangeProperty returnSpeed = new RangeProperty("Return Speed", new RangeValue(1, 90, 5, 15, 1));
    public RangeProperty aps = new RangeProperty("APS", new RangeValue(1, 20, 8, 12, 1));
    public final EnumProperty<RotationMode> rotationMode = new EnumProperty<>("Rotations", RotationMode.Instant);
    /** Ticks of target movement to lead the aim by. */
    public final DoubleProperty prediction = new DoubleProperty("Prediction", new DoubleValue(0, 5, 1, 0.1));
    /** How long before/after the crosshair is on the target the aura keeps clicking. */
    public final DoubleProperty clickGrace = new DoubleProperty("Click Grace", new DoubleValue(0, 500, 200, 10));
    /**
     * Extra distance past attack range to start aiming from - only when there is a single target,
     * so the aura never pre-aims at one player while another is the real threat.
     */
    public final DoubleProperty preAim = new DoubleProperty("Pre-Aim Range", new DoubleValue(0, 5, 1, 0.1));
    /** Off: the aura's rotation is applied to the real camera too, so what you see is what's sent. */
    public final BooleanProperty silentRotations = new BooleanProperty("Silent Rotations", true);
    public EntityPlayer target = null;
    private boolean hadTarget = false;
    private boolean wasUsingItem;
    private final MSTimer attackTimer = new MSTimer();
    /** Time since the committed rotation last actually pointed at {@link #target}. */
    private final MSTimer onTargetTimer = new MSTimer();
    private boolean everOnTarget = false;

    /**
     * The delay the current attack cycle is actually waiting on. {@link #getAttackDelay()} rolls a
     * fresh random value on every call, so Lazy has to budget against the same number the attack
     * check will use rather than re-rolling and getting a different answer each tick.
     */
    private long currentAttackDelay = 100L;

    private static final double ATTACK_RANGE = 3.0;

    // Aim point drift, as fractions of the target's box (0 = min edge, 1 = max edge).
    private float driftX = 0.5f, driftY = 0.65f, driftZ = 0.5f;
    private float driftGoalX = 0.5f, driftGoalY = 0.65f, driftGoalZ = 0.5f;
    private int driftTicksLeft = 0;
    /** 0 = aim at the nearest point of the box, 1 = aim at the drift point. Eased, never snapped. */
    private float driftBlend = 0f;

    // Lazy flick state.
    /** Error (degrees) past which Lazy treats the turn as a flick rather than tracking. */
    private static final float FLICK_THRESHOLD = 12f;
    private boolean flicking = false;
    private int flickTick, flickDuration;
    private float overshootYaw, overshootPitch;
    private EntityPlayer flickTarget = null;


    /**
     * How targets are being chosen - the setting that actually changes how the aura behaves.
     * <p>
     * Deliberately constant: this used to swap to the current target's name whenever one was locked
     * on, so the suffix changed on every target switch and every time a fight started or ended.
     * A label that only ever reflects a setting stays put, which is the whole point of it.
     */
    @Override
    public String getHudInfo() {
        return TargetManager.sortMode.getValue().name().toLowerCase();
    }

    @Override
    protected void onEnable() {
        target = null;
        hadTarget = false;
        everOnTarget = false;
        flicking = false;
        flickTarget = null;
        driftBlend = 0f;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        target = pickTarget();
        updateDrift();
        if (target != null && hitflick().ownsRotation()) {
            // Hitflick is turning away to throw the knockback; aiming resumes once it's done.
            hadTarget = true;
            return;
        }
        if (target != null) {
            float[] rots = getPredictedRotations(target, (float) prediction.getValue().getInput());
            if (rotationMode.getValue() == RotationMode.Lazy) {
                lazyRotate(event, rots);
            } else {
                event.setYaw(rots[0]);
                event.setPitch(rots[1]);
                event.setSpeed((float) speed.getValue().getRandomInRange());
            }
            hadTarget = true;
        } else if (hadTarget) {
            flicking = false;
            flickTarget = null;
            // Target lost/killed: rotate back to player yaw at a slower speed
            event.setSpeed((float) returnSpeed.getValue().getRandomInRange());
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> eventTickListener = event -> {
        // Reset hadTarget once rotation has fully returned to player yaw
        if (target == null && hadTarget && !event.isModified()) {
            hadTarget = false;
        }
        // Non-silent: put the committed rotation on the real camera. prevRotationYaw/Pitch were
        // already rolled over this tick, so the frame interpolation renders it as a smooth turn.
        // Next tick the manager starts from a player yaw equal to its own, so once there's no
        // target there's nothing to rotate back from.
        Hitflick hitflick = hitflick();
        boolean flickInProgress = hitflick.ownsRotation();
        if (!silentRotations.getValue() && target != null && !flickInProgress) {
            mc.thePlayer.rotationYaw = event.getYaw();
            mc.thePlayer.rotationPitch = event.getPitch();
        }
        boolean usingItem = mc.thePlayer.isUsingItem();
        MovingObjectPosition raytrace = event.getRayTraceEntity();
        Entity hit = raytrace != null ? raytrace.entityHit : null;
        // Reach is measured where the look ray actually enters the hitbox - that's what the server
        // checks - not at the box's nearest point. Aiming anywhere but the nearest point (drift,
        // prediction, mid-turn) puts the entry point further away, so a nearest-point check can
        // pass at 2.9 while the real hit lands past 3. Past reach, vanilla wouldn't have the entity
        // under the crosshair at all, so treat it as nothing there.
        if (hit != null && mc.thePlayer.getPositionEyes(1f).distanceTo(raytrace.hitVec) > ATTACK_RANGE)
            hit = null;
        if (target != null && hit == target) {
            onTargetTimer.reset();
            everOnTarget = true;
        }
        // The flick's own hit landing this tick counts as ours, so the next one waits a full cycle
        // instead of stacking a second attack on the same tick.
        if (hitflick.attackedThisTick())
            resetAttackCycle();
        if (target != null
                && !flickInProgress
                && attackTimer.getTime() >= currentAttackDelay
                && mc.currentScreen == null
                && !usingItem
                && !wasUsingItem) {
            if (hit == target) {
                // Hand the hit to Hitflick when it takes it: it flicks next tick and throws this
                // attack itself the tick after. Void mode declines when no angle empties into the
                // void, and the hit goes through normally then.
                if (hitflick.isEnabled() && hitflick.shouldFlick() && hitflick.armFlick(hit, event.getYaw())) {
                    resetAttackCycle();
                } else {
                    mc.thePlayer.swingItem();
                    mc.playerController.attackEntity(mc.thePlayer, hit);
                    resetAttackCycle();
                }
            } else if (hit == null
                    && RotationUtils.getDistanceToEntityBox(target) <= ATTACK_RANGE
                    && shouldMissClick(event)) {
                // Crosshair just slipped off, or is about to land: keep clicking like a player
                // would. Nothing is under the crosshair, so this is a plain miss-swing - attacking
                // an entity the ray doesn't touch is exactly what hitbox/raytrace checks catch.
                mc.thePlayer.swingItem();
                resetAttackCycle();
            }
            // A different entity under the crosshair (teammate, bot, armour stand) gets neither a
            // hit nor a swing - a player wouldn't click on it either.
        }
        wasUsingItem = usingItem;
    };


    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onServerMove = event -> {
        if (event.getPacket() instanceof S08PacketPlayerPosLook)
            setEnabled(false);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        if(target == null)
            return;
        int col = Arsenic.getInstance().getThemeManager().getCurrentTheme().getMainColor();
        RenderUtils.drawCircle(target, event.partialTicks, 0.7, col, 255);
    };

    public enum RotationMode {
        /** Turn at the configured Speed, arriving as fast as that allows. */
        Instant,
        /**
         * Human-shaped turns: flicks follow a minimum-jerk curve (speed up, peak, slow down) timed
         * to land by the tick the attack registers, sometimes overshoot slightly and correct, and
         * small errors are tracked with a loose follow rather than a hard lock.
         */
        Lazy
    }


    /**
     * The best target to aim at, if any is close enough. Aiming normally starts at attack range;
     * with exactly one candidate it starts {@link #preAim} further out, so the turn is already done
     * by the time they walk into reach instead of snapping the moment they do.
     */
    private Hitflick hitflick() {
        return Arsenic.getArsenic().getModuleManager().getModuleByClass(Hitflick.class);
    }

    private EntityPlayer pickTarget() {
        List<EntityPlayer> candidates = TargetManager.getTargets();
        double aimRange = ATTACK_RANGE + (candidates.size() == 1 ? preAim.getValue().getInput() : 0);
        for (EntityPlayer candidate : candidates) {
            if (RotationUtils.getDistanceToEntityBox(candidate) <= aimRange)
                return candidate;
        }
        return null;
    }

    /**
     * {@link RotationMode#Lazy}: shapes the turn itself and hands the manager the exact rotation
     * for this tick, with smoothing off so its own easing doesn't get layered on top.
     * <p>
     * A big error starts a flick along a minimum-jerk curve - the velocity profile real hand
     * movements follow - timed to land by the tick the next attack registers server side, so the
     * view isn't locked on for ticks before the swing that needed it. Larger flicks sometimes
     * overshoot a little. Once a flick lands (or the error was small to begin with) the aim
     * tracks with a loose proportional follow, which is also what corrects any overshoot.
     */
    private void lazyRotate(EventSilentRotation event, float[] rots) {
        SilentRotationManager srm = Arsenic.getArsenic().getSilentRotationManager();
        float curYaw = srm.yaw, curPitch = srm.pitch;
        float yawErr = RotationUtils.getYawDifference(rots[0], curYaw);
        float pitchErr = rots[1] - curPitch;
        float err = Math.max(Math.abs(yawErr), Math.abs(pitchErr));
        float maxSpeed = (float) speed.getValue().getMax();

        if (target != flickTarget) {
            flicking = false;
            flickTarget = target;
        }
        if (!flicking && err > FLICK_THRESHOLD)
            startFlick(yawErr, pitchErr, err, maxSpeed);

        float stepYaw, stepPitch;
        if (flicking) {
            float done = minJerk(flickTick / (float) flickDuration);
            float next = minJerk(Math.min(1f, (flickTick + 1) / (float) flickDuration));
            // Share of what's left to cover this tick. Re-applied to the live error every tick, so
            // the flick bends to follow a moving target instead of landing where it used to be.
            float frac = (next - done) / (1f - done);
            stepYaw = (yawErr + overshootYaw) * frac;
            stepPitch = (pitchErr + overshootPitch) * frac;
            if (++flickTick >= flickDuration)
                flicking = false;
        } else {
            float gain = random(0.5f, 0.75f);
            stepYaw = yawErr * gain;
            stepPitch = pitchErr * gain;
        }

        stepYaw = MathHelper.clamp_float(stepYaw, -maxSpeed, maxSpeed);
        stepPitch = MathHelper.clamp_float(stepPitch, -maxSpeed, maxSpeed);
        event.setYaw(curYaw + stepYaw);
        event.setPitch(MathHelper.clamp_float(curPitch + stepPitch, -90f, 90f));
        event.setSpeed(maxSpeed);
        event.setSmoothing(false);
    }

    private void startFlick(float yawErr, float pitchErr, float err, float maxSpeed) {
        // Budget: the wait until the next swing plus the one-way trip for it to reach the server.
        long remainingMs = Math.max(0L, currentAttackDelay - attackTimer.getTime());
        float budget = remainingMs / 50f + LagManager.getPingAsTicks() / 2f;
        // Minimum-jerk peaks at 1.875x its average speed - never plan a flick the Speed cap would cut.
        float minBySpeed = 1.875f * err / Math.max(1f, maxSpeed);
        float ticks = Math.max(budget, minBySpeed) * random(0.9f, 1.15f);
        flickDuration = MathHelper.clamp_int(Math.round(ticks), 2, 12);
        flickTick = 0;
        flicking = true;

        if (err > 25f && ThreadLocalRandom.current().nextFloat() < 0.35f) {
            float k = random(0.04f, 0.12f);
            overshootYaw = yawErr * k;
            overshootPitch = pitchErr * k * 0.5f;
        } else {
            overshootYaw = overshootPitch = 0f;
        }
    }

    /** Minimum-jerk position profile: 0 at s=0, 1 at s=1, zero velocity and acceleration at both ends. */
    private static float minJerk(float s) {
        return s * s * s * (10f + s * (-15f + 6f * s));
    }

    /**
     * Moves the drift point once per tick. While we're moving, the aim eases onto a point that
     * wanders slowly around the target's chest; standing still, it eases back to the nearest point
     * of the box. The blend is eased both ways, so switching never snaps the aim.
     */
    private void updateDrift() {
        double mx = mc.thePlayer.posX - mc.thePlayer.lastTickPosX;
        double mz = mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ;
        boolean moving = mx * mx + mz * mz > 1.0E-4;
        driftBlend += ((moving ? 1f : 0f) - driftBlend) * 0.2f;

        if (--driftTicksLeft <= 0) {
            driftGoalX = random(0.25f, 0.75f);
            driftGoalY = random(0.5f, 0.85f);
            driftGoalZ = random(0.25f, 0.75f);
            driftTicksLeft = ThreadLocalRandom.current().nextInt(8, 26);
        }
        driftX += (driftGoalX - driftX) * 0.15f;
        driftY += (driftGoalY - driftY) * 0.15f;
        driftZ += (driftGoalZ - driftZ) * 0.15f;
    }

    private void resetAttackCycle() {
        // Attacks can only fire on a tick, so the timer always passes the delay by up to a tick
        // before one goes out. Carry that overrun into the next cycle - otherwise every delay
        // rounds up to the next tick and real APS lands well under the setting (which is what the
        // old "+ 6" on the APS was papering over). An overrun of a tick or more means we were idle
        // rather than rounding, and isn't carried, so a pause never banks a burst.
        long now = System.currentTimeMillis();
        long overrun = now - (attackTimer.lastMS + currentAttackDelay);
        attackTimer.setTime(now - (overrun >= 0 && overrun < 50 ? overrun : 0));
        currentAttackDelay = getAttackDelay(); // roll the next cycle once, here
    }

    /**
     * Whether the aura should keep clicking while the crosshair is off the target: either it was on
     * the target within the grace window, or it will be within the grace window - because the
     * target is walking into the current aim line, or because the rotation is turning onto it fast
     * enough to get there in time.
     */
    private boolean shouldMissClick(EventSilentRotation.Post event) {
        double graceMs = clickGrace.getValue().getInput();
        if (graceMs <= 0)
            return false;
        if (everOnTarget && onTargetTimer.getTime() <= graceMs)
            return true;

        int lookahead = Math.max(1, (int) Math.round(graceMs / 50.0));
        Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
        Vec3 look = ((IMixinEntity) mc.thePlayer).invokeGetVectorForRotation(event.getPitch(), event.getYaw());
        Vec3 end = eyes.addVector(look.xCoord * ATTACK_RANGE, look.yCoord * ATTACK_RANGE, look.zCoord * ATTACK_RANGE);
        float border = target.getCollisionBorderSize();
        for (int t = 1; t <= lookahead; t++) {
            AxisAlignedBB box = predictBox(target, t).expand(border, border, border);
            if (box.isVecInside(eyes) || box.calculateIntercept(eyes, end) != null)
                return true;
        }

        // Turning onto the target: will the rotation arrive within the window at its current speed?
        float speed = Math.max(event.getSpeed(), 0.01f);
        float[] rots = getPredictedRotations(target, lookahead);
        float yawDelta = Math.abs(RotationUtils.getYawDifference(rots[0], event.getYaw()));
        float pitchDelta = Math.abs(rots[1] - event.getPitch());
        return Math.max(yawDelta, pitchDelta) / speed <= lookahead;
    }

    /**
     * {@link RotationUtils#getRotationsToEntity} aimed at where the target will be {@code ticks}
     * from now, relative to our own movement, instead of where it is this tick. The point on the
     * box is the nearest one blended toward the drift point (see {@link #updateDrift()}); both lie
     * inside the box, so the blend does too.
     */
    private float[] getPredictedRotations(Entity e, float ticks) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
        AxisAlignedBB box = predictBox(e, ticks);
        double x = aimCoord(eyes.xCoord, box.minX, box.maxX, driftX) - eyes.xCoord;
        double y = aimCoord(eyes.yCoord, box.minY, box.maxY, driftY) - eyes.yCoord;
        double z = aimCoord(eyes.zCoord, box.minZ, box.maxZ, driftZ) - eyes.zCoord;
        double dist = MathHelper.sqrt_double(x * x + z * z);
        float yaw = (float) Math.toDegrees(Math.atan2(z, x)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(y, dist));
        return new float[]{yaw, pitch};
    }

    /**
     * The target's box extrapolated {@code ticks} ahead using its horizontal velocity relative to
     * ours. Vertical motion is left out - jumps and falls arc, so a straight-line guess overshoots.
     */
    private AxisAlignedBB predictBox(Entity e, float ticks) {
        if (ticks <= 0)
            return e.getEntityBoundingBox();
        double dx = (e.posX - e.lastTickPosX) - (mc.thePlayer.posX - mc.thePlayer.lastTickPosX);
        double dz = (e.posZ - e.lastTickPosZ) - (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ);
        return e.getEntityBoundingBox().offset(dx * ticks, 0, dz * ticks);
    }

    private double aimCoord(double eye, double min, double max, float drift) {
        double nearest = MathHelper.clamp_double(eye, min, max);
        double wander = min + (max - min) * drift;
        return nearest + (wander - nearest) * driftBlend;
    }

    private long getAttackDelay() {
        return (long) (1000.0 / aps.getValue().getRandomInRange());
    }

    private static float random(float min, float max) {
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }
}
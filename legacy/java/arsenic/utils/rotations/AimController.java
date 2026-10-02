package arsenic.utils.rotations;

import arsenic.event.impl.EventSilentRotation;
import arsenic.main.Arsenic;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The aim shared by KillAura and AimAssist: where on the target to look (a prediction-led point
 * that drifts around the chest while moving), and how to turn onto it ({@link RotationMode}).
 * Each module owns its own instance, since the drift and flick state are per-aimer.
 */
public class AimController {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /** Error (degrees) past which Lazy treats the turn as a flick rather than tracking. */
    private static final float FLICK_THRESHOLD = 12f;

    public enum RotationMode {
        /** Turn at the configured Speed, arriving as fast as that allows. */
        Instant,
        /**
         * Human-shaped turns: flicks follow a minimum-jerk curve (speed up, peak, slow down) timed
         * to land by a caller-supplied deadline, sometimes overshoot slightly and correct, and
         * small errors are tracked with a loose follow rather than a hard lock.
         */
        Lazy
    }

    // Aim point drift, as fractions of the target's box (0 = min edge, 1 = max edge).
    private float driftX = 0.5f, driftY = 0.65f, driftZ = 0.5f;
    private float driftGoalX = 0.5f, driftGoalY = 0.65f, driftGoalZ = 0.5f;
    private int driftTicksLeft = 0;
    /** 0 = aim at the nearest point of the box, 1 = aim at the drift point. Eased, never snapped. */
    private float driftBlend = 0f;

    // Lazy flick state.
    private boolean flicking = false;
    private int flickTick, flickDuration;
    private float overshootYaw, overshootPitch;
    private Entity flickTarget = null;

    public void reset() {
        flicking = false;
        flickTarget = null;
        driftBlend = 0f;
    }

    /** Drops any flick in progress, e.g. when the target is lost. */
    public void cancelFlick() {
        flicking = false;
        flickTarget = null;
    }

    /**
     * Turns {@code event} onto {@code rots} for this tick.
     *
     * @param minSpeed     lower bound of the Instant turn speed (degrees per tick)
     * @param maxSpeed     upper bound; also Lazy's hard cap
     * @param budgetTicks  Lazy only: ticks a new flick should take to land, e.g. until the next
     *                     attack registers. 0 lets the speed cap alone decide.
     */
    public void rotate(EventSilentRotation event, Entity target, float[] rots, RotationMode mode,
                       float minSpeed, float maxSpeed, float budgetTicks) {
        if (mode == RotationMode.Lazy) {
            lazyRotate(event, target, rots, maxSpeed, budgetTicks);
        } else {
            event.setYaw(rots[0]);
            event.setPitch(rots[1]);
            event.setSpeed(random(minSpeed, maxSpeed));
        }
    }

    /**
     * {@link RotationMode#Lazy}: shapes the turn itself and hands the manager the exact rotation
     * for this tick, with smoothing off so its own easing doesn't get layered on top.
     * <p>
     * A big error starts a flick along a minimum-jerk curve - the velocity profile real hand
     * movements follow - timed to land by the budget, so the view isn't locked on for ticks before
     * it's needed. Larger flicks sometimes overshoot a little. Once a flick lands (or the error was
     * small to begin with) the aim tracks with a loose proportional follow, which is also what
     * corrects any overshoot.
     */
    private void lazyRotate(EventSilentRotation event, Entity target, float[] rots, float maxSpeed, float budgetTicks) {
        SilentRotationManager srm = Arsenic.getArsenic().getSilentRotationManager();
        float curYaw = srm.yaw, curPitch = srm.pitch;
        float yawErr = RotationUtils.getYawDifference(rots[0], curYaw);
        float pitchErr = rots[1] - curPitch;
        float err = Math.max(Math.abs(yawErr), Math.abs(pitchErr));

        if (target != flickTarget) {
            flicking = false;
            flickTarget = target;
        }
        if (!flicking && err > FLICK_THRESHOLD)
            startFlick(yawErr, pitchErr, err, maxSpeed, budgetTicks);

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

    private void startFlick(float yawErr, float pitchErr, float err, float maxSpeed, float budgetTicks) {
        // Minimum-jerk peaks at 1.875x its average speed - never plan a flick the Speed cap would cut.
        float minBySpeed = 1.875f * err / Math.max(1f, maxSpeed);
        float ticks = Math.max(budgetTicks, minBySpeed) * random(0.9f, 1.15f);
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
    public void updateDrift() {
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

    /**
     * {@link RotationUtils#getRotationsToEntity} aimed at where the target will be {@code ticks}
     * from now, relative to our own movement, instead of where it is this tick. The point on the
     * box is the nearest one blended toward the drift point (see {@link #updateDrift()}); both lie
     * inside the box, so the blend does too.
     */
    public float[] getPredictedRotations(Entity e, float ticks) {
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
    public AxisAlignedBB predictBox(Entity e, float ticks) {
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

    private static float random(float min, float max) {
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }
}

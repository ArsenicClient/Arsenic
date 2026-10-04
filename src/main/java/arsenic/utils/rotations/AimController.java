package arsenic.utils.rotations;

import arsenic.event.impl.EventSilentRotation;
import arsenic.main.Arsenic;
import arsenic.utils.aimcore.AimCore;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The aim shared by KillAura and AimAssist: where on the target to look, and how to turn onto it
 * ({@link RotationMode}). The maths live in {@link AimCore}; this only feeds it the game's
 * state. Each module owns its own
 * instance, since the aim's state is per-aimer.
 * <p>
 * The aim keeps the crosshair where it already is on the target's hitbox and only turns when the
 * box would slide out from under it, instead of chasing a point on the box every tick. Chasing the
 * box point nearest our eyes was what made the pitch jitter: whenever either player jumped or got
 * knocked up, that point jumped between eye level and the top of the head, and up close the pitch
 * to it swung towards straight down with every step.
 */
public class AimController {

    private static final Minecraft mc = Minecraft.getMinecraft();

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

    private final AimCore core = new AimCore(AimCore.Tuning.best(), new Random());

    /** The tuned lead, in ticks, for callers without a setting of their own. */
    public float defaultPrediction() {
        return core.tun.predictionTicks;
    }

    public void reset() {
        core.reset();
    }

    /** Drops any flick in progress, e.g. when the target is lost. */
    public void cancelFlick() {
        core.cancelFlick();
    }

    /** Once per tick, target or not. */
    public void updateDrift() {
        core.updateDrift(input(null));
    }

    /**
     * This tick's rotation to aim at {@code e}, leading it by {@code ticks}. Stateful: call it once
     * per tick, for the target actually being aimed at.
     */
    public float[] aimAt(Entity e, float ticks) {
        AimCore.Input in = input(e);
        core.observe(in);
        return core.aimRotations(in, ticks);
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
            AimCore.Input in = input(target);
            in.maxSpeed = maxSpeed;
            in.budgetTicks = budgetTicks;
            float[] out = core.lazyStep(in, rots);
            event.setYaw(out[0]);
            event.setPitch(out[1]);
            event.setSpeed(maxSpeed);
            // the turn is already shaped: the manager applies it as-is
            event.setSmoothing(false);
        } else {
            event.setYaw(rots[0]);
            event.setPitch(rots[1]);
            event.setSpeed(minSpeed + ThreadLocalRandom.current().nextFloat() * (maxSpeed - minSpeed));
        }
    }

    /**
     * The rotation that would aim at {@code e} led by {@code ticks}, without changing any aim state
     * (for "will we be on target soon" checks).
     */
    public float[] getPredictedRotations(Entity e, float ticks) {
        return core.peekRotations(input(e), ticks);
    }

    /** The target's box extrapolated {@code ticks} ahead horizontally, as the aim leads it. */
    public AxisAlignedBB predictBox(Entity e, float ticks) {
        double[] o = core.predictOffset(input(e), ticks);
        return e.getEntityBoundingBox().offset(o[0], 0, o[1]);
    }

    private AimCore.Input input(Entity e) {
        AimCore.Input in = new AimCore.Input();
        Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
        in.eyeX = eyes.xCoord;
        in.eyeY = eyes.yCoord;
        in.eyeZ = eyes.zCoord;
        in.selfDX = mc.thePlayer.posX - mc.thePlayer.lastTickPosX;
        in.selfDZ = mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ;
        in.selfMotionX = mc.thePlayer.motionX;
        in.selfMotionZ = mc.thePlayer.motionZ;
        SilentRotationManager srm = Arsenic.getArsenic().getSilentRotationManager();
        in.curYaw = srm.yaw;
        in.curPitch = srm.pitch;
        if (e != null) {
            AxisAlignedBB box = e.getEntityBoundingBox();
            in.minX = box.minX;
            in.minY = box.minY;
            in.minZ = box.minZ;
            in.maxX = box.maxX;
            in.maxY = box.maxY;
            in.maxZ = box.maxZ;
            in.targetDX = e.posX - e.lastTickPosX;
            in.targetDZ = e.posZ - e.lastTickPosZ;
            in.targetId = e.getEntityId();
        }
        return in;
    }
}

package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import static net.minecraft.util.MathHelper.wrapAngleTo180_float;

/**
 * Nudges the player's aim toward the current target while they are attacking.
 * <p>
 * The correction is expressed in <b>degrees per tick</b> and applied through {@code setAngles},
 * which is where Minecraft turns raw mouse movement into a rotation change. The module drives the
 * view directly, ignoring mouse input while a target is up - the other three modes this used to
 * offer (Silent, Additive, Adaptive) were removed; Normal is the only behaviour now.
 */
@ModuleInfo(name = "AimAssist", category = ModuleCategory.COMBAT)
public class AimAssist extends Module {

    public final DoubleProperty speed = new DoubleProperty("Speed", new DoubleValue(1, 50, 10, 1));

    /**
     * {@code Entity.setAngles} multiplies whatever it is handed by 0.15 before applying it:
     * {@code rotationYaw += yaw * 0.15}. Handing it a value in degrees therefore moved the view by
     * 15% of that, so every correction this module computed was silently cut to a seventh of its
     * intended size and the Speed slider topped out at 7.5 deg/tick instead of 50. Dividing the
     * correction by this before returning it makes Speed mean what it says.
     */
    private static final float SET_ANGLES_SCALE = 0.15f;

    /**
     * Amplitude, in degrees, of the wobble laid over the target point.
     * <p>
     * This was {@code Math.random() - Math.random()}: plus or minus a full degree of fresh white
     * noise every tick. Against a correction of a couple of degrees that is enormous, it never
     * settles because it is uncorrelated frame to frame, and it reads as a shake rather than as a
     * hand. Two slow sine waves at a fraction of the amplitude drift instead of jitter.
     */
    private static final float WOBBLE_YAW = 0.35f;
    private static final float WOBBLE_PITCH = 0.2f;

    private float yawDelta, pitchDelta;
    private EntityLivingBase target;

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventTickListener = event -> {
        if (!mc.gameSettings.keyBindAttack.isKeyDown()) {
            clearTarget();
            return;
        }

        target = TargetManager.getTarget();
        if (target == null || isBehindWall(target)) {
            clearTarget();
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> rayTraceListener = event -> {
        if (target == null) {
            yawDelta = 0;
            pitchDelta = 0;
            return;
        }

        float[] rots = aimRotations(target);
        yawDelta = yawStep(rots[0]);
        pitchDelta = pitchStep(rots[1]);
    };

    /** Rotations to the target's nearest hittable point, with the humanising wobble applied. */
    private float[] aimRotations(EntityLivingBase entity) {
        float[] rots = RotationUtils.getRotationsToEntity(entity);
        if (rots == null)
            return new float[]{mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch};
        double t = System.currentTimeMillis() / 1000.0;
        rots[0] += (float) (Math.sin(t * 2.7) * 0.6 + Math.sin(t * 6.1) * 0.4) * WOBBLE_YAW;
        rots[1] += (float) (Math.sin(t * 3.3) * 0.6 + Math.sin(t * 7.9) * 0.4) * WOBBLE_PITCH;
        return rots;
    }

    /**
     * Line of sight test against terrain.
     * <p>
     * This replaces a check on {@code objectMouseOver} that turned the module off whenever the
     * crosshair was over any non-liquid block. Since being off-target usually means the crosshair is
     * on the ground or a wall behind the opponent, that disabled the assist in precisely the
     * situation it exists to fix. What was presumably intended - don't help aim through terrain -
     * is what this does: trace from the eyes to the target's own hitbox rather than wherever the
     * crosshair happens to be pointing.
     */
    private boolean isBehindWall(EntityLivingBase entity) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
        Vec3 aim = RotationUtils.getBestHitVec(entity);
        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyes, aim, false, true, false);
        return mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
    }

    /** Degrees of yaw to move this tick, capped by Speed and never overshooting the target. */
    private float yawStep(float targetYaw) {
        float delta = wrapAngleTo180_float(
                wrapAngleTo180_float(targetYaw) - wrapAngleTo180_float(mc.thePlayer.rotationYaw));
        return step(delta);
    }

    /** Degrees of pitch to move this tick. Pitch does not wrap, so the raw difference is correct. */
    private float pitchStep(float targetPitch) {
        return step(targetPitch - mc.thePlayer.rotationPitch);
    }

    /**
     * Eases the correction: fast while far off, slowing as the crosshair closes on the target so it
     * settles instead of snapping and oscillating. Never larger than the remaining distance, so the
     * aim cannot overshoot and bounce back.
     */
    private float step(float delta) {
        float magnitude = Math.abs(delta);
        if (magnitude < 0.01f)
            return 0f;
        float max = (float) speed.getValue().getInput();
        float eased = max * Math.min(1f, 0.25f + magnitude / 30f);
        return Math.min(eased, magnitude) * Math.signum(delta);
    }

    private void clearTarget() {
        pitchDelta = 0;
        yawDelta = 0;
        this.target = null;
    }

    /**
     * @param yaw the player's raw mouse delta, before {@code setAngles} scales it
     * @return the value {@code setAngles} should use instead
     */
    public float modifyYaw(float yaw) {
        if (target == null)
            return yaw;

        // Replaces the player's input outright: the view goes where the module says.
        return yawDelta / SET_ANGLES_SCALE;
    }

    /**
     * @param pitch the player's raw mouse delta. {@code setAngles} <em>subtracts</em> pitch
     *              ({@code rotationPitch -= pitch * 0.15}), so a correction that should raise the
     *              aim has to be handed over negated.
     */
    public float modifyPitch(float pitch) {
        if (target == null)
            return pitch;

        return -pitchDelta / SET_ANGLES_SCALE;
    }
}

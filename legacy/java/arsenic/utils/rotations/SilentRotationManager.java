package arsenic.utils.rotations;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.main.Arsenic;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

import static net.minecraft.util.MathHelper.wrapAngleTo180_float;

public class SilentRotationManager {


    private final Minecraft mc = Minecraft.getMinecraft();
    public float yaw;
    private float prevYaw;
    public float pitch ;
    private float prevPitch;
    private boolean modified;
    private MovementFix movementFix = MovementFix.SILENT;
    private boolean doJumpFix;
    private boolean blockUserInput;
    private float lastPlaceYawDelta = -1f;
    private boolean smoothing = true;
    private float speed;
    private float yawMomentum = 0;
    private float pitchMomentum = 0;
    private static final float MOMENTUM_BLEND = 0.45f;

    @EventLink
    public final Listener<EventLiving> eventTickListener = event -> {
        EventSilentRotation rotation = new EventSilentRotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, speed);
        Arsenic.getArsenic().getEventManager().post(rotation);
        prevYaw = yaw;
        prevPitch = pitch;

        movementFix = rotation.getMovementFix();
        doJumpFix = rotation.doJumpFix();
        speed = rotation.getSpeed();
        smoothing = rotation.isSmoothing();
        blockUserInput = rotation.isBlockUserInput();


        if (!rotation.hasBeenModified() && !modified) {
            yaw = snapYawToMultipleOf360(yaw, mc.thePlayer.rotationYaw);
            mc.thePlayer.rotationYaw = yaw;
            pitch = mc.thePlayer.rotationPitch;
            modified = false;
            yawMomentum = 0;
            pitchMomentum = 0;
            postSettled();
            return;
        }

        float yawDelta = getYawDelta(rotation.getYaw());
        float pitchDelta = getPitchDelta(rotation.getPitch());
        yaw = prevYaw + yawDelta;
        pitch = prevPitch + pitchDelta;

        float[] rotations = RotationUtils.patchGCD(
                new float[]{prevYaw, prevPitch},
                new float[]{yaw, pitch}
        );

        yaw = rotations[0];
        pitch = Math.min(90,rotations[1]);

        if (rotation.isPreventDuplicateLook()) {
            float currentDelta = Math.abs(yaw - prevYaw);

            if (currentDelta >= 2) {
                float xDiff = Math.abs(currentDelta - lastPlaceYawDelta);

                if (xDiff < 0.002F) {
                    float gcd = RotationUtils.getGCD();
                    yaw += gcd;
                }
                lastPlaceYawDelta = Math.abs(yaw - prevYaw);
            }
        }

        modified = rotation.hasBeenModified() || (Math.abs(RotationUtils.getYawDifference(mc.thePlayer.rotationYaw, yaw)) > speed);
        if(!modified && !rotation.hasBeenModified()) {
            yaw = snapYawToMultipleOf360(yaw, mc.thePlayer.rotationYaw);
            mc.thePlayer.rotationYaw = yaw;
            pitch = mc.thePlayer.rotationPitch;
        }
        postSettled();
    };

    /**
     * @return whether the player's own attack/use input should be swallowed this tick.
     *         Set per tick via {@link EventSilentRotation#setBlockUserInput(boolean)}; off by default.
     */
    public boolean isBlockingUserInput() {
        return blockUserInput && mc.thePlayer != null;
    }

    private void postSettled() {
        Arsenic.getArsenic().getEventManager().post(
                new EventSilentRotation.Post(yaw, pitch, prevYaw, prevPitch, modified, speed));
    }

    @EventLink
    public final Listener<EventUpdate.Pre> eventUpdateListener = event -> {
        if(!modified)
            return;
        event.setYaw(yaw);
        event.setPitch(pitch);
    };

    @EventLink
    public final Listener<EventLook> eventLookListener = event -> {
        if(!modified)
            return;
        event.setYaw(yaw);
        event.setPitch(pitch);
    };

    @EventLink
    public final Listener<EventRenderThirdPerson> eventRenderThirdPersonListener = event -> {
        if(!modified)
            return;
        event.setAccepted(true);
        event.setYaw(yaw);
        event.setPrevYaw(prevYaw);
        event.setPitch(pitch);
        event.setPrevPitch(prevPitch);
    };

    @EventLink
    public final Listener<EventMove> eventMoveListener = event -> {
        // OFF: no movement fix. STRICT/SILENT: motion is computed with the silent yaw so the
        // server-side prediction matches the rotation we send.
        if(!modified || movementFix == MovementFix.OFF)
            return;

        event.setYaw(yaw);
    };

    @EventLink
    public final Listener<EventMovementInput> eventMovementInputListener = event -> {
        // Only SILENT rewrites the movement keys so the intended travel direction is preserved
        // under the silent yaw. STRICT leaves the keys alone (player moves relative to the yaw).
        if(!modified || movementFix != MovementFix.SILENT || (event.getSpeed() == 0 && event.getStrafe() == 0))
            return;
        float moveAngle = wrapAngleToPi(normaliseYaw(mc.thePlayer.rotationYaw) + (float) Math.atan2(-event.getStrafe(), event.getSpeed()));
        float moveKeyAngle = wrapAngleToPi(moveAngle - (float) Math.toRadians(yaw));
        float speedValue = (moveKeyAngle >= -Math.PI * 3/8 && moveKeyAngle <= Math.PI * 3/8) ? 1 : (moveKeyAngle <= -Math.PI * 5/8 || moveKeyAngle >= Math.PI * 5/8) ? -1 : 0;
        float strafeValue = (moveKeyAngle >= Math.PI/8 && moveKeyAngle <= Math.PI * 7/8) ? -1 : (moveKeyAngle <= -Math.PI/8 && moveKeyAngle >= -Math.PI * 7/8) ? 1 : 0;
        if(mc.thePlayer.isSneaking()) {
            speedValue *= 0.3F;
            strafeValue *= 0.3F;
        }
        event.setSpeed(speedValue);
        event.setStrafe(strafeValue);
    };

    @EventLink
    public final Listener<EventJump> eventJumpListener = event -> {
        if(!modified || !doJumpFix)
            return;
        event.setYaw(yaw);
    };

    public float normaliseYaw(float yaw) {
        return (float) Math.toRadians(MathHelper.wrapAngleTo180_float(yaw));
    }

    public static float wrapAngleToPi(float value) {
        float twoPi = (float)(2.0 * Math.PI);
        value = (value + (float)Math.PI) % twoPi;
        if (value < 0) {
            value += twoPi;
        }
        return value - (float)Math.PI;
    }


    private float getYawDelta(float targetYaw) {
        float delta = wrapAngleTo180_float(wrapAngleTo180_float(targetYaw) - wrapAngleTo180_float(prevYaw));
        float absDelta = Math.abs(delta);

        if (!smoothing) {
            // Caller shapes its own motion: apply it as-is, and keep momentum in step so a later
            // smoothed tick carries on from the real speed instead of a stale one.
            yawMomentum = Math.min(speed, absDelta);
            return yawMomentum * Math.signum(delta);
        }

        // Ease-out quadratic: fast when far from target, decelerates as approaching
        float t = Math.min(1.0f, absDelta / 120.0f);
        float eased = t * (2.0f - t);
        float targetSpeed = speed * Math.max(0.12f, eased);

        // Momentum blending prevents jerky speed changes between ticks
        yawMomentum += (targetSpeed - yawMomentum) * MOMENTUM_BLEND;
        yawMomentum = Math.max(0, yawMomentum);

        float result = Math.min(yawMomentum, absDelta) * Math.signum(delta);
        return result;
    }

    private float getPitchDelta(float targetPitch) {
        float delta = targetPitch - prevPitch;
        float absDelta = Math.abs(delta);

        if (!smoothing) {
            pitchMomentum = Math.min(speed, absDelta);
            return pitchMomentum * Math.signum(delta);
        }

        float t = Math.min(1.0f, absDelta / 60.0f);
        float eased = t * (2.0f - t);
        float targetSpeed = (speed * 0.65f) * Math.max(0.15f, eased);

        pitchMomentum += (targetSpeed - pitchMomentum) * MOMENTUM_BLEND;
        pitchMomentum = Math.max(0, pitchMomentum);

        float result = Math.min(pitchMomentum, absDelta) * Math.signum(delta);
        return result;
    }

    private float snapYawToMultipleOf360(float currentYaw, float targetYaw) {
        float diff = currentYaw - targetYaw;
        float n = Math.round(diff / 360.0f);
        return targetYaw + n * 360.0f;
    }

    /**
     * OFF    — no movement correction at all.
     * STRICT — motion is computed with the silent yaw, but the movement keys are left untouched.
     * SILENT — motion is computed with the silent yaw AND the movement keys are rewritten so the
     *          player still travels in the direction they intended.
     */
    public enum MovementFix {
        OFF,
        STRICT,
        SILENT
    }

}

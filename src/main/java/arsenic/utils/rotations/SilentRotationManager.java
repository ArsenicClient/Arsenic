package arsenic.utils.rotations;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.main.Arsenic;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;



public class SilentRotationManager {


    private final Minecraft mc = Minecraft.getInstance();
    public float yaw;
    private float prevYaw;
    public float pitch ;
    private float prevPitch;
    private boolean modified;
    private boolean prevModified;
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
        EventSilentRotation rotation = new EventSilentRotation(mc.player.getYRot(), mc.player.getXRot(), speed);
        Arsenic.getArsenic().getEventManager().post(rotation);
        prevYaw = yaw;
        prevPitch = pitch;
        prevModified = modified;

        movementFix = rotation.getMovementFix();
        doJumpFix = rotation.doJumpFix();
        speed = rotation.getSpeed();
        smoothing = rotation.isSmoothing();
        blockUserInput = rotation.isBlockUserInput();


        if (!rotation.hasBeenModified() && !modified) {
            yaw = snapYawToMultipleOf360(yaw, mc.player.getYRot());
            mc.player.setYRot(yaw);
            pitch = mc.player.getXRot();
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

        modified = rotation.hasBeenModified() || (Math.abs(RotationUtils.getYawDifference(mc.player.getYRot(), yaw)) > speed);
        if(!modified && !rotation.hasBeenModified()) {
            yaw = snapYawToMultipleOf360(yaw, mc.player.getYRot());
            mc.player.setYRot(yaw);
            pitch = mc.player.getXRot();
        }
        postSettled();
    };

    public boolean isBlockingUserInput() {
        return blockUserInput && mc.player != null;
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
        if(!modified && !prevModified)
            return;
        event.setAccepted(true);
        event.setYaw(yaw);
        event.setPrevYaw(prevYaw);
        event.setPitch(pitch);
        event.setPrevPitch(prevPitch);
    };

    @EventLink
    public final Listener<EventMove> eventMoveListener = event -> {
        if(!modified || movementFix == MovementFix.OFF)
            return;

        event.setYaw(yaw);
    };

    @EventLink
    public final Listener<EventMovementInput> eventMovementInputListener = event -> {
        if(!modified || movementFix != MovementFix.SILENT || (event.getSpeed() == 0 && event.getStrafe() == 0))
            return;
        float moveAngle = wrapAngleToPi(normaliseYaw(mc.player.getYRot()) + (float) Math.atan2(-event.getStrafe(), event.getSpeed()));
        float moveKeyAngle = wrapAngleToPi(moveAngle - (float) Math.toRadians(yaw));
        float speedValue = (moveKeyAngle >= -Math.PI * 3/8 && moveKeyAngle <= Math.PI * 3/8) ? 1 : (moveKeyAngle <= -Math.PI * 5/8 || moveKeyAngle >= Math.PI * 5/8) ? -1 : 0;
        float strafeValue = (moveKeyAngle >= Math.PI/8 && moveKeyAngle <= Math.PI * 7/8) ? -1 : (moveKeyAngle <= -Math.PI/8 && moveKeyAngle >= -Math.PI * 7/8) ? 1 : 0;
        // No sneak scaling here: 1.8 had already applied it to the keys, modern applies it later.
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
        return (float) Math.toRadians(Mth.wrapDegrees(yaw));
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
        float delta = Mth.wrapDegrees(Mth.wrapDegrees(targetYaw) - Mth.wrapDegrees(prevYaw));
        float absDelta = Math.abs(delta);

        if (!smoothing) {
            yawMomentum = Math.min(speed, absDelta);
            return yawMomentum * Math.signum(delta);
        }

        float t = Math.min(1.0f, absDelta / 120.0f);
        float eased = t * (2.0f - t);
        float targetSpeed = speed * Math.max(0.12f, eased);

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

    public enum MovementFix {
        OFF,
        STRICT,
        SILENT
    }

}

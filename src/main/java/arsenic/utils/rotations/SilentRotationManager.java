package arsenic.utils.rotations;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.main.Arsenic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MathHelper;

import static net.minecraft.util.MathHelper.wrapAngleTo180_float;

public class SilentRotationManager {


    private final Minecraft mc = Minecraft.getMinecraft();
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

    /** Player instance seen on the previous tick, used to detect rejoins / server switches / dimension changes. */
    private EntityPlayerSP lastPlayer;

    @EventLink
    public final Listener<EventLiving> eventTickListener = event -> {
        if (mc.thePlayer != lastPlayer) {
            lastPlayer = mc.thePlayer;
            resetState();
        }

        EventSilentRotation rotation = new EventSilentRotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, speed);
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
            syncPlayerYaw();
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
            syncPlayerYaw();
            pitch = mc.thePlayer.rotationPitch;
        }
        postSettled();
    };

    /**
     * Resets all internal rotation state to the current player's rotation.
     * Called when a new player entity is detected (rejoin, server switch, dimension change),
     * so stale values from the previous session are never applied to the new player.
     */
    private void resetState() {
        yaw = prevYaw = mc.thePlayer.rotationYaw;
        pitch = prevPitch = mc.thePlayer.rotationPitch;
        modified = prevModified = false;
        yawMomentum = 0;
        pitchMomentum = 0;
        lastPlaceYawDelta = -1f;
    }

    /**
     * Aligns the player's rotationYaw with the internal yaw (same angle modulo 360)
     * WITHOUT causing a visual camera spin.
     *
     * Previously only rotationYaw was snapped, while prevRotationYaw stayed untouched.
     * Rendering interpolates between the two, so a 360°/720° snap turned into a
     * full camera spin within one tick (and caused chunks to unload/reload).
     * Here every interpolated yaw field is shifted by the same offset.
     */
    private void syncPlayerYaw() {
        EntityPlayerSP player = mc.thePlayer;
        float snapped = snapYawToMultipleOf360(yaw, player.rotationYaw);
        float offset = snapped - player.rotationYaw;

        if (offset != 0f) {
            player.rotationYaw += offset;
            player.prevRotationYaw += offset;
            player.rotationYawHead += offset;
            player.prevRotationYawHead += offset;
            player.renderYawOffset += offset;
            player.prevRenderYawOffset += offset;
            player.renderArmYaw += offset;
            player.prevRenderArmYaw += offset;
        }

        yaw = player.rotationYaw;
    }

    public boolean isBlockingUserInput() {
        return blockUserInput && mc.thePlayer != null;
    }

    public boolean isModified() { return modified; }

    public boolean isPrevModified() { return prevModified; }

    public float getPrevYaw() { return prevYaw; }

    public float getPrevPitch() { return prevPitch; }

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

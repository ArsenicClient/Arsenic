package arsenic.utils.rotations;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.main.Arsenic;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

public class SilentRotationManager {


    private final Minecraft mc = Minecraft.getMinecraft();
    public float yaw;
    private float prevYaw;
    public float pitch ;
    private float prevPitch;
    private boolean modified;
    private boolean doMovementFix;
    private boolean doJumpFix;
    private float speed;

    @EventLink
    public final Listener<EventLiving> eventTickListener = event -> {
        EventSilentRotation rotation = new EventSilentRotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, speed);
        Arsenic.getArsenic().getEventManager().post(rotation);
        prevYaw = yaw;
        prevPitch = pitch;

        if(!modified && !rotation.hasBeenModified()) {
            yaw = mc.thePlayer.rotationYaw;
            pitch = mc.thePlayer.rotationPitch;
            return;
        }

        doMovementFix = rotation.doMovementFix();
        doJumpFix = rotation.doJumpFix();
        speed = rotation.getSpeed();

        float[] patchedRots = RotationUtils.getPatchedAndCappedRots(
                new float[]{prevYaw,prevPitch},
                new float[]{rotation.getYaw(), rotation.getPitch()},
                speed
        );
        yaw = patchedRots[0];
        pitch = patchedRots[1];
        modified = rotation.hasBeenModified() || (Math.abs(RotationUtils.getYawDifference(mc.thePlayer.rotationYaw, yaw)) > speed);
    };

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
        if(!modified || !doMovementFix)
            return;

        event.setYaw(yaw);
    };

    @EventLink
    public final Listener<EventMovementInput> eventMovementInputListener = event -> {
        if(!modified || !doMovementFix)
            return;

        if(event.getSpeed() == 0 && event.getStrafe() == 0)
            return;

        // Get player's current yaw
        float playerYaw = normaliseYaw(mc.thePlayer.rotationYaw);

        PlayerUtils.addWaterMarkedMessageToChat( "§a=== Movement Fix Debug ===");
        PlayerUtils.addWaterMarkedMessageToChat( "§bPlayer Current Yaw: §f", String.format("%.2f", Math.toDegrees(playerYaw)));
        PlayerUtils.addWaterMarkedMessageToChat( "§bPlayer Current Forward: §f", event.getSpeed());
        PlayerUtils.addWaterMarkedMessageToChat( "§bPlayer Current Strafe: §f",  event.getStrafe());

        // Calculate movement angle
        float playerMoveAngle = (float) Math.atan2(event.getStrafe(), event.getSpeed());
        float playerMoveAngleDegrees = (float) Math.toDegrees(playerMoveAngle);

        PlayerUtils.addWaterMarkedMessageToChat( "§bPlayer Move Angle: §f" + String.format("%.2f°", playerMoveAngleDegrees));

        //calc the direction the player wants to go
        float moveAngle = wrapAngleToPi(playerYaw + playerMoveAngle);
        float moveAngleDegrees = (float) Math.toDegrees(moveAngle);

        //calc the direction the movement keys need to go
        PlayerUtils.addWaterMarkedMessageToChat( "§bMove Angle: §f" + String.format("%.2f°", moveAngleDegrees));

        //calc the direction that the movementKeys will press
        float moveKeyAngle = wrapAngleToPi((float) (Math.toRadians(yaw) + moveAngle));
        float moveKeyAngleDegrees = (float) Math.toDegrees(moveKeyAngle);

        PlayerUtils.addWaterMarkedMessageToChat( "§bMove Key Angle: §f" + String.format("%.2f°", moveKeyAngleDegrees));

        // Calculate keys
        int forwardKey =
                (moveKeyAngle >= -Math.PI / 8 * 3 && moveKeyAngle <= Math.PI / 8 * 3) ? 1 :
                        (moveKeyAngle <= -Math.PI / 8 * 5 || moveKeyAngle >= Math.PI / 8 * 5) ? -1 : 0;

        int strafeKey =
                (moveKeyAngle >= Math.PI / 8 && moveKeyAngle <= Math.PI / 8 * 7) ? -1 :
                        (moveKeyAngle <= -Math.PI / 8 && moveKeyAngle >= -Math.PI / 8 * 7) ? 1 : 0;

        event.setSpeed(forwardKey);
        event.setStrafe(strafeKey);

        PlayerUtils.addWaterMarkedMessageToChat( "§eInput - Forward: §f" + event.getSpeed() + " §eStrafe: §f" + event.getStrafe());

        event.setSpeed(0);
        event.setStrafe(0);

        //-67.5 to 67.5 degrees press the forward key (forward key = 1)
        //below -112.5 or above 112.5 degrees press the down key (forward key = -1)
        //22.5 to 157.5 degrees press the right key (strafe key = -1)
        //-22.5 to -157.5 degrees press the right key (strafe key = 1)
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

}

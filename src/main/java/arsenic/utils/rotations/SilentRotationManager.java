package arsenic.utils.rotations;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.main.Arsenic;
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
    public final Listener<EventTick> eventTickListener = event -> {
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

        float yawDiff = RotationUtils.getYawDifference(rotation.getYaw(), yaw); //prevyaw
        if(Math.abs(yawDiff) > speed)
            yawDiff = (speed * (yawDiff > 0 ? 1 : -1))/2f;
        float cappedPYaw = MathHelper.wrapAngleTo180_float(yaw + yawDiff);

        float pitchDiff = RotationUtils.getYawDifference(rotation.getPitch(), pitch); //prevpitch
        if(Math.abs(pitchDiff) > speed/2f)
            pitchDiff =  (speed/2f * (pitchDiff > 0 ? 1 : -1))/2f;
        float cappedPitch = MathHelper.wrapAngleTo180_float(pitch + pitchDiff);

        float[] patchedRots = RotationUtils.patchGCD(new float[]{prevYaw,prevPitch},new float[]{cappedPYaw,cappedPitch});
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
    public final Listener<EventJump> eventJumpListener = event -> {
        if(!modified || !doJumpFix)
            return;
        event.setYaw(yaw);
    };
}

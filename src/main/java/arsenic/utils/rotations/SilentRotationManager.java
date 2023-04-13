package arsenic.utils.rotations;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.main.Arsenic;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

public class SilentRotationManager {


    private final Minecraft mc = Minecraft.getMinecraft();
    private float yaw  = 0;
    private float pitch  = 0;
    private boolean modified;
    private boolean first = true;
    private float speed = 10f;

    @EventLink
    public final Listener<EventTick> eventTickListener = event -> {
        if(first) {
            yaw = mc.thePlayer.rotationYaw;
            pitch = mc.thePlayer.rotationPitch;
            first = false;
            return;
        }


        EventSilentRotation rotation = new EventSilentRotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, speed);
        Arsenic.getArsenic().getEventManager().post(rotation);
        speed = rotation.getSpeed();

        float yawDiff = RotationUtils.getYawDifference(rotation.getYaw(), yaw); //prevyaw
        if(Math.abs(yawDiff) > speed)
            yawDiff = (speed * (yawDiff > 0 ? 1 : -1))/2f;
        yaw = MathHelper.wrapAngleTo180_float(yaw + yawDiff);

        float pitchDiff = RotationUtils.getYawDifference(rotation.getPitch(), pitch); //prevpitch
        if(Math.abs(pitchDiff) > speed/2f)
            pitchDiff =  (speed/2f * (pitchDiff > 0 ? 1 : -1))/2f;
        pitch = MathHelper.wrapAngleTo180_float(pitch + pitchDiff);

        modified = rotation.hasBeenModified() || !(Math.abs(RotationUtils.getYawDifference(mc.thePlayer.rotationYaw, yaw)) > speed);
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
        event.setYaw(yaw);
        event.setPitch(pitch);
    };

    @EventLink
    public final Listener<EventMove> eventMoveListener = event -> {
        if(!modified)
            return;
        event.setYaw(yaw);
    };
}

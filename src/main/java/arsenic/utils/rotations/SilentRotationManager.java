package arsenic.utils.rotations;

import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.main.Arsenic;
import net.minecraft.client.Minecraft;

public class SilentRotationManager{

    private final Minecraft mc = Minecraft.getMinecraft();

    private float yaw  = 0;
    private float pitch  = 0;
    private boolean modified;



    @EventLink(Priorities.VERY_LOW)
    public final Listener<EventTick> eventTickListener = event -> {
        EventSilentRotation rotation = new EventSilentRotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        Arsenic.getArsenic().getEventManager().post(rotation);
        modified = rotation.hasBeenModified();
        yaw = rotation.getYaw();
        pitch = rotation.getPitch();
    };

    @EventLink
    public final Listener<EventKey> eventKeyListener = event -> {
        if(!modified)
            return;
    };

    @EventLink
    public final Listener<EventUpdate> eventUpdateListener = event -> {
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

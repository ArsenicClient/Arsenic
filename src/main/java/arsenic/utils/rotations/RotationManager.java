package arsenic.utils.rotations;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRotate;
import arsenic.event.impl.EventTick;
import arsenic.event.impl.EventUpdate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

public class RotationManager {

    //no look :eyes:

    private float yaw, prevYaw, pitch, prevPitch;
    private boolean locked; //if the rotations are the same as player rotations
    private EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;


    @EventLink
    public final Listener<EventTick> onTick = event -> {
        EventRotate e = new EventRotate(player.rotationYaw, player.rotationPitch);
    };


    @EventLink
    public final Listener<EventUpdate> onUpdate = event -> {
        if(locked) return;
        event.setYaw(yaw);
        event.setPitch(pitch);
    };
    /*

    @EventLink
    public final Listener<MoveInputEvent> onMove = event -> {
        if(locked) return;
        event.setYaw(yaw);
    };

    @EventLink
    public final Listener<LookEvent> onLook = event -> {
        if(locked) return;
        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setPrevYaw(yaw);
        event.setPrevPitch(pitch);
    };
    */
}

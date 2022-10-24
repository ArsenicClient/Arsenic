package arsenic.utils.rotations;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventLook;
import arsenic.event.impl.EventMove;
import arsenic.event.impl.EventRotate;
import arsenic.event.impl.EventTick;
import arsenic.event.impl.EventUpdate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

public class RotationManager {

    //no look :eyes:

    private float yaw, prevYaw, pitch, prevPitch;
    private float maxRotationSpeed = 10; //prob changed in a module somewhere or sent in the event + this is per tick
    private boolean locked; //if the rotations are the same as player rotations
    private EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;


    @EventLink
    public final Listener<EventTick> onTick = event -> {
        EventRotate e = new EventRotate(player.rotationYaw, player.rotationPitch);
        prevYaw = yaw;
        prevPitch = pitch;
        if(locked && !e.hasBeenTouched())
            return;

        final float yawd = RotationUtils.getYawDifference(yaw, e.getYaw());
        final int yawm = yawd > 0 ? 1 : -1; //surely there is a better way to do this
        yaw += Math.min(yawd, maxRotationSpeed) * yawm;

        final float pitchd = RotationUtils.getYawDifference(pitch, e.getPitch());
        final int pitchm = pitchd > 0 ? 1 : -1; //surely there is a better way to do this
        yaw += Math.min(pitchd, maxRotationSpeed) * pitchm;

        //checking that the yaws & pitches are valid
        yaw = ((yaw + 180) % 360) - 180;
        pitch = ((pitch + 90) % 180) - 90;

        if(!e.hasBeenTouched()) {
            if(Math.min(Math.abs(yaw), maxRotationSpeed) == yaw)
                locked = true;
        }
    };


    @EventLink
    public final Listener<EventUpdate> onUpdate = event -> {
        if(locked) return;
        event.setYaw(yaw);
        event.setPitch(pitch);
    };

    @EventLink
    public final Listener<EventMove> onMove = event -> {
        if(locked) return;
        event.setYaw(yaw);
    };

    @EventLink
    public final Listener<EventLook> onLook = event -> {
        if(locked) return;
        event.setYaw(yaw);
        event.setPitch(pitch);
    };

}

package arsenic.utils.rotations;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventLook;
import arsenic.event.impl.EventMove;
import arsenic.event.impl.EventRotate;
import arsenic.event.impl.EventTick;
import arsenic.event.impl.EventUpdate;
import arsenic.main.Arsenic;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

public class RotationManager {

    //no look :eyes:

    private float yaw, prevYaw, pitch, prevPitch;
    private float maxRotationSpeed = 0.1f; //prob changed in a module somewhere or sent in the event + this is per tick
    private boolean locked = true; //if the rotations are the same as player rotations
    private Minecraft mc = Minecraft.getMinecraft();

    public RotationManager() {
        Arsenic.getArsenic().getEventManager().subscribe(this);
    }


    @EventLink
    public final Listener<EventTick> onTick = event -> {
        EventRotate e = new EventRotate(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        Arsenic.getArsenic().getEventManager().getBus().post(e);
        prevYaw = yaw;
        prevPitch = pitch;
        if(locked && !e.hasBeenTouched())
            return;
        if(locked && e.hasBeenTouched()) {
            yaw = mc.thePlayer.rotationYaw;
            prevYaw = mc.thePlayer.prevRotationYaw;
            pitch = mc.thePlayer.rotationPitch;
            prevPitch = mc.thePlayer.prevRotationPitch;
        }


        final float yawd = -RotationUtils.getYawDifference(yaw, e.getYaw());
        final int yawm = yawd > 0 ? 1 : -1; //surely there is a better way to do this
        yaw += Math.min(Math.abs(yawd), maxRotationSpeed) * yawm;

        final float pitchd = -RotationUtils.getPitchDifference(pitch, e.getPitch());
        final int pitchm = pitchd > 0 ? 1 : -1; //surely there is a better way to do this
        pitch += Math.min(Math.abs(pitchd), maxRotationSpeed) * pitchm;



        if(!e.hasBeenTouched()) {
            locked = (Math.min(Math.abs(yawd), maxRotationSpeed * 2) == yawd && Math.min(Math.abs(pitchd), maxRotationSpeed * 2) == pitchd);
        }

        mc.thePlayer.addChatMessage(new ChatComponentText((int) e.getPitch() + "   " + (int) pitch + "   "  +(int) pitchd + "    "  + pitchm));
    };



    @EventLink
    public final Listener<EventUpdate.Pre> onUpdate = event -> {
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
        //mc.thePlayer.sendChatMessage("yaw: " + yaw);
        //mc.thePlayer.sendChatMessage("prev yaw" + prevYaw);
        event.setYaw(yaw);
        event.setPrevYaw(prevYaw);
        event.setPitch(pitch);
        event.setPrevPitch(prevPitch);
    };
}

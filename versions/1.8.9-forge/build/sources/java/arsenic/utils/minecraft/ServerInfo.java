package arsenic.utils.minecraft;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventUpdate;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0BPacketEntityAction;

/**
 * provides information about player's state(s) on server side
 */
public class ServerInfo {
    private final Minecraft mc = Minecraft.getMinecraft();
    public int onGroundTicks,offGroundTicks;
    public float yaw,pitch;
    public boolean blocking,sprinting;

    @RequiresPlayer
    @EventLink
    public final Listener<EventUpdate.Pre> preListener = pre -> {
        pitch = pre.getPitch();
        yaw = pre.getYaw();
        if (mc.thePlayer.onGround){
            onGroundTicks++;
            offGroundTicks = 0;
        } else {
            onGroundTicks = 0;
            offGroundTicks++;
        }
    };

    @EventLink
    public final Listener<EventPacket.OutGoing> outGoingListener = e -> {
        if (e.getPacket() instanceof C0BPacketEntityAction) {
            if (((C0BPacketEntityAction) e.getPacket()).getAction() == C0BPacketEntityAction.Action.START_SPRINTING){
                sprinting = true;
            }
            if (((C0BPacketEntityAction) e.getPacket()).getAction() == C0BPacketEntityAction.Action.STOP_SPRINTING){
                sprinting = false;
            }
        }
        if (e.getPacket() instanceof C08PacketPlayerBlockPlacement){
            if (((C08PacketPlayerBlockPlacement) e.getPacket()).getPlacedBlockDirection() == 255){
                if (PlayerUtils.isPlayerHoldingSword()){
                    blocking = true;
                }
            }
        }

        if (e.getPacket() instanceof C07PacketPlayerDigging){
            if (((C07PacketPlayerDigging) e.getPacket()).getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM){
                blocking = false;
            }
        }
    };
}

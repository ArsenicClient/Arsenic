package arsenic.utils.minecraft;

import arsenic.injection.accessor.InboundHandlerTuplePacketListener;
import arsenic.utils.java.UtilityClass;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;

public class PacketUtil extends UtilityClass {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void send(final Packet<?> packet) {
        mc.getNetHandler().addToSendQueue(packet);
    }
    //causes crashes due to some mixin issues
    //there has to be a better way of doing this without messing with vanilla network handler
    //todo: fix this?
   /* public static void sendNoEvent(Packet<? extends INetHandler> packetIn) {
        if (mc.getNetHandler().getNetworkManager().isChannelOpen()) {
            ((IMixinNetworkManager) mc.getNetHandler().getNetworkManager()).callFlushOutboundQueue();
            ((IMixinNetworkManager) mc.getNetHandler().getNetworkManager()).callDispatchPacket(packetIn, null);
        } else {
            ((IMixinNetworkManager) mc.getNetHandler().getNetworkManager()).getField_181680_j().writeLock().lock();

            try {
                ((IMixinNetworkManager) mc.getNetHandler().getNetworkManager()).getOutboundPacketsQueue().add(new InboundHandlerTuplePacketListener(packetIn, (GenericFutureListener[]) null));
            } finally {
                ((IMixinNetworkManager) mc.getNetHandler().getNetworkManager()).getField_181680_j().writeLock().unlock();
            }
        }
    }*/

    public static int getPlayerPing() {
        Minecraft mc = Minecraft.getMinecraft();
        NetworkPlayerInfo info = mc.thePlayer.sendQueue
                .getPlayerInfo(mc.thePlayer.getUniqueID());
        return info.getResponseTime();
    }

    public static int getPlayerPingAsTicks() {
        Minecraft mc = Minecraft.getMinecraft();
        NetworkPlayerInfo info = mc.thePlayer.sendQueue
                .getPlayerInfo(mc.thePlayer.getUniqueID());
        return (int) Math.ceil(info.getResponseTime() / 50.0);
    }
}
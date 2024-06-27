package arsenic.utils.minecraft;

import arsenic.injection.accessor.IMixinNetworkManager;
import arsenic.injection.accessor.InboundHandlerTuplePacketListener;
import arsenic.utils.java.UtilityClass;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;

public class PacketUtil extends UtilityClass {

    public static void send(final Packet<?> packet) {
        mc.getNetHandler().addToSendQueue(packet);
    }

    public static void sendNoEvent(Packet<? extends INetHandler> packetIn) {
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
    }
}
package arsenic.utils.minecraft;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.injection.accessor.InboundHandlerTuplePacketListener;
import arsenic.utils.java.UtilityClass;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.ThreadQuickExitException;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.server.S00PacketKeepAlive;

import java.util.HashMap;
import java.util.Map;

public class PacketUtil extends UtilityClass {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final Map<Integer, Long> keepAliveSentTimes = new HashMap<>();
    private static int currentPing = 0;

    public static void send(final Packet<?> packet) {
        mc.getNetHandler().addToSendQueue(packet);
    }


    @EventLink
    public Listener<EventPacket.OutGoing> outGoingListener = event -> {
        if (event.getPacket() instanceof C00PacketKeepAlive) {
            C00PacketKeepAlive packet = (C00PacketKeepAlive) event.getPacket();
            keepAliveSentTimes.put(packet.getKey(), System.currentTimeMillis());
        }
    };

    @EventLink
    public Listener<EventPacket.Incoming.Pre> incomingListener = event -> {
        if (event.getPacket() instanceof S00PacketKeepAlive) {
            S00PacketKeepAlive packet = (S00PacketKeepAlive) event.getPacket();
            int key = packet.func_149134_c(); // Gets the keep-alive ID

            Long sentTime = keepAliveSentTimes.get(key);
            if (sentTime != null) {
                currentPing = (int) (System.currentTimeMillis() - sentTime);
                keepAliveSentTimes.remove(key);

                // Clean up old entries (older than 10 seconds)
                keepAliveSentTimes.entrySet().removeIf(entry ->
                        System.currentTimeMillis() - entry.getValue() > 10000);
            }
        }
    };

    public static void receivePacket(Packet<?> packet) {
        if (packet == null)
            return;
        try {
            ((Packet<INetHandlerPlayClient>) packet).processPacket(mc.getNetHandler());
        } catch (ThreadQuickExitException ignored) {
            ignored.printStackTrace();
        }
    }


    public static int getPlayerPing() {
        return currentPing;
    }

    public static int getPlayerPingAsTicks() {
        return (int) Math.ceil(currentPing / 50.0);
    }

}
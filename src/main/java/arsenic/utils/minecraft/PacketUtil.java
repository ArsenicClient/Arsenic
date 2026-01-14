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

    private static int currentPing = 0;
    private static int averagePing = 0;

    // Rolling average tracking
    private static final int[] recentPings = new int[20]; // Last 20 pings
    private static int pingIndex = 0;
    private static int pingCount = 0;
    private static long lastPingUpdate = 0;

    public static void send(final Packet<?> packet) {
        mc.getNetHandler().addToSendQueue(packet);
    }

    /**
     * Updates ping from NetworkPlayerInfo
     * Call this regularly (e.g., every tick or in a render event)
     */
    public static void updatePing() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        try {
            NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
            if (playerInfo != null) {
                int newPing = playerInfo.getResponseTime();

                // Only update if ping changed or if it's been more than 1 second
                long currentTime = System.currentTimeMillis();
                if (newPing != currentPing || currentTime - lastPingUpdate > 1000) {
                    currentPing = newPing;
                    lastPingUpdate = currentTime;

                    // Update rolling average
                    recentPings[pingIndex] = newPing;
                    pingIndex = (pingIndex + 1) % recentPings.length;
                    if (pingCount < recentPings.length) {
                        pingCount++;
                    }

                    // Calculate average
                    int sum = 0;
                    for (int i = 0; i < pingCount; i++) {
                        sum += recentPings[i];
                    }
                    averagePing = pingCount > 0 ? sum / pingCount : 0;
                }
            }
        } catch (Exception e) {
            // Silently handle any errors
        }
    }

    public static void receivePacket(Packet<?> packet) {
        if (packet == null)
            return;
        try {
            ((Packet<INetHandlerPlayClient>) packet).processPacket(mc.getNetHandler());
        } catch (ThreadQuickExitException ignored) {
            ignored.printStackTrace();
        }
    }

    /**
     * Gets the most recent ping measurement from NetworkPlayerInfo
     * @return Current ping in milliseconds
     */
    public static int getPlayerPing() {
        updatePing();
        return currentPing;
    }

    /**
     * Gets the average ping over the last 20 measurements
     * @return Average ping in milliseconds
     */
    public static int getAveragePing() {
        return averagePing;
    }

    /**
     * Converts current ping to server ticks (50ms per tick)
     * @return Ping in ticks
     */
    public static int getPlayerPingAsTicks() {
        return (int) Math.ceil(currentPing / 50.0);
    }

    /**
     * Gets the minimum ping from recent measurements
     * @return Minimum ping in milliseconds
     */
    public static int getMinPing() {
        if (pingCount == 0) return 0;
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < pingCount; i++) {
            if (recentPings[i] < min) {
                min = recentPings[i];
            }
        }
        return min;
    }

    /**
     * Gets the maximum ping from recent measurements
     * @return Maximum ping in milliseconds
     */
    public static int getMaxPing() {
        if (pingCount == 0) return 0;
        int max = 0;
        for (int i = 0; i < pingCount; i++) {
            if (recentPings[i] > max) {
                max = recentPings[i];
            }
        }
        return max;
    }

    /**
     * Gets ping stability (difference between min and max)
     * @return Ping jitter in milliseconds
     */
    public static int getPingJitter() {
        return getMaxPing() - getMinPing();
    }

    /**
     * Resets ping tracking data
     */
    public static void resetPingTracking() {
        currentPing = 0;
        averagePing = 0;
        pingCount = 0;
        pingIndex = 0;
        lastPingUpdate = 0;
        for (int i = 0; i < recentPings.length; i++) {
            recentPings[i] = 0;
        }
    }

    /**
     * Gets a color code based on ping value
     * @param ping The ping value
     * @return Minecraft color code (e.g., "§a" for green)
     */
    public static String getPingColor(int ping) {
        if (ping < 50) return "§a"; // Green
        if (ping < 100) return "§e"; // Yellow
        if (ping < 150) return "§6"; // Gold/Orange
        return "§c"; // Red
    }

    /**
     * Gets a formatted ping string with color
     * @return Formatted ping string
     */
    public static String getFormattedPing() {
        return getPingColor(currentPing) + currentPing + "ms";
    }
}
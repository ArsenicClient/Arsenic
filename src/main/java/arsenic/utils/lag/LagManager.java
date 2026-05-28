package arsenic.utils.lag;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.module.impl.movement.InvMove;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.Packet;
import net.minecraft.network.ThreadQuickExitException;
import net.minecraft.network.play.INetHandlerPlayClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class LagManager {

    public static final Predicate<Packet<?>> ALL_PACKETS = p -> true;

    private static final Map<Class<?>, Predicate<Packet<?>>> holders = new ConcurrentHashMap<>();
    private static final List<Packet<?>> buffer = Collections.synchronizedList(new ArrayList<>());
    private static final Minecraft mc =  Minecraft.getMinecraft();
    private static int currentPing = 0;
    private static long lastPingUpdate;

    public static void updatePing() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        try {
            NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
            if (playerInfo != null) {
                int newPing = playerInfo.getResponseTime();
                long currentTime = System.currentTimeMillis();
                if(newPing < 5) {
                    //assume that the players ping cannot be < 10
                    return;
                }
                if (newPing != currentPing || currentTime - lastPingUpdate > 1000) {
                    currentPing = newPing;
                    lastPingUpdate = currentTime;
                }
            }
        } catch (Exception e) {
            // Silently handle any errors
        }
    }

    public static int getPing() {
        updatePing();
        return currentPing;
    }

    public static int getPingAsTicks() {
        updatePing();
        return currentPing/20;
    }


    @EventLink
    public final Listener<EventPacket.OutGoing> onPacket = event -> {
        if (holders.isEmpty()) return;
        Packet<?> packet = event.getPacket();
        boolean held = holders.values().stream().anyMatch(f -> f.test(packet));
        if (held) {
            buffer.add(packet);
            event.cancel();
        }
    };

    public static void acquire(Class<?> holderClass, Predicate<Packet<?>> filter) {
        holders.put(holderClass, filter);
    }

    public static void acquire(Class<?> holderClass) {
        acquire(holderClass, ALL_PACKETS);
    }

    public static void release(Class<?> holderId) {
        holders.remove(holderId);
        flushUnheld();
    }

    public static boolean isLagging() {
        return !holders.isEmpty();
    }

    public static Set<Class<?>> getHolders() {
        return Collections.unmodifiableSet(holders.keySet());
    }

    public static boolean isHolding(Class<?> holderId) {
        return LagManager.getHolders().contains(holderId);
    }

    private static void flushUnheld() {
        if (holders.isEmpty()) {
            flushAll();
            return;
        }
        synchronized (buffer) {
            Iterator<Packet<?>> it = buffer.iterator();
            while (it.hasNext()) {
                Packet<?> packet = it.next();
                boolean stillHeld = holders.values().stream().anyMatch(f -> f.test(packet));
                if (!stillHeld) {
                    sendPacket(packet);
                    it.remove();
                }
            }
        }
    }

    private static void flushAll() {
        synchronized (buffer) {
            buffer.forEach(LagManager::sendPacket);
            buffer.clear();
        }
    }

    public static void sendPacket(Packet<?> packet) {
        if (mc.getNetHandler() != null)
            mc.getNetHandler().addToSendQueue(packet);
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

}

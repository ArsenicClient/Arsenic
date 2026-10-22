package arsenic.utils.lag;

import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Round-trip time estimate built from packets we send and the server's confirmation of them:
 * block placement / break / item use -> the server's acknowledgement of that action's sequence number,
 * and keep-alive (the server's keep-alive arriving -> our reply leaving). The keep-alive needs no player action, so
 * the estimate exists even when the tab list is spoofed to zero. The tab list response time is only used until the first measurement; measured
 * samples never expire by age (the last 12 stay), since an old measurement beats a stale tab value.
 *
 * Outgoing packets are timed at the point they really leave (after LagManager had its chance to hold
 * them), incoming ones at arrival (before any delay is applied), so our own lag modules do not
 * inflate the result.
 */
public final class PingTracker {

    public enum Source { NONE, TAB_LIST, CONFIRMATION, LOCKED }

    private static final Minecraft mc = Minecraft.getInstance();

    private static final long PENDING_TTL_MS = 3_000L;
    private static final int MAX_SAMPLES = 12;
    private static final int MAX_PENDING = 64;
    private static final int MAX_RTT_MS = 5_000;
    /** Tab list values below this are treated as "not reported yet". */
    private static final int MIN_TAB_PING_MS = 5;

    private static final Object lock = new Object();
    /** Sequence numbers of block actions waiting for their acknowledgement, with when they were sent. */
    private static final Map<Integer, Long> pendingActions = new HashMap<>();
    private static final ArrayDeque<long[]> samples = new ArrayDeque<>();

    private static volatile int tabPing;
    /** Ping set with .ping lock, returned instead of any measurement; -1 when not locked. Survives world changes. */
    private static volatile int lockedPing = -1;
    // When the server's keep-alive arrived and we have not answered it yet, or -1
    private static long keepAliveArrivedAt = -1;

    private static void recordSent(int sequence) {
        synchronized (lock) {
            prune(System.currentTimeMillis());
            if (pendingActions.size() >= MAX_PENDING)
                pendingActions.clear();
            pendingActions.put(sequence, System.currentTimeMillis());
        }
    }

    private static void prune(long now) {
        for (Iterator<Long> it = pendingActions.values().iterator(); it.hasNext(); )
            if (now - it.next() > PENDING_TTL_MS)
                it.remove();
    }

    private static void addSample(long sentAt, long now) {
        long rtt = now - sentAt;
        if (rtt < 0 || rtt > MAX_RTT_MS)
            return;
        samples.addLast(new long[]{now, rtt});
        while (samples.size() > MAX_SAMPLES)
            samples.pollFirst();
    }

    /** The server acknowledges every action up to this sequence number at once. */
    private static void confirmActions(int sequence, long now) {
        Long newest = null;
        for (Iterator<Map.Entry<Integer, Long>> it = pendingActions.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> e = it.next();
            if (e.getKey() <= sequence) {
                if (e.getKey() == sequence)
                    newest = e.getValue();
                it.remove();
            }
        }
        if (newest != null)
            addSample(newest, now);
    }

    @EventLink(Priorities.VERY_LOW)
    public final Listener<EventPacket.OutGoing> onOutgoing = e -> {
        if (e.isCancelled())
            return;
        Packet<?> p = e.getPacket();

        if (p instanceof ServerboundKeepAlivePacket) {
            // The client answers each keep-alive right away, so the next reply is the one to this keep-alive. A reply
            // held by one of our own lag modules would only measure that hold, so it is skipped.
            synchronized (lock) {
                long now = System.currentTimeMillis();
                if (keepAliveArrivedAt >= 0 && !LagManager.isLagging())
                    addSample(keepAliveArrivedAt, now);
                keepAliveArrivedAt = -1;
            }
        } else if (p instanceof ServerboundUseItemOnPacket useOn) {
            recordSent(useOn.sequence());
        } else if (p instanceof ServerboundUseItemPacket use) {
            recordSent(use.sequence());
        } else if (p instanceof ServerboundPlayerActionPacket action) {
            ServerboundPlayerActionPacket.Action type = action.getAction();
            if (type == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                    || type == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK)
                recordSent(action.getSequence());
        }
    };

    @EventLink(Priorities.VERY_HIGH)
    public final Listener<EventPacket.Incoming.Pre> onIncoming = e -> {
        Packet<?> p = e.getPacket();
        long now = System.currentTimeMillis();

        if (p instanceof ClientboundBlockChangedAckPacket ack) {
            synchronized (lock) {
                confirmActions(ack.sequence(), now);
            }
        } else if (p instanceof ClientboundKeepAlivePacket) {
            synchronized (lock) {
                if (keepAliveArrivedAt < 0)
                    keepAliveArrivedAt = now;
            }
        } else if (p instanceof ClientboundLoginPacket) {
            reset();
        }
    };

    public static void reset() {
        synchronized (lock) {
            pendingActions.clear();
            samples.clear();
            keepAliveArrivedAt = -1;
        }
        tabPing = 0;
    }

    private static void refreshTabPing() {
        if (mc.player == null || mc.level == null)
            return;
        ClientPacketListener handler = mc.getConnection();
        if (handler == null)
            return;
        PlayerInfo info = handler.getPlayerInfo(mc.player.getUUID());
        if (info != null)
            tabPing = info.getLatency() >= MIN_TAB_PING_MS ? info.getLatency() : 0;
    }

    /** Median of the last confirmations, or -1 if there are none. */
    private static int measured() {
        synchronized (lock) {
            prune(System.currentTimeMillis());
            if (samples.isEmpty())
                return -1;
            long[] rtts = new long[samples.size()];
            int i = 0;
            for (long[] s : samples)
                rtts[i++] = s[1];
            Arrays.sort(rtts);
            return (int) rtts[rtts.length / 2];
        }
    }

    public static void lock(int ping) {
        lockedPing = Math.max(0, ping);
    }

    public static void unlock() {
        lockedPing = -1;
    }

    public static boolean isLocked() {
        return lockedPing >= 0;
    }

    public static int getPing() {
        int locked = lockedPing;
        if (locked >= 0)
            return locked;
        int m = measured();
        if (m >= 0)
            return m;
        refreshTabPing();
        return tabPing;
    }

    public static Source getSource() {
        if (lockedPing >= 0)
            return Source.LOCKED;
        if (measured() >= 0)
            return Source.CONFIRMATION;
        refreshTabPing();
        return tabPing > 0 ? Source.TAB_LIST : Source.NONE;
    }

    /** Spread of recent confirmations (max - min), 0 if fewer than two. */
    public static int getJitter() {
        synchronized (lock) {
            prune(System.currentTimeMillis());
            if (samples.size() < 2)
                return 0;
            long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
            for (long[] s : samples) {
                min = Math.min(min, s[1]);
                max = Math.max(max, s[1]);
            }
            return (int) (max - min);
        }
    }
}

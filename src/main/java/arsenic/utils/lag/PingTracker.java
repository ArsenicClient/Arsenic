package arsenic.utils.lag;

import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Round-trip time estimate built from packets we send and the server's confirmation of them:
 * block placement / break -> block change, window click -> confirm transaction, and keep-alive
 * (the server's keep-alive arriving -> our reply leaving). The keep-alive needs no player action, so
 * the estimate exists even when the tab list is spoofed to zero. The tab list response time is only used until the first measurement; measured
 * samples never expire by age (the last 12 stay), since an old measurement beats a stale tab value.
 *
 * Outgoing packets are timed at the point they really leave (after LagManager had its chance to hold
 * them), incoming ones at arrival (before any delay is applied), so our own lag modules do not
 * inflate the result.
 */
public final class PingTracker {

    public enum Source { NONE, TAB_LIST, CONFIRMATION }

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final long PENDING_TTL_MS = 3_000L;
    private static final int MAX_SAMPLES = 12;
    private static final int MAX_PENDING = 64;
    private static final int MAX_RTT_MS = 5_000;
    /** Tab list values below this are treated as "not reported yet". */
    private static final int MIN_TAB_PING_MS = 5;

    private static final Object lock = new Object();
    private static final Map<BlockPos, Long> pendingBlocks = new HashMap<>();
    private static final Map<Integer, Long> pendingClicks = new HashMap<>();
    private static final ArrayDeque<long[]> samples = new ArrayDeque<>();

    private static volatile int tabPing;
    // When the server's keep-alive arrived and we have not answered it yet, or -1
    private static long keepAliveArrivedAt = -1;

    private static void recordSent(Map<BlockPos, Long> map, BlockPos pos) {
        synchronized (lock) {
            prune(System.currentTimeMillis());
            if (map.size() >= MAX_PENDING)
                map.clear();
            map.put(pos, System.currentTimeMillis());
        }
    }

    private static void prune(long now) {
        for (Iterator<Long> it = pendingBlocks.values().iterator(); it.hasNext(); )
            if (now - it.next() > PENDING_TTL_MS)
                it.remove();
        for (Iterator<Long> it = pendingClicks.values().iterator(); it.hasNext(); )
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

    private static void confirmBlock(BlockPos pos, long now) {
        Long sent = pendingBlocks.remove(pos);
        if (sent != null)
            addSample(sent, now);
    }

    @EventLink(Priorities.VERY_LOW)
    public final Listener<EventPacket.OutGoing> onOutgoing = e -> {
        if (e.isCancelled())
            return;
        Packet<?> p = e.getPacket();

        if (p instanceof C00PacketKeepAlive) {
            // The client answers each keep-alive right away, so the next reply is the one to this keep-alive. A reply
            // held by one of our own lag modules would only measure that hold, so it is skipped.
            synchronized (lock) {
                long now = System.currentTimeMillis();
                if (keepAliveArrivedAt >= 0 && !LagManager.isLagging())
                    addSample(keepAliveArrivedAt, now);
                keepAliveArrivedAt = -1;
            }
        } else if (p instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement c08 = (C08PacketPlayerBlockPlacement) p;
            int dir = c08.getPlacedBlockDirection();
            if (dir < 0 || dir > 5 || c08.getPosition() == null)
                return;
            recordSent(pendingBlocks, c08.getPosition().offset(EnumFacing.getFront(dir)));
        } else if (p instanceof C07PacketPlayerDigging) {
            C07PacketPlayerDigging c07 = (C07PacketPlayerDigging) p;
            if (c07.getStatus() == C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK && c07.getPosition() != null)
                recordSent(pendingBlocks, c07.getPosition());
        } else if (p instanceof C0EPacketClickWindow) {
            C0EPacketClickWindow c0e = (C0EPacketClickWindow) p;
            synchronized (lock) {
                prune(System.currentTimeMillis());
                if (pendingClicks.size() >= MAX_PENDING)
                    pendingClicks.clear();
                pendingClicks.put(clickKey(c0e.getWindowId(), c0e.getActionNumber()), System.currentTimeMillis());
            }
        }
    };

    @EventLink(Priorities.VERY_HIGH)
    public final Listener<EventPacket.Incoming.Pre> onIncoming = e -> {
        Packet<?> p = e.getPacket();
        long now = System.currentTimeMillis();

        if (p instanceof S23PacketBlockChange) {
            synchronized (lock) {
                confirmBlock(((S23PacketBlockChange) p).getBlockPosition(), now);
            }
        } else if (p instanceof S22PacketMultiBlockChange) {
            synchronized (lock) {
                if (pendingBlocks.isEmpty())
                    return;
                for (S22PacketMultiBlockChange.BlockUpdateData d : ((S22PacketMultiBlockChange) p).getChangedBlocks())
                    confirmBlock(d.getPos(), now);
            }
        } else if (p instanceof S32PacketConfirmTransaction) {
            S32PacketConfirmTransaction s32 = (S32PacketConfirmTransaction) p;
            synchronized (lock) {
                Long sent = pendingClicks.remove(clickKey(s32.getWindowId(), s32.getActionNumber()));
                if (sent != null)
                    addSample(sent, now);
            }
        } else if (p instanceof S00PacketKeepAlive) {
            synchronized (lock) {
                if (keepAliveArrivedAt < 0)
                    keepAliveArrivedAt = now;
            }
        } else if (p instanceof S01PacketJoinGame) {
            reset();
        }
    };

    private static int clickKey(int windowId, int action) {
        return (windowId << 16) | (action & 0xFFFF);
    }

    public static void reset() {
        synchronized (lock) {
            pendingBlocks.clear();
            pendingClicks.clear();
            samples.clear();
            keepAliveArrivedAt = -1;
        }
        tabPing = 0;
    }

    private static void refreshTabPing() {
        if (mc.thePlayer == null || mc.theWorld == null)
            return;
        NetHandlerPlayClient handler = mc.getNetHandler();
        if (handler == null)
            return;
        NetworkPlayerInfo info = handler.getPlayerInfo(mc.thePlayer.getUniqueID());
        if (info != null)
            tabPing = info.getResponseTime() >= MIN_TAB_PING_MS ? info.getResponseTime() : 0;
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

    public static int getPing() {
        int m = measured();
        if (m >= 0)
            return m;
        refreshTabPing();
        return tabPing;
    }

    public static Source getSource() {
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

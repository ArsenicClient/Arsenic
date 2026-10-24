package arsenic.utils.lag;

import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

import java.util.HashMap;
import java.util.Map;

/**
 * Client side copy of Grim's timer balance (checks/impl/timer/Timer and TimerLimit in GrimAnticheat/Grim 2.0), so
 * modules know how many movement packets the server would accept right now.
 *
 * Grim adds 50 ms to a balance for every movement packet (not teleport confirms) and flags when the balance passes its
 * own clock. After every movement, the first transaction answered sets the floor: the server's send time of the latest
 * answered transaction, minus 120 ms drift. TimerLimit treats that transaction as at most 1000 ms old. So skipping
 * ticks banks at most transaction ping + 120 ms (1120 ms at most), and that bank is what lets ticks be sent faster.
 *
 * Server times are estimated from our clock: a packet leaving at t arrives at t + rtt/2, a transaction arriving at t
 * was sent at t - rtt/2. Outgoing packets are counted when they really leave (after LagManager), incoming ones at
 * arrival (before any delay), like {@link PingTracker}. Delaying incoming packets therefore raises the bank: the
 * transactions get answered later.
 */
public final class TimerBalance {

    private static final long TICK = 50_000_000L;
    /** Timer's .drift default. */
    private static final long DRIFT = 120_000_000L;
    /** TimerLimit's .ping-abuse-limit-threshold default. */
    private static final long PING_ABUSE_LIMIT = 1_000_000_000L;
    /** Grim's starting clock: real time minus the longest keep-alive wait. */
    private static final long START_CLOCK_AGE = 60_000_000_000L;
    private static final int MAX_PENDING = 256;

    private static final Object lock = new Object();
    /** Transactions waiting for our answer, by window and action number, with the server's estimated send time. */
    private static final Map<Integer, Long> pendingTransactions = new HashMap<>();

    private static long balance;
    private static long clockAtLeast;
    private static long lastMovementClock;
    private static boolean movementAfterTransaction;
    private static int pendingTeleports;
    private static int predictedFlags;

    static {
        reset();
    }

    @EventLink(Priorities.VERY_HIGH)
    public final Listener<EventPacket.Incoming.Pre> onIncoming = e -> {
        Packet<?> p = e.getPacket();
        if (p instanceof S32PacketConfirmTransaction) {
            S32PacketConfirmTransaction s32 = (S32PacketConfirmTransaction) p;
            if (s32.func_148888_e())
                return;
            synchronized (lock) {
                if (pendingTransactions.size() >= MAX_PENDING)
                    pendingTransactions.clear();
                pendingTransactions.put(key(s32.getWindowId(), s32.getActionNumber()), System.nanoTime() - halfRtt());
            }
        } else if (p instanceof S08PacketPlayerPosLook) {
            synchronized (lock) {
                pendingTeleports++;
            }
        } else if (p instanceof S01PacketJoinGame) {
            reset();
        }
    };

    @EventLink(Priorities.VERY_LOW)
    public final Listener<EventPacket.OutGoing> onOutgoing = e -> {
        if (e.isCancelled())
            return;
        Packet<?> p = e.getPacket();
        if (p instanceof C0FPacketConfirmTransaction) {
            C0FPacketConfirmTransaction c0f = (C0FPacketConfirmTransaction) p;
            synchronized (lock) {
                Long sent = pendingTransactions.remove(key(c0f.getWindowId(), c0f.getUid()));
                if (sent != null)
                    clockAtLeast = sent;
                if (movementAfterTransaction) {
                    lastMovementClock = clockAtLeast;
                    movementAfterTransaction = false;
                }
            }
        } else if (p instanceof C03PacketPlayer) {
            synchronized (lock) {
                // the answer to a teleport is not counted
                if (pendingTeleports > 0 && p instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
                    pendingTeleports--;
                    return;
                }
                movementAfterTransaction = true;
                balance += TICK;
                long now = serverNow();
                if (balance > now) {
                    predictedFlags++;
                    balance -= TICK;
                }
                balance = Math.max(balance, floor(lastMovementClock, now));
            }
        }
    };

    public static void reset() {
        synchronized (lock) {
            long now = System.nanoTime();
            pendingTransactions.clear();
            balance = 0;
            clockAtLeast = lastMovementClock = now - START_CLOCK_AGE;
            movementAfterTransaction = false;
            pendingTeleports = 0;
        }
    }

    /** How far the balance is behind the server's clock: the time movement packets can be sent ahead of real time. */
    public static double getBalanceMs() {
        synchronized (lock) {
            return (serverNow() - balance) / 1e6;
        }
    }

    /** The most the balance can fall behind right now, given the transactions answered so far. */
    public static double getMaxBalanceMs() {
        synchronized (lock) {
            long now = serverNow();
            return (now - floor(clockAtLeast, now)) / 1e6;
        }
    }

    /** Movement packets the server would accept at this moment, keeping {@code marginMs} spare. */
    public static int getSpareTicks(double marginMs) {
        return Math.max(0, (int) Math.floor((getBalanceMs() - marginMs) / 50.0));
    }

    /** Movement packets that went out while the balance was already ahead of the server's clock. */
    public static int getPredictedFlags() {
        return predictedFlags;
    }

    private static long floor(long clock, long now) {
        if (now - clock > PING_ABUSE_LIMIT)
            clock = now - PING_ABUSE_LIMIT;
        return clock - DRIFT;
    }

    private static long serverNow() {
        return System.nanoTime() + halfRtt();
    }

    private static long halfRtt() {
        return PingTracker.getPing() * 500_000L;
    }

    private static int key(int windowId, short action) {
        return (windowId << 16) | (action & 0xFFFF);
    }
}

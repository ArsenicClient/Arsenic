package arsenic.utils.lag;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventTick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.Packet;
import net.minecraft.network.ThreadQuickExitException;
import net.minecraft.network.play.INetHandlerPlayClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class LagManager {

    public static final Predicate<Packet<?>> ALL_PACKETS = p -> true;

    // ---- outgoing "hold while acquired" mechanism (indefinite, predicate-based) ----
    private static final Map<Class<?>, Predicate<Packet<?>>> holders = new ConcurrentHashMap<>();
    private static final List<Packet<?>> buffer = Collections.synchronizedList(new ArrayList<>());

    // ---- "delay by function" mechanism, one channel per direction ----
    private static final PacketDelayChannel incomingDelay = new PacketDelayChannel();
    private static final PacketDelayChannel outgoingDelay = new PacketDelayChannel();

    private static final Minecraft mc = Minecraft.getMinecraft();
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
                if (newPing < 5) {
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
        return currentPing / 50;
    }

    @EventLink
    public final Listener<EventPacket.OutGoing> onOutgoing = event -> {
        Packet<?> packet = event.getPacket();

        // a packet we delayed and are now replaying - let it through once.
        if (outgoingDelay.consumeSkip(packet))
            return;

        if (outgoingDelay.offer(packet)) {
            event.cancel();
            return;
        }

        if (holders.isEmpty())
            return;
        boolean held = holders.values().stream().anyMatch(f -> f.test(packet));
        if (held) {
            buffer.add(packet);
            event.cancel();
        }
    };

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onIncoming = event -> {
        Packet<?> packet = event.getPacket();

        if (incomingDelay.consumeSkip(packet))
            return;

        if (event.isCancelled())
            return;

        if (incomingDelay.offer(packet))
            event.cancel();
    };

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        incomingDelay.releaseFinished(LagManager::receivePacket);
        outgoingDelay.releaseFinished(LagManager::sendPacket);

        // chunked releases get one slice of packets per tick, on top of whatever
        // releaseFinished already let through naturally this tick.
        incomingDelay.processChunkedReleases(LagManager::receivePacket);
        outgoingDelay.processChunkedReleases(LagManager::sendPacket);
    };

    // ---- legacy outgoing "hold while acquired" API ----

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

    // ---- incoming delay-by-function API ----

    /**
     * Binds a delay function to an incoming packet class. Whenever a packet whose exact
     * class is {@code packetClass} is received, {@code delayFunction} is invoked with that
     * packet and should return the number of milliseconds to hold it for before it's released
     * back into the normal packet-handling pipeline. Returning {@code 0} (or {@code null})
     * lets the packet through immediately, untouched.
     * <p>
     * Pass {@code Packet.class} itself to match every incoming packet, regardless of its
     * concrete type, as a wildcard. An exact class match always takes priority over the wildcard.
     * <p>
     * Only one delay function may be bound per packet class (or the wildcard) at a time;
     * binding a new one replaces the previous.
     */
    public static void delay(Class<?> packetClass, Function<Packet<?>, Long> delayFunction) {
        incomingDelay.bind(packetClass, delayFunction);
    }

    /** Unbinds any delay function previously bound to {@code packetClass}. Does not flush pending packets. */
    public static void undelay(Class<?> packetClass) {
        incomingDelay.unbind(packetClass);
    }

    /** Immediately replays every currently-queued delayed incoming packet matching {@code filter}, skipping the rest of its delay. */
    public static void releaseDelayed(Predicate<Packet<?>> filter) {
        incomingDelay.releaseMatching(filter, LagManager::receivePacket);
    }

    /**
     * Like {@link #releaseDelayed}, but releases at most {@code chunkSize} matching packets
     * per tick instead of all of them at once, so a large queue drains gradually rather than
     * arriving in a single burst. Packets are released oldest-first. The job keeps running
     * across ticks (via {@link #onTick}) until nothing left in the queue matches {@code filter},
     * at which point it removes itself automatically. A {@code chunkSize <= 0} falls back to
     * an immediate full release.
     */
    public static void releaseDelayedChunked(Predicate<Packet<?>> filter, int chunkSize) {
        incomingDelay.releaseMatchingChunked(filter, chunkSize, LagManager::receivePacket);
    }

    /** Drops every currently-queued delayed incoming packet matching {@code filter} without replaying it. */
    public static void discardDelayed(Predicate<Packet<?>> filter) {
        incomingDelay.discard(filter);
    }

    /** Number of queued delayed incoming packets matching {@code filter}. */
    public static int countDelayed(Predicate<Packet<?>> filter) {
        return incomingDelay.count(filter);
    }

    // ---- outgoing delay-by-function API (same mechanism, opposite direction) ----

    /**
     * Binds a delay function to an outgoing packet class. Same semantics as {@link #delay},
     * but for packets the client is sending rather than receiving. Pass {@code Packet.class}
     * to match every outgoing packet as a wildcard (e.g. for a fake-lag style effect).
     */
    public static void delayOutgoing(Class<?> packetClass, Function<Packet<?>, Long> delayFunction) {
        outgoingDelay.bind(packetClass, delayFunction);
    }

    /** Unbinds any delay function previously bound to {@code packetClass} for outgoing packets. */
    public static void undelayOutgoing(Class<?> packetClass) {
        outgoingDelay.unbind(packetClass);
    }

    /** Immediately sends every currently-queued delayed outgoing packet matching {@code filter}, skipping the rest of its delay. */
    public static void releaseDelayedOutgoing(Predicate<Packet<?>> filter) {
        outgoingDelay.releaseMatching(filter, LagManager::sendPacket);
    }

    /**
     * Outgoing counterpart of {@link #releaseDelayedChunked}. Releases at most {@code chunkSize}
     * matching queued outgoing packets per tick (oldest-first) instead of sending them all in
     * one go, e.g. so a fake-lag module can trickle its held packets back out smoothly instead
     * of producing a single, easily-flagged burst. A {@code chunkSize <= 0} falls back to an
     * immediate full release via {@link #releaseDelayedOutgoing}.
     */
    public static void releaseDelayedOutgoingChunked(Predicate<Packet<?>> filter, int chunkSize) {
        outgoingDelay.releaseMatchingChunked(filter, chunkSize, LagManager::sendPacket);
    }

    /** Drops every currently-queued delayed outgoing packet matching {@code filter} without ever sending it. */
    public static void discardDelayedOutgoing(Predicate<Packet<?>> filter) {
        outgoingDelay.discard(filter);
    }

    /** Number of queued delayed outgoing packets matching {@code filter}. */
    public static int countDelayedOutgoing(Predicate<Packet<?>> filter) {
        return outgoingDelay.count(filter);
    }

    // ---- raw send/receive ----

    public static void sendPacket(Packet<?> packet) {
        if (mc.getNetHandler() != null)
            mc.getNetHandler().addToSendQueue(packet);
    }

    @SuppressWarnings("unchecked")
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
     * Self-contained holder + queue + skip-list for one direction (incoming or outgoing) of the
     * delay-by-function mechanism. Keeps {@link LagManager} from duplicating this bookkeeping
     * for both directions.
     */
    private static final class PacketDelayChannel {

        private final Map<Class<?>, Function<Packet<?>, Long>> handlers = new ConcurrentHashMap<>();
        private final Queue<TimedPacket> queue = new ConcurrentLinkedQueue<>();
        private final Set<Packet<?>> skip = Collections.newSetFromMap(new ConcurrentHashMap<>());
        private final Queue<ChunkedRelease> chunkedReleases = new ConcurrentLinkedQueue<>();

        void bind(Class<?> packetClass, Function<Packet<?>, Long> fn) {
            handlers.put(packetClass, fn);
        }

        void unbind(Class<?> packetClass) {
            handlers.remove(packetClass);
        }

        /** Call once per incoming/outgoing event for this packet, before {@link #offer}. */
        boolean consumeSkip(Packet<?> packet) {
            return skip.remove(packet);
        }

        /** Returns true if the packet was queued (and should be cancelled by the caller). */
        boolean offer(Packet<?> packet) {
            if (handlers.isEmpty())
                return false;

            Function<Packet<?>, Long> handler = handlers.get(packet.getClass());
            if (handler == null)
                handler = handlers.get(Packet.class); // wildcard fallback
            if (handler == null)
                return false;

            Long delayMillis = handler.apply(packet);
            if (delayMillis == null || delayMillis <= 0)
                return false;

            TimedPacket timedPacket = new TimedPacket(packet, delayMillis);
            timedPacket.getTimer().start();
            queue.add(timedPacket);
            return true;
        }

        void releaseFinished(Consumer<Packet<?>> releaser) {
            if (queue.isEmpty())
                return;
            Iterator<TimedPacket> it = queue.iterator();
            while (it.hasNext()) {
                TimedPacket timedPacket = it.next();
                if (timedPacket.getTimer().hasFinished()) {
                    it.remove();
                    skip.add(timedPacket.getPacket());
                    releaser.accept(timedPacket.getPacket());
                }
            }
        }

        void releaseMatching(Predicate<Packet<?>> filter, Consumer<Packet<?>> releaser) {
            Iterator<TimedPacket> it = queue.iterator();
            while (it.hasNext()) {
                TimedPacket timedPacket = it.next();
                if (filter.test(timedPacket.getPacket())) {
                    it.remove();
                    skip.add(timedPacket.getPacket());
                    releaser.accept(timedPacket.getPacket());
                }
            }
        }

        /** Queues a chunked release job; actual draining happens in {@link #processChunkedReleases}. */
        void releaseMatchingChunked(Predicate<Packet<?>> filter, int chunkSize, Consumer<Packet<?>> releaser) {
            if (chunkSize <= 0) {
                releaseMatching(filter, releaser);
                return;
            }
            chunkedReleases.add(new ChunkedRelease(filter, chunkSize));
        }

        /** Drains up to {@code chunkSize} matching packets per active job, oldest-first, once per tick. */
        void processChunkedReleases(Consumer<Packet<?>> releaser) {
            if (chunkedReleases.isEmpty())
                return;

            Iterator<ChunkedRelease> jobs = chunkedReleases.iterator();
            while (jobs.hasNext()) {
                ChunkedRelease job = jobs.next();
                int released = 0;

                Iterator<TimedPacket> it = queue.iterator();
                while (it.hasNext() && released < job.chunkSize) {
                    TimedPacket timedPacket = it.next();
                    if (job.filter.test(timedPacket.getPacket())) {
                        it.remove();
                        skip.add(timedPacket.getPacket());
                        releaser.accept(timedPacket.getPacket());
                        released++;
                    }
                }

                if (!anyMatch(job.filter))
                    jobs.remove();
            }
        }

        private boolean anyMatch(Predicate<Packet<?>> filter) {
            for (TimedPacket timedPacket : queue)
                if (filter.test(timedPacket.getPacket()))
                    return true;
            return false;
        }

        void discard(Predicate<Packet<?>> filter) {
            queue.removeIf(timedPacket -> filter.test(timedPacket.getPacket()));
        }

        int count(Predicate<Packet<?>> filter) {
            int n = 0;
            for (TimedPacket timedPacket : queue)
                if (filter.test(timedPacket.getPacket()))
                    n++;
            return n;
        }

        /** A pending chunked-release job: keep releasing up to {@code chunkSize} matching packets per tick. */
        private static final class ChunkedRelease {
            final Predicate<Packet<?>> filter;
            final int chunkSize;

            ChunkedRelease(Predicate<Packet<?>> filter, int chunkSize) {
                this.filter = filter;
                this.chunkSize = chunkSize;
            }
        }
    }

}
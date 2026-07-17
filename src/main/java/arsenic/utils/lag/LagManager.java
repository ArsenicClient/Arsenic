package arsenic.utils.lag;

import arsenic.asm.RequiresPlayer;
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

    private static final Map<Class<?>, Predicate<Packet<?>>> holders = new ConcurrentHashMap<>();
    private static final List<Packet<?>> buffer = Collections.synchronizedList(new ArrayList<>());
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

    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.OutGoing> onOutgoing = event -> {
        Packet<?> packet = event.getPacket();

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

    @RequiresPlayer
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
        incomingDelay.processChunkedReleases(LagManager::receivePacket);
        outgoingDelay.processChunkedReleases(LagManager::sendPacket);
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

    /**
     * Register an incoming-packet delay under a holder key with a custom selector.
     * Multiple holders may be interested in the same packet; when a packet arrives every
     * matching holder is polled and the largest requested delay wins (the packet is tagged
     * with that holder so it can be released independently).
     */
    public static void delay(Class<?> holderKey, Predicate<Packet<?>> selector, Function<Packet<?>, Long> delayFunction) {
        incomingDelay.bind(holderKey, selector, delayFunction);
    }

    /** Convenience: delay all packets that are instances of {@code packetClass}. */
    public static void delay(Class<?> holderKey, Class<?> packetClass, Function<Packet<?>, Long> delayFunction) {
        incomingDelay.bind(holderKey, packetClass::isInstance, delayFunction);
    }

    /** Remove a holder's incoming delay binding. Does not release packets it already holds. */
    public static void undelay(Class<?> holderKey) {
        incomingDelay.unbind(holderKey);
    }

    public static void releaseDelayed(Predicate<Packet<?>> filter) {
        incomingDelay.releaseMatching(filter, LagManager::receivePacket);
    }

    /** Release every delayed packet owned by {@code holderKey}. */
    public static void releaseDelayedFor(Class<?> holderKey) {
        incomingDelay.releaseOwned(holderKey, p -> true, LagManager::receivePacket);
    }

    /** Release delayed packets owned by {@code holderKey} that also match {@code filter}. */
    public static void releaseDelayedFor(Class<?> holderKey, Predicate<Packet<?>> filter) {
        incomingDelay.releaseOwned(holderKey, filter, LagManager::receivePacket);
    }

    public static void releaseDelayedChunked(Predicate<Packet<?>> filter, int chunkSize) {
        incomingDelay.releaseMatchingChunked(filter, chunkSize, LagManager::receivePacket);
    }

    public static void discardDelayed(Predicate<Packet<?>> filter) {
        incomingDelay.discard(filter);
    }

    public static int countDelayed(Predicate<Packet<?>> filter) {
        return incomingDelay.count(filter);
    }

    public static void delayOutgoing(Class<?> holderKey, Function<Packet<?>, Long> delayFunction) {
        outgoingDelay.bind(holderKey, holderKey::isInstance, delayFunction);
    }

    public static void delayOutgoing(Class<?> holderKey, Predicate<Packet<?>> selector, Function<Packet<?>, Long> delayFunction) {
        outgoingDelay.bind(holderKey, selector, delayFunction);
    }

    public static void undelayOutgoing(Class<?> holderKey) {
        outgoingDelay.unbind(holderKey);
    }

    public static void releaseDelayedOutgoingFor(Class<?> holderKey) {
        outgoingDelay.releaseOwned(holderKey, p -> true, LagManager::sendPacket);
    }

    public static void releaseDelayedOutgoing(Predicate<Packet<?>> filter) {
        outgoingDelay.releaseMatching(filter, LagManager::sendPacket);
    }

    public static void releaseDelayedOutgoingChunked(Predicate<Packet<?>> filter, int chunkSize) {
        outgoingDelay.releaseMatchingChunked(filter, chunkSize, LagManager::sendPacket);
    }

    public static void discardDelayedOutgoing(Predicate<Packet<?>> filter) {
        outgoingDelay.discard(filter);
    }

    public static int countDelayedOutgoing(Predicate<Packet<?>> filter) {
        return outgoingDelay.count(filter);
    }

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

    private static final class DelayRule {
        final Predicate<Packet<?>> selector;
        final Function<Packet<?>, Long> delayFn;

        DelayRule(Predicate<Packet<?>> selector, Function<Packet<?>, Long> delayFn) {
            this.selector = selector;
            this.delayFn = delayFn;
        }
    }

    private static final class PacketDelayChannel {

        // holderKey -> its delay rule. Keyed by the module that owns the delay, not the packet type.
        private final Map<Class<?>, DelayRule> handlers = new ConcurrentHashMap<>();
        private final Queue<TimedPacket> queue = new ConcurrentLinkedQueue<>();
        private final Set<Packet<?>> skip = Collections.newSetFromMap(new ConcurrentHashMap<>());
        private final Queue<ChunkedRelease> chunkedReleases = new ConcurrentLinkedQueue<>();

        void bind(Class<?> holderKey, Predicate<Packet<?>> selector, Function<Packet<?>, Long> fn) {
            handlers.put(holderKey, new DelayRule(selector, fn));
        }

        void unbind(Class<?> holderKey) {
            handlers.remove(holderKey);
        }

        boolean consumeSkip(Packet<?> packet) {
            return skip.remove(packet);
        }

        boolean offer(Packet<?> packet) {
            if (handlers.isEmpty())
                return false;

            // Poll every interested holder; the largest requested delay wins and owns the packet.
            long bestDelay = 0L;
            Class<?> winner = null;
            for (Map.Entry<Class<?>, DelayRule> entry : handlers.entrySet()) {
                DelayRule rule = entry.getValue();
                if (!rule.selector.test(packet))
                    continue;
                Long delayMillis = rule.delayFn.apply(packet);
                if (delayMillis == null || delayMillis <= 0)
                    continue;
                if (delayMillis > bestDelay) {
                    bestDelay = delayMillis;
                    winner = entry.getKey();
                }
            }

            if (winner == null)
                return false;

            TimedPacket timedPacket = new TimedPacket(packet, bestDelay, winner);
            timedPacket.getTimer().start();
            queue.add(timedPacket);
            return true;
        }

        void releaseOwned(Class<?> holderKey, Predicate<Packet<?>> filter, Consumer<Packet<?>> releaser) {
            Iterator<TimedPacket> it = queue.iterator();
            while (it.hasNext()) {
                TimedPacket timedPacket = it.next();
                if (timedPacket.isOwnedBy(holderKey) && filter.test(timedPacket.getPacket())) {
                    it.remove();
                    skip.add(timedPacket.getPacket());
                    releaser.accept(timedPacket.getPacket());
                }
            }
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

        void releaseMatchingChunked(Predicate<Packet<?>> filter, int chunkSize, Consumer<Packet<?>> releaser) {
            if (chunkSize <= 0) {
                releaseMatching(filter, releaser);
                return;
            }
            chunkedReleases.add(new ChunkedRelease(filter, chunkSize));
        }

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
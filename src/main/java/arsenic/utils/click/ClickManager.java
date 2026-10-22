package arsenic.utils.click;

import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.timer.MSTimer;

import java.util.ArrayDeque;

/**
 * Owns the click timing for both Clicker and KillAura so they behave identically: a {@link ClickPattern} per
 * client and a shared rule for when the next click is due.
 */
public final class ClickManager {

    public enum Client { CLICKER, KILLAURA }

    /** Chance a click is followed by a second one inside the same tick, as jitter and butterfly clicking do. */
    private static final double DOUBLE_CLICK_CHANCE = 0.15;
    /**
     * Hard cap on clicks in any rolling window. Server click limiters flag above 20 c/s, and packets can bunch up
     * in transit, so the window is a little longer than a second.
     */
    private static final int MAX_CLICKS_PER_WINDOW = 20;
    private static final long WINDOW_MS = 1100;

    private static final ClickManager INSTANCE = new ClickManager();

    private final ClickPattern[] patterns = new ClickPattern[Client.values().length];
    private final MSTimer[] timers = new MSTimer[Client.values().length];
    private final long[] delays = new long[Client.values().length];
    @SuppressWarnings("unchecked")
    private final ArrayDeque<Long>[] history = new ArrayDeque[Client.values().length];

    private ClickManager() {
        for (int i = 0; i < patterns.length; i++) {
            patterns[i] = new ClickPattern();
            timers[i] = new MSTimer();
            delays[i] = 100L;
            history[i] = new ArrayDeque<>();
        }
    }

    public static ClickManager get() {
        return INSTANCE;
    }

    /** True once the gap picked after the previous click has passed and another click stays under the cap. */
    public boolean isDue(Client client) {
        return timers[client.ordinal()].getTime() >= delays[client.ordinal()] && underCap(client);
    }

    /** True when the gap just picked is the second click of a double, due within this tick. */
    public boolean doubleClickPending(Client client) {
        return patterns[client.ordinal()].lastWasBurst() && underCap(client);
    }

    private boolean underCap(Client client) {
        ArrayDeque<Long> h = history[client.ordinal()];
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        while (!h.isEmpty() && h.peekFirst() <= cutoff)
            h.pollFirst();
        return h.size() < MAX_CLICKS_PER_WINDOW;
    }

    /** Milliseconds left until the next click is due. */
    public long remainingMs(Client client) {
        return Math.max(0L, delays[client.ordinal()] - timers[client.ordinal()].getTime());
    }

    /**
     * Records a click and picks the gap to the next one. A small overrun past the due time is carried over so a
     * late tick does not stretch the average rate.
     */
    public void onClick(Client client, RangeValue cps) {
        int i = client.ordinal();
        MSTimer timer = timers[i];
        long now = System.currentTimeMillis();
        history[i].addLast(now);
        long overrun = now - (timer.lastMS + delays[i]);
        timer.setTime(now - (overrun >= 0 && overrun < 50 ? overrun : 0));
        delays[i] = patterns[i].nextDelayMs(cps.getMin(), cps.getMax(), DOUBLE_CLICK_CHANCE);
    }

    /** The current median cps for a client, for display. */
    public double getMedian(Client client) {
        return patterns[client.ordinal()].getMedian();
    }

    public void reset(Client client) {
        int i = client.ordinal();
        patterns[i].reset();
        delays[i] = 100L;
        timers[i].reset();
        history[i].clear();
    }
}

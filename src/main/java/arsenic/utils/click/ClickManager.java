package arsenic.utils.click;

import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.timer.MSTimer;

/**
 * Owns the click timing for both Clicker and KillAura so they behave identically: a {@link ClickPattern} per
 * client and a shared rule for when the next click is due.
 */
public final class ClickManager {

    public enum Client { CLICKER, KILLAURA }

    private static final ClickManager INSTANCE = new ClickManager();

    private final ClickPattern[] patterns = new ClickPattern[Client.values().length];
    private final MSTimer[] timers = new MSTimer[Client.values().length];
    private final long[] delays = new long[Client.values().length];

    private ClickManager() {
        for (int i = 0; i < patterns.length; i++) {
            patterns[i] = new ClickPattern();
            timers[i] = new MSTimer();
            delays[i] = 100L;
        }
    }

    public static ClickManager get() {
        return INSTANCE;
    }

    /** True once the gap picked after the previous click has passed. */
    public boolean isDue(Client client) {
        return timers[client.ordinal()].getTime() >= delays[client.ordinal()];
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
        long overrun = now - (timer.lastMS + delays[i]);
        timer.setTime(now - (overrun >= 0 && overrun < 50 ? overrun : 0));
        delays[i] = patterns[i].nextDelayMs(cps.getMin(), cps.getMax());
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
    }
}

package arsenic.utils.click;

import java.util.Random;

/**
 * Generates the gap between clicks. Each gap is a draw from a normal distribution around a median that itself
 * drifts: it eases toward a target that is re-picked every couple of seconds, so the clicker speeds up and slows
 * down over time instead of sitting on one rhythm.
 */
public final class ClickPattern {

    private static final double MIN_RETARGET_MS = 1500;
    private static final double MAX_RETARGET_MS = 4500;
    /** Fraction of the gap to the target the median closes per second. */
    private static final double EASE_PER_SECOND = 0.8;
    private static final int MAX_RESAMPLES = 4;

    private final Random random = new Random();
    private double median;
    private double targetMedian;
    private long lastUpdate;
    private long retargetAt;
    private boolean started;

    /** Drops the drift state so the next click starts from the middle of the range again. */
    public void reset() {
        started = false;
    }

    /** The cps the current median sits at, for display. */
    public double getMedian() {
        return median;
    }

    /** Milliseconds to wait before the next click for a cps range of [minCps, maxCps]. */
    public long nextDelayMs(double minCps, double maxCps) {
        if (maxCps < minCps)
            maxCps = minCps;
        long now = System.currentTimeMillis();
        advanceMedian(now, minCps, maxCps);

        double sigma = Math.max(0.35, (maxCps - minCps) / 4.0);
        double cps = median;
        for (int i = 0; i < MAX_RESAMPLES; i++) {
            cps = median + random.nextGaussian() * sigma;
            if (cps >= minCps && cps <= maxCps)
                break;
        }
        cps = Math.max(Math.max(1.0, minCps), Math.min(maxCps, cps));
        return Math.max(1L, Math.round(1000.0 / cps));
    }

    private void advanceMedian(long now, double minCps, double maxCps) {
        if (!started) {
            started = true;
            median = (minCps + maxCps) / 2.0;
            targetMedian = pickTarget(minCps, maxCps);
            lastUpdate = now;
            retargetAt = now + nextRetargetGap();
            return;
        }
        if (now >= retargetAt) {
            targetMedian = pickTarget(minCps, maxCps);
            retargetAt = now + nextRetargetGap();
        }
        double dt = Math.min(1.0, (now - lastUpdate) / 1000.0);
        lastUpdate = now;
        median += (targetMedian - median) * Math.min(1.0, EASE_PER_SECOND * dt);
        median = Math.max(minCps, Math.min(maxCps, median));
    }

    /** A target drawn from a normal centred on the range, so extremes are visited less often than the middle. */
    private double pickTarget(double minCps, double maxCps) {
        double mid = (minCps + maxCps) / 2.0;
        double half = (maxCps - minCps) / 2.0;
        double t = mid + random.nextGaussian() * half / 2.0;
        return Math.max(minCps, Math.min(maxCps, t));
    }

    private long nextRetargetGap() {
        return (long) (MIN_RETARGET_MS + random.nextDouble() * (MAX_RETARGET_MS - MIN_RETARGET_MS));
    }
}

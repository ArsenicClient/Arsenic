package arsenic.utils.timer;

import java.util.function.Supplier;

public class AnimationTimer {

    int maxMs;
    long lastTick;
    long ticksLived;
    private final TickMode tickMode;
    private final Supplier<Boolean> func;

    public AnimationTimer(int maxMs, Supplier<Boolean> func) {
        this(maxMs, func, TickMode.SINE);
    }

    public AnimationTimer(int maxMs, Supplier<Boolean> func, TickMode tickMode) {
        this.maxMs = maxMs;
        this.func = func;
        this.tickMode = tickMode;
    }

    public float getPercent() {
        long tickDifference = lastTick - System.currentTimeMillis();
        lastTick = System.currentTimeMillis();
        ticksLived = Math.max(0, Math.min(maxMs, ticksLived + (tickDifference * (func.get() ? -1 : 1))));
        return tickMode.toSmoothPercent((float) ticksLived / maxMs);
    }

    public void setElapsedMs(int ms) {
        lastTick = System.currentTimeMillis();
        ticksLived = ms;
    }

    /**
     * Retunes how long this animation takes, keeping whatever progress it has already made.
     * <p>
     * Elapsed time is rescaled rather than left alone, so changing the duration mid-flight does not
     * teleport the animation: an element that is 40% open stays 40% open and simply covers the rest
     * at the new rate. That is what lets a panel whose content height is only known at draw time
     * pick a duration proportional to how far it actually has to travel, instead of every panel
     * sharing one duration and the tall ones appearing to whip open.
     */
    public void setMaxMs(int maxMs) {
        maxMs = Math.max(1, maxMs);
        if (maxMs == this.maxMs)
            return;
        ticksLived = Math.round(ticksLived * (maxMs / (double) this.maxMs));
        this.maxMs = maxMs;
        ticksLived = Math.max(0, Math.min(maxMs, ticksLived));
    }
}

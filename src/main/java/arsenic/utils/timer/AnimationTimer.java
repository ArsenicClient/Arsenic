package arsenic.utils.timer;

public class AnimationTimer {

    int maxMs;
    long lastTick;
    long ticksLived;
    private TickMode tickMode;

    public AnimationTimer(int maxMs) {
        this.tickMode = TickMode.LINEAR;
        this.maxMs = maxMs;
    }

    public AnimationTimer(int maxMs, TickMode tickMode) {
        this.tickMode = tickMode;
        this.maxMs = maxMs;
    }

    public float getPercent(boolean b) {
        long tickDifference = lastTick - System.currentTimeMillis();
        lastTick = System.currentTimeMillis();
        ticksLived = Math.max(0, Math.min(maxMs, ticksLived + (tickDifference * (b ? -1 : 1))));
        return tickMode.toSmoothPercent((float) ticksLived/maxMs);
    }
}

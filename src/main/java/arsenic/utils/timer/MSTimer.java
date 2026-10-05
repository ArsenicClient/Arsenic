package arsenic.utils.timer;

public class MSTimer {

    public long lastMS = System.currentTimeMillis();

    /** A timer that already counts as long elapsed, for "last happened at" fields that start unset. */
    public static MSTimer expired() {
        MSTimer t = new MSTimer();
        t.lastMS = 0;
        return t;
    }

    public void reset() {
        lastMS = System.currentTimeMillis();
    }

    public boolean hasTimeElapsed(long time, boolean reset) {
        if (System.currentTimeMillis() - lastMS > time) {
            if (reset) reset();
            return true;
        }

        return false;
    }

    public boolean finished(final long delay) {
        return System.currentTimeMillis() - delay >= lastMS;
    }

    public boolean hasTimeElapsed(long time) {
        return System.currentTimeMillis() - lastMS > time;
    }

    public boolean hasTimeElapsed(double time) {
        return hasTimeElapsed((long) time);
    }

    public long getTime() {
        return System.currentTimeMillis() - lastMS;
    }

    public void setTime(long time) {
        lastMS = time;
    }
}
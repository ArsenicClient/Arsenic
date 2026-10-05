package arsenic.utils.timer;

public final class TimeUtils {

    private TimeUtils() {}

    /** True for the first half of every period and false for the second: a text cursor blink. */
    public static boolean blink(long halfPeriodMs) {
        return (System.currentTimeMillis() / halfPeriodMs) % 2 == 0;
    }
}

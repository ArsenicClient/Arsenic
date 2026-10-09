package arsenic.utils.keystrokes;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;

/**
 * Presses Arsenic makes for the player: clicks, sprint resets, forced jumps. The keyboard and mouse never show these,
 * so the Keystrokes HUD reads them from here as well.
 */
public final class SyntheticKeys {

    public enum Key { LMB, JUMP, SPRINT }

    private static final long FLASH_MS = 120L;
    private static final long HISTORY_MS = 1000L;
    private static final int MAX_HISTORY = 64;

    private static final Map<Key, ArrayDeque<Long>> presses = new EnumMap<>(Key.class);

    static {
        for (Key key : Key.values())
            presses.put(key, new ArrayDeque<>());
    }

    private SyntheticKeys() {}

    public static synchronized void press(Key key) {
        long now = System.currentTimeMillis();
        ArrayDeque<Long> history = presses.get(key);
        history.addLast(now);
        while (history.size() > MAX_HISTORY || now - history.peekFirst() > HISTORY_MS)
            history.pollFirst();
    }

    /** True for a moment after the last press, so a quick sprint reset or jump still shows. */
    public static synchronized boolean flashing(Key key) {
        ArrayDeque<Long> history = presses.get(key);
        return !history.isEmpty() && System.currentTimeMillis() - history.peekLast() <= FLASH_MS;
    }

    /** Presses of this key in the last second. */
    public static synchronized int countLastSecond(Key key) {
        long cutoff = System.currentTimeMillis() - HISTORY_MS;
        int count = 0;
        for (long time : presses.get(key))
            if (time >= cutoff)
                count++;
        return count;
    }
}

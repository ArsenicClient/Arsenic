package arsenic.gui.design;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Gives the cursor to exactly one thing per frame.
 * <p>
 * The GUI draws immediate-mode, so a widget can't know while drawing whether something drawn later
 * will cover it. Each widget calls {@link #test} with a stable key and its rect; the last rect drawn
 * under the cursor (the topmost) becomes the owner, and from the next frame on only that key gets
 * {@code true}. The one-frame lag is invisible and removes overlapping highlights.
 * <p>
 * {@link #pushClip} limits hits to a scissored area so scrolled-away rows can't take the cursor.
 */
public final class Hover {

    private static float mouseX = -1, mouseY = -1;
    private static Object owner, candidate;
    private static boolean suppressed;
    private static final Deque<float[]> clips = new ArrayDeque<>();

    private Hover() {}

    /** Call once at the start of each GUI frame, before anything is drawn. */
    public static void beginFrame(float mx, float my) {
        owner = candidate;
        candidate = null;
        mouseX = mx;
        mouseY = my;
        clips.clear();
        suppressed = false;
    }

    /** Drops the owner at once, e.g. when the screen closes or a page switches. */
    public static void reset() {
        owner = candidate = null;
    }

    /** While true (a drag is in progress) nothing new can take the cursor. */
    public static void suppress(boolean on) {
        suppressed = on;
    }

    public static boolean isSuppressed() {
        return suppressed;
    }

    /**
     * Registers {@code key} at this rect and returns whether it owns the cursor this frame.
     * Keys must be stable between frames (compared with equals): the component itself, a constant, an enum.
     */
    public static boolean test(Object key, float x1, float y1, float x2, float y2) {
        if (key == null)
            return false;
        if (!suppressed && inside(x1, y1, x2, y2))
            candidate = key;
        return key.equals(owner);
    }

    /**
     * For immediate-mode rows with no object of their own: the rect itself is the key.
     * A row that moves (scrolling) is re-keyed, so it may miss one frame of hover.
     */
    public static boolean testRect(float x1, float y1, float x2, float y2) {
        long key = ((long) Math.round(x1 * 2) & 0xFFFF) << 48 | ((long) Math.round(y1 * 2) & 0xFFFF) << 32
                | ((long) Math.round(x2 * 2) & 0xFFFF) << 16 | ((long) Math.round(y2 * 2) & 0xFFFF);
        return test(key, x1, y1, x2, y2);
    }

    /** True if the cursor is in the rect and the current clip, ignoring ownership. */
    public static boolean inside(float x1, float y1, float x2, float y2) {
        if (mouseX < x1 || mouseX > x2 || mouseY < y1 || mouseY > y2)
            return false;
        float[] clip = clips.peek();
        return clip == null || (mouseX >= clip[0] && mouseX <= clip[2] && mouseY >= clip[1] && mouseY <= clip[3]);
    }

    public static boolean owns(Object key) {
        return key != null && key.equals(owner);
    }

    public static void pushClip(float x1, float y1, float x2, float y2) {
        float[] parent = clips.peek();
        if (parent != null) {
            x1 = Math.max(x1, parent[0]);
            y1 = Math.max(y1, parent[1]);
            x2 = Math.min(x2, parent[2]);
            y2 = Math.min(y2, parent[3]);
        }
        clips.push(new float[]{x1, y1, x2, y2});
    }

    public static void popClip() {
        if (!clips.isEmpty())
            clips.pop();
    }
}

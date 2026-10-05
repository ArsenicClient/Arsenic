package arsenic.utils.java;

public class MathUtils extends UtilityClass {

    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    public static float clamp01(float v) {
        return clamp(v, 0f, 1f);
    }

    public static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /** Point in rectangle, inclusive on every edge. */
    public static boolean inside(double px, double py, double x1, double y1, double x2, double y2) {
        return px >= x1 && px <= x2 && py >= y1 && py <= y2;
    }

    /** Point in a rectangle given as position and size, exclusive on the far edges (vanilla GuiButton hit test). */
    public static boolean insideSized(double px, double py, double x, double y, double w, double h) {
        return px >= x && py >= y && px < x + w && py < y + h;
    }
}

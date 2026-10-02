package arsenic.utils.render;

import arsenic.utils.java.UtilityClass;

/**
 * Nested clipping for GUI drawing. The GUI render state keeps its own scissor stack (and already
 * intersects nested regions), so this is a thin wrapper that keeps the old call shape.
 */
public class ScissorUtils extends UtilityClass {

    private static int depth;

    /** Clips to the rectangle (x1, y1) - (x2, y2), intersected with any enclosing clip. */
    public static void subScissor(int x1, int y1, int x2, int y2) {
        RenderContext.graphics().enableScissor(x1, y1, Math.max(x1, x2), Math.max(y1, y2));
        depth++;
    }

    /** {@code scale} used to be the GUI scale factor; scissoring is in GUI units now. */
    public static void subScissor(int x1, int y1, int x2, int y2, int scale) {
        subScissor(x1, y1, x2, y2);
    }

    public static void endSubScissor() {
        if (depth <= 0)
            return;
        depth--;
        RenderContext.graphics().disableScissor();
    }

    /** Drops any clips left open, e.g. if a component threw halfway through drawing. */
    public static void resetScissor() {
        while (depth > 0 && RenderContext.isActive())
            endSubScissor();
        depth = 0;
    }
}

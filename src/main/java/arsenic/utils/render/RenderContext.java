package arsenic.utils.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The GUI draw target for the frame currently being built.
 * <p>
 * 1.8 drew through global GL state, so any code could draw from anywhere. Modern Minecraft records
 * GUI draws into a {@link GuiGraphicsExtractor} that only exists while a screen or HUD element is
 * extracting its render state. Rather than threading that object through every draw helper and
 * every component's signature, whoever owns a frame opens a context with {@link #begin} and the
 * static helpers ({@link DrawUtils}, the fonts, {@link ScissorUtils}) draw into it.
 */
public final class RenderContext implements AutoCloseable {

    private static final Deque<GuiGraphicsExtractor> STACK = new ArrayDeque<>();
    private static final RenderContext HANDLE = new RenderContext();

    /**
     * Opacity multiplier for everything the client draws through its own helpers. Used for whole
     * screen fades - 1.8 did those by rendering into a framebuffer and compositing it.
     */
    private static float alpha = 1f;

    private RenderContext() {
    }

    public static RenderContext begin(GuiGraphicsExtractor graphics) {
        STACK.push(graphics);
        return HANDLE;
    }

    public static boolean isActive() {
        return !STACK.isEmpty();
    }

    public static GuiGraphicsExtractor graphics() {
        GuiGraphicsExtractor graphics = STACK.peek();
        if (graphics == null)
            throw new IllegalStateException("Drawing outside of a render pass - wrap the caller in RenderContext.begin(graphics)");
        return graphics;
    }

    public static float getAlpha() {
        return alpha;
    }

    public static void setAlpha(float value) {
        alpha = Math.max(0f, Math.min(1f, value));
    }

    /** Scales a colour's alpha byte by the current opacity. */
    public static int applyAlpha(int color) {
        if (alpha >= 1f)
            return color;
        int a = (int) (((color >>> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    @Override
    public void close() {
        STACK.pop();
    }
}

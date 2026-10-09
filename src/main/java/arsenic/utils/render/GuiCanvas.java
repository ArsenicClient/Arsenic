package arsenic.utils.render;

import net.minecraft.client.Minecraft;

/**
 * A fixed design canvas, so a screen lays out identically on every monitor.
 *
 * The canvas is the 1980x1080 layout at GUI scale 2 (990x540 units). It is scaled uniformly to fit the window and
 * centred, so a screen with a different shape gets bars rather than a stretched or reflowed layout.
 *
 * Screens that opt in draw in canvas units. While {@link #isActive()} is set, scissor rectangles are converted through
 * this class too, so callers do not pass a scale of their own.
 */
public final class GuiCanvas {

    public static final float WIDTH = 990f;
    public static final float HEIGHT = 540f;

    private static boolean active;

    private GuiCanvas() {}

    public static void setActive(boolean on) {
        active = on;
    }

    public static boolean isActive() {
        return active;
    }

    /** Pixels per canvas unit. */
    public static float scale() {
        Minecraft mc = Minecraft.getMinecraft();
        return Math.min(mc.displayWidth / WIDTH, mc.displayHeight / HEIGHT);
    }

    /** Left edge of the canvas in window pixels. Bars on either side are equal, so this is half the spare width. */
    public static float offsetX() {
        Minecraft mc = Minecraft.getMinecraft();
        return (mc.displayWidth - WIDTH * scale()) / 2f;
    }

    /** Top edge of the canvas in window pixels, counted from the top. */
    public static float offsetY() {
        Minecraft mc = Minecraft.getMinecraft();
        return (mc.displayHeight - HEIGHT * scale()) / 2f;
    }

    public static float pixelX(float x) {
        return x * scale() + offsetX();
    }

    public static float pixelY(float y) {
        return y * scale() + offsetY();
    }

    /** Canvas x for a mouse position given as a left-origin window pixel. */
    public static float unitX(float pixelX) {
        return (pixelX - offsetX()) / scale();
    }

    /** Canvas y for a mouse position given as a top-origin window pixel. */
    public static float unitY(float pixelY) {
        return (pixelY - offsetY()) / scale();
    }
}

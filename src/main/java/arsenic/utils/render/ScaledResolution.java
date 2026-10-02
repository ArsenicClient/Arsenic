package arsenic.utils.render;

import net.minecraft.client.Minecraft;

/**
 * The GUI-scaled screen size, under the name 1.8 code knows it by. Modern Minecraft keeps these
 * on the window; this just reads them back out.
 */
public final class ScaledResolution {

    private final int width, height, scaleFactor;

    public ScaledResolution(Minecraft mc) {
        this.width = mc.getWindow().getGuiScaledWidth();
        this.height = mc.getWindow().getGuiScaledHeight();
        this.scaleFactor = mc.getWindow().getGuiScale();
    }

    public int getScaledWidth() {
        return width;
    }

    public int getScaledHeight() {
        return height;
    }

    public int getScaleFactor() {
        return scaleFactor;
    }
}

package arsenic.utils.java;

import arsenic.gui.design.M3;
import arsenic.gui.themes.Theme;
import arsenic.main.Arsenic;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.timer.TickMode;

import java.awt.*;

public class ColorUtils extends UtilityClass {

    public static int setColor(int value, int i, int newValue) {
        if(newValue > 0xFF)
            newValue = 0xFF;
        else if (newValue < 0)
            newValue = 0;
        int a = 24 - (i*8);
        return ((value & ~(0xFF << a)) | (newValue << a));
    }

    public static int withAlpha(int rgb, int alpha) {
        return (MathUtils.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Keeps the colour and scales its alpha to 0..1 of full. */
    public static int withAlpha(int rgb, float alpha) {
        return withAlpha(rgb, (int) (255 * MathUtils.clamp01(alpha)));
    }

    /** Blends the rgb channels only; the result has no alpha. */
    public static int intMixRgb(int a, int b, float t) {
        return mixRgb(a, b, t);
    }

    public static int mixRgb(int a, int b, float t) {
        int r = (int) ((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
        int g = (int) ((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return r << 16 | g << 8 | bl;
    }

    /** Blends two argb colours channel by channel, alpha included. */
    public static int mixArgb(int a, int b, float t) {
        int out = 0;
        for (int s = 0; s <= 24; s += 8)
            out |= ((int) ((a >>> s & 0xFF) + ((b >>> s & 0xFF) - (a >>> s & 0xFF)) * t) & 0xFF) << s;
        return out;
    }

    public static float luminance(int rgb) {
        return (0.299f * (rgb >> 16 & 0xFF) + 0.587f * (rgb >> 8 & 0xFF) + 0.114f * (rgb & 0xFF)) / 255f;
    }

    public static int getColor(int value, int i) {
        return (value >> (8 * (3 - i))) & 0xFF;
    }

    public static int getThemeRainbowColor(long speed, long delay) {
        long period = Math.max(1L, speed * 1000L);
        float percent = Math.floorMod(System.currentTimeMillis() - delay, period) / (float) period;
        percent = TickMode.SINE.toSmoothPercent(2f * percent);
        Theme theme = Arsenic.getArsenic().getThemeManager().getCurrentTheme();
        int secondary = theme.isGradientEnabled() ? theme.getGradientColor() : theme.getWhite();
        return RenderUtils.interpolateColoursInt(theme.getMainColor(), secondary, percent);
    }

    public static int getRainbow(float speed, long delay) {
        long period = Math.max(1L, (long) (speed * 1000f));
        float hue = Math.floorMod(System.currentTimeMillis() - delay, period) / (float) period;
        return Color.HSBtoRGB(hue, 0.6f, 0.88f);
    }

}

package arsenic.gui.click;

import arsenic.utils.java.MathUtils;
import arsenic.gui.themes.ThemeManager;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import org.lwjgl.opengl.GL11;

public final class UITheme {

    private UITheme() {}


    public static float unit(float guiHeight) {
        return guiHeight / 100f;
    }

    public static float space(float guiHeight, float steps) {
        return unit(guiHeight) * steps;
    }

    public static float radiusPill(float height) { return height / 2f; }
    public static float radiusCard(float height) { return Math.max(3f, height * 0.28f); }
    public static float radiusChip(float height) { return Math.max(2f, height * 0.34f); }

    public static final int DUR_PRESS  = 90;
    public static final int DUR_HOVER  = 160;
    public static final int DUR_TOGGLE = 260;
    public static final int DUR_EXPAND = 320;

    public static int expandDuration(float contentHeight) {
        return (int) MathUtils.clamp(110 + Math.abs(contentHeight) * 1.15f, 150, 420);
    }


    public static int alpha(int argb, int a) {
        return ColorUtils.setColor(argb, 0, MathUtils.clamp(a, 0, 255));
    }

    public static int alpha(int argb, float pct) {
        return alpha(argb, (int) (255 * MathUtils.clamp01(pct)));
    }

    public static int fade(int argb, float pct) {
        int a = (argb >>> 24) & 0xFF;
        return alpha(argb, (int) (a * MathUtils.clamp01(pct)));
    }

    public static int accent() { return ThemeManager.getMainColor(); }
    public static int accentAlt() { return ThemeManager.getGradientColor(); }
    public static int textPrimary() { return ThemeManager.getTextPrimary(); }
    public static int textSecondary() { return ThemeManager.getTextSecondary(); }
    public static int textMuted() { return ThemeManager.getTextMuted(); }

    public static float luminance(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    public static int readableOn(int background) {
        return luminance(background) > 0.55f ? 0xFF15161A : 0xFFF5F6F8;
    }

    public static int mix(int from, int to, float pct) {
        return RenderUtils.interpolateColoursInt(from, to, MathUtils.clamp01(pct));
    }


    public enum Elevation {
        FLAT(0f, 0, 0),
        RAISED(0.45f, 130, 20),
        FLOATING(0.9f, 180, 30);

        private final float spread;
        private final int shadow;
        private final int edge;

        Elevation(float spread, int shadow, int edge) {
            this.spread = spread;
            this.shadow = shadow;
            this.edge = edge;
        }
    }

    public static void surface(float x1, float y1, float x2, float y2, float radius,
                               int fill, Elevation elevation, float intensity) {
        if (x2 <= x1 || y2 <= y1)
            return;

        intensity = MathUtils.clamp01(intensity);
        float h = y2 - y1;

        GL11.glEnable(GL11.GL_BLEND);

        if (elevation != Elevation.FLAT && intensity > 0.01f) {
            DrawUtils.drawShadow(x1, y1, x2, y2, radius,
                    GuiStyle.shadowSpread(h * elevation.spread),
                    GuiStyle.shadowAlpha((int) (elevation.shadow * intensity)), 6);
        }

        DrawUtils.drawRoundedRect(x1, y1, x2, y2, radius, GuiStyle.glassify(fill));

        if (GuiStyle.glassEnabled())
            DrawUtils.drawGlassRect(x1, y1, x2, y2, radius,
                    alpha(accent(), 14), ThemeManager.getWhite(),
                    GuiStyle.glassStrength() * (0.6f + 0.4f * intensity));

        if (elevation != Elevation.FLAT && intensity > 0.01f)
            DrawUtils.drawEdgeHighlight(x1, y1, x2, y2, radius, accent(),
                    GuiStyle.edgeAlpha((int) (elevation.edge * intensity)));
    }

    public static void surface(float x1, float y1, float x2, float y2, float radius, int fill, Elevation elevation) {
        surface(x1, y1, x2, y2, radius, fill, elevation, 1f);
    }

    public static void hoverWash(float x1, float y1, float x2, float y2, float radius, float pct) {
        if (pct <= 0.01f)
            return;
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, radius,
                alpha(ThemeManager.getModuleHover(), (int) (26 * pct)));
    }

    public static void accentBar(float x1, float y1, float x2, float y2, float radius, float pct) {
        if (pct <= 0.01f)
            return;
        DrawUtils.drawGradientRoundedRect(x1, y1, x2, y2, radius,
                alpha(accent(), (int) (220 * pct)), alpha(accent(), (int) (220 * pct)),
                alpha(accentAlt(), (int) (220 * pct)), alpha(accentAlt(), (int) (220 * pct)));
    }

    public static void divider(float x1, float y, float x2, float pct) {
        if (pct <= 0.01f)
            return;
        DrawUtils.drawRect(x1, y, x2, y + 0.75f, fade(ThemeManager.getSeparator(), pct));
    }

    public static void chevron(float cx, float cy, float size, float thickness, int color, float openPct) {
        float p = MathUtils.clamp01(openPct);
        float h = size / 2f;

        float ax = MathUtils.lerp(-h / 2f, -h, p),  ay = MathUtils.lerp(-h, -h / 2f, p);
        float bx = MathUtils.lerp(h / 2f, 0f, p),   by = MathUtils.lerp(0f, h / 2f, p);
        float ccx = MathUtils.lerp(-h / 2f, h, p),  ccy = MathUtils.lerp(h, -h / 2f, p);

        line(cx + ax, cy + ay, cx + bx, cy + by, thickness, color);
        line(cx + bx, cy + by, cx + ccx, cy + ccy, thickness, color);
    }

    public static void line(float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.0001f)
            return;
        float nx = -dy / len * (thickness / 2f);
        float ny = dx / len * (thickness / 2f);

        final float ax = (x1 + nx) * 2, ay = (y1 + ny) * 2;
        final float bx = (x2 + nx) * 2, by = (y2 + ny) * 2;
        final float cx = (x2 - nx) * 2, cy = (y2 - ny) * 2;
        final float dx2 = (x1 - nx) * 2, dy2 = (y1 - ny) * 2;

        DrawUtils.drawCustom(color, () -> {
            GL11.glVertex2d(ax, ay);
            GL11.glVertex2d(bx, by);
            GL11.glVertex2d(cx, cy);
            GL11.glVertex2d(dx2, dy2);
        });
    }

    public static void check(float cx, float cy, float size, float thickness, int color) {
        float h = size / 2f;
        line(cx - h, cy, cx - h * 0.15f, cy + h * 0.75f, thickness, color);
        line(cx - h * 0.15f, cy + h * 0.75f, cx + h, cy - h * 0.7f, thickness, color);
    }

    public static void scrollbar(float x, float y, float trackHeight, float contentOverflow, float scroll, float pct) {
        if (contentOverflow <= 0 || pct <= 0.01f)
            return;
        float thumbHeight = Math.max(trackHeight * 0.08f,
                trackHeight * (trackHeight / (trackHeight + contentOverflow)));
        float thumbY = y + (scroll / -contentOverflow) * (trackHeight - thumbHeight);
        float w = 2.5f;
        DrawUtils.drawRoundedRect(x - w, y, x, y + trackHeight, w / 2f,
                fade(ThemeManager.getScrollbarTrack(), pct * 0.7f));
        DrawUtils.drawGradientRoundedRect(x - w, thumbY, x, thumbY + thumbHeight, w / 2f,
                alpha(accent(), pct), alpha(accent(), pct),
                alpha(accentAlt(), pct), alpha(accentAlt(), pct));
    }

    public static float chip(arsenic.utils.font.FontRendererExtension<?> fr, String text,
                             float rightX, float centreY, float height, int textColor, int fillColor) {
        float padding = height * 0.42f;
        float w = fr.getWidth(text) + padding * 2f;
        float x1 = rightX - w;
        float y1 = centreY - height / 2f;
        float y2 = centreY + height / 2f;
        DrawUtils.drawRoundedRect(x1, y1, rightX, y2, radiusChip(height), fillColor);
        fr.drawString(text, x1 + padding, centreY, textColor, fr.CENTREY);
        return w;
    }
}

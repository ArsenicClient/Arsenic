package arsenic.gui.hud;

import arsenic.gui.click.GuiStyle;
import arsenic.gui.design.M3;
import arsenic.gui.design.M3Icons;
import arsenic.main.Arsenic;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.font.VanillaFontRenderer;
import arsenic.utils.java.MathUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

/**
 * Shared Material Design 3 + glass/tonal building blocks for all HUD elements and addons.
 * <p>
 * Every helper snaps box coordinates to the active device pixel grid ({@link #snap(float)}) and
 * centers text and icons around an explicit vertical midpoint {@code cy = y + h / 2f} so that top
 * and bottom margins are identical across all Minecraft GUI scales (1, 2, 3, 4).
 */
public final class HudStyle {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public static final float RADIUS_CARD = 9f;
    public static final float RADIUS_COMPACT = 7f;
    public static final float RADIUS_PILL = 6f;

    private HudStyle() {}

    public static float scaleFactor() {
        if (DrawUtils.overrideScaleFactor > 0f)
            return DrawUtils.overrideScaleFactor;
        return new ScaledResolution(mc).getScaleFactor();
    }

    /** Snaps a GUI-unit coordinate to the nearest physical screen pixel at the current GUI scale. */
    public static float snap(float v) {
        float sf = Math.max(1f, scaleFactor());
        return Math.round(v * sf) / sf;
    }

    public static FontRendererExtension<?> body() {
        try {
            FontRendererExtension<?> fr = GuiStyle.fontEnabled()
                    ? Arsenic.getInstance().getFonts().Inter.getFontRendererExtension()
                    : VanillaFontRenderer.of(mc.fontRendererObj).getFontRendererExtension();
            fr.resetScale();
            return fr;
        } catch (Throwable t) {
            return VanillaFontRenderer.of(mc.fontRendererObj).getFontRendererExtension();
        }
    }

    public static FontRendererExtension<?> label() {
        try {
            FontRendererExtension<?> fr = GuiStyle.fontEnabled()
                    ? M3.label()
                    : VanillaFontRenderer.of(mc.fontRendererObj).getFontRendererExtension();
            fr.resetScale();
            return fr;
        } catch (Throwable t) {
            return body();
        }
    }

    public static FontRendererExtension<?> title() {
        try {
            FontRendererExtension<?> fr = GuiStyle.fontEnabled()
                    ? M3.title()
                    : VanillaFontRenderer.of(mc.fontRendererObj).getFontRendererExtension();
            fr.resetScale();
            return fr;
        } catch (Throwable t) {
            return label();
        }
    }

    /** Standard M3 HUD card with ambient shadow, tonal surface, optional liquid-glass film, and hairline outline. */
    public static void card(float x1, float y1, float x2, float y2, float radius) {
        card(x1, y1, x2, y2, radius, 228);
    }

    /** Standard M3 HUD card with configurable surface alpha (0..255). */
    public static void card(float x1, float y1, float x2, float y2, float radius, int alpha) {
        x1 = snap(x1);
        y1 = snap(y1);
        x2 = snap(x2);
        y2 = snap(y2);
        int a = MathUtils.clamp(alpha, 0, 255);
        if (a <= 0)
            return;
        float r = Math.min(radius, Math.min(x2 - x1, y2 - y1) / 2f);
        int shadowAlpha = (int) (GuiStyle.shadowAlpha(95) * (a / 255f));
        if (shadowAlpha > 0 && a > 40) {
            DrawUtils.drawShadow(x1, y1, x2, y2, r, 6f, shadowAlpha);
        }
        int baseTone = M3.mix(M3.surfaceContainerLow(), M3.surfaceContainer(), 0.45f);
        int fill = M3.alpha(baseTone, a);
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, r, fill);
        float glass = GuiStyle.glassStrength() * 0.55f * (a / 255f);
        if (glass > 0.005f) {
            int rim = M3.mix(M3.outlineVariant(), M3.primary(), 0.28f);
            DrawUtils.drawGlassRect(x1, y1, x2, y2, r, fill, rim, glass);
        }
        int outlineAlpha = Math.min(155, (int) (130f * (a / 255f)));
        DrawUtils.drawRoundedOutline(x1, y1, x2, y2, r, 1f, M3.alpha(M3.outlineVariant(), outlineAlpha));
        resetGl();
    }

    /** Tonal container pill (e.g. primaryContainer, secondaryContainer, surfaceContainerHigh). */
    public static void tonalBox(float x1, float y1, float x2, float y2, float radius, int fill, int outlineColor) {
        x1 = snap(x1);
        y1 = snap(y1);
        x2 = snap(x2);
        y2 = snap(y2);
        float r = Math.min(radius, Math.min(x2 - x1, y2 - y1) / 2f);
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, r, fill);
        if ((outlineColor >>> 24) > 0)
            DrawUtils.drawRoundedOutline(x1, y1, x2, y2, r, 1f, outlineColor);
        resetGl();
    }

    /**
     * Draws a square/rounded tonal icon badge vertically centered on {@code cy}, with the Material
     * Icon glyph centered inside it.
     */
    public static void iconBadge(float x, float cy, float size, float radius, String glyph, int bgColor, int fgColor) {
        float x1 = snap(x);
        float y1 = snap(cy - size / 2f);
        float x2 = snap(x1 + size);
        float y2 = snap(y1 + size);
        float r = Math.min(radius, size / 2f);
        if ((bgColor >>> 24) > 0)
            DrawUtils.drawRoundedRect(x1, y1, x2, y2, r, bgColor);
        float boxCx = (x1 + x2) / 2f;
        float boxCy = (y1 + y2) / 2f;
        M3Icons.drawCentered(glyph, boxCx, boxCy, fgColor, 0.85f);
        resetGl();
    }

    /**
     * Draws a right-aligned tonal pill chip centered on {@code cy} and returns its width.
     */
    public static float rightChip(FontRendererExtension<?> fr, String text, float rightX, float cy,
                                  float height, float padX, int bgColor, int fgColor) {
        float textW = fr.getWidth(text);
        float w = snap(textW + padX * 2f);
        float x2 = snap(rightX);
        float x1 = snap(x2 - w);
        float y1 = snap(cy - height / 2f);
        float y2 = snap(y1 + height);
        float r = (y2 - y1) / 2f;
        if ((bgColor >>> 24) > 0)
            DrawUtils.drawRoundedRect(x1, y1, x2, y2, r, bgColor);
        float boxCx = (x1 + x2) / 2f;
        float boxCy = (y1 + y2) / 2f;
        fr.drawString(text, boxCx, boxCy, fgColor, fr.CENTREX, fr.CENTREY);
        resetGl();
        return w;
    }

    /**
     * Draws a left-aligned tonal pill chip centered on {@code cy} and returns its width.
     */
    public static float chip(FontRendererExtension<?> fr, String text, float leftX, float cy,
                             float height, float padX, int bgColor, int fgColor) {
        return leftChip(fr, text, leftX, cy, height, padX, bgColor, fgColor);
    }

    public static float leftChip(FontRendererExtension<?> fr, String text, float leftX, float cy,
                                 float height, float padX, int bgColor, int fgColor) {
        float textW = fr.getWidth(text);
        float w = snap(textW + padX * 2f);
        float x1 = snap(leftX);
        float x2 = snap(x1 + w);
        float y1 = snap(cy - height / 2f);
        float y2 = snap(y1 + height);
        float r = (y2 - y1) / 2f;
        if ((bgColor >>> 24) > 0)
            DrawUtils.drawRoundedRect(x1, y1, x2, y2, r, bgColor);
        float boxCx = (x1 + x2) / 2f;
        float boxCy = (y1 + y2) / 2f;
        fr.drawString(text, boxCx, boxCy, fgColor, fr.CENTREX, fr.CENTREY);
        resetGl();
        return w;
    }

    /**
     * Draws a Material Design 3 linear progress bar with optional trailing damage/secondary fill.
     */
    public static void progressBar(float x1, float y1, float x2, float y2,
                                   float progress, float trailProgress,
                                   int activeColor, int trailColor, int trackColor) {
        x1 = snap(x1);
        y1 = snap(y1);
        x2 = snap(x2);
        y2 = snap(y2);
        float w = x2 - x1;
        float h = y2 - y1;
        if (w <= 0f || h <= 0f)
            return;
        float r = h / 2f;
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, r, trackColor);
        float pct = MathUtils.clamp01(progress);
        float trailPct = MathUtils.clamp01(trailProgress);
        if (trailPct > pct + 0.005f && (trailColor >>> 24) > 0) {
            float tw = Math.max(h, w * trailPct);
            DrawUtils.drawRoundedRect(x1, y1, x1 + tw, y2, r, trailColor);
        }
        if (pct > 0.005f) {
            float fw = Math.max(h, w * pct);
            DrawUtils.drawRoundedRect(x1, y1, x1 + fw, y2, r, activeColor);
        }
        resetGl();
    }

    public static void divider(float x1, float x2, float y) {
        float sy = snap(y);
        float onePx = 1f / Math.max(1f, scaleFactor());
        DrawUtils.drawRect(snap(x1), sy, snap(x2), sy + onePx, M3.alpha(M3.outlineVariant(), 95));
        resetGl();
    }

    public static void resetGl() {
        GlStateManager.color(1f, 1f, 1f, 1f);
        RenderUtils.resetColorText();
    }
}

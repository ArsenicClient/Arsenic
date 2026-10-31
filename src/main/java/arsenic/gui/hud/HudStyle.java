package arsenic.gui.hud;

import arsenic.gui.click.GuiStyle;
import arsenic.gui.design.M3;
import arsenic.gui.design.M3Icons;
import arsenic.main.Arsenic;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.MathUtils;
import arsenic.utils.render.DrawUtils;

/**
 * Shared Material Design 3 building blocks for the HUD editor and for HUD elements and addons: tonal cards, chips,
 * icon badges and progress bars, all centred on an explicit vertical midpoint so margins match at every GUI scale.
 */
public final class HudStyle {

    public static final float RADIUS_CARD = 9f;
    public static final float RADIUS_COMPACT = 7f;
    public static final float RADIUS_PILL = 6f;

    private HudStyle() {}

    public static FontRendererExtension<?> body() {
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        fr.resetScale();
        return fr;
    }

    public static FontRendererExtension<?> label() {
        FontRendererExtension<?> fr = GuiStyle.fontEnabled() ? M3.label() : body();
        fr.resetScale();
        return fr;
    }

    public static FontRendererExtension<?> title() {
        FontRendererExtension<?> fr = GuiStyle.fontEnabled() ? M3.title() : body();
        fr.resetScale();
        return fr;
    }

    /** Standard HUD card: soft shadow, tonal surface and a hairline outline. */
    public static void card(float x1, float y1, float x2, float y2, float radius) {
        card(x1, y1, x2, y2, radius, 228);
    }

    /** The same card with a configurable surface alpha (0..255). */
    public static void card(float x1, float y1, float x2, float y2, float radius, int alpha) {
        int a = MathUtils.clamp(alpha, 0, 255);
        if (a <= 0)
            return;
        float r = Math.min(radius, Math.min(x2 - x1, y2 - y1) / 2f);
        int shadowAlpha = (int) (GuiStyle.shadowAlpha(95) * (a / 255f));
        if (shadowAlpha > 0 && a > 40)
            DrawUtils.drawShadow(x1, y1, x2, y2, r, 6f, shadowAlpha);
        int fill = M3.alpha(M3.mix(M3.surfaceContainerLow(), M3.surfaceContainer(), 0.45f), a);
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, r, fill);
        int outlineAlpha = Math.min(155, (int) (130f * (a / 255f)));
        DrawUtils.drawRoundedOutline(x1, y1, x2, y2, r, 1f, M3.alpha(M3.outlineVariant(), outlineAlpha));
    }

    /** A tonal container (primaryContainer, secondaryContainer, surfaceContainerHigh ...) with an optional outline. */
    public static void tonalBox(float x1, float y1, float x2, float y2, float radius, int fill, int outlineColor) {
        float r = Math.min(radius, Math.min(x2 - x1, y2 - y1) / 2f);
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, r, fill);
        if ((outlineColor >>> 24) > 0)
            DrawUtils.drawRoundedOutline(x1, y1, x2, y2, r, 1f, outlineColor);
    }

    /** A tonal square badge centred on {@code cy} with a Material icon glyph inside. */
    public static void iconBadge(float x, float cy, float size, float radius, String glyph, int bgColor, int fgColor) {
        float y1 = cy - size / 2f;
        if ((bgColor >>> 24) > 0)
            DrawUtils.drawRoundedRect(x, y1, x + size, y1 + size, Math.min(radius, size / 2f), bgColor);
        M3Icons.drawCentered(glyph, x + size / 2f, cy, fgColor, 0.85f);
    }

    /** A right-aligned pill chip centred on {@code cy}; returns its width. */
    public static float rightChip(FontRendererExtension<?> fr, String text, float rightX, float cy,
                                  float height, float padX, int bgColor, int fgColor) {
        float w = fr.getWidth(text) + padX * 2f;
        drawChip(fr, text, rightX - w, rightX, cy, height, bgColor, fgColor);
        return w;
    }

    /** A left-aligned pill chip centred on {@code cy}; returns its width. */
    public static float leftChip(FontRendererExtension<?> fr, String text, float leftX, float cy,
                                 float height, float padX, int bgColor, int fgColor) {
        float w = fr.getWidth(text) + padX * 2f;
        drawChip(fr, text, leftX, leftX + w, cy, height, bgColor, fgColor);
        return w;
    }

    public static float chip(FontRendererExtension<?> fr, String text, float leftX, float cy,
                             float height, float padX, int bgColor, int fgColor) {
        return leftChip(fr, text, leftX, cy, height, padX, bgColor, fgColor);
    }

    private static void drawChip(FontRendererExtension<?> fr, String text, float x1, float x2, float cy,
                                 float height, int bgColor, int fgColor) {
        float y1 = cy - height / 2f;
        if ((bgColor >>> 24) > 0)
            DrawUtils.drawRoundedRect(x1, y1, x2, y1 + height, height / 2f, bgColor);
        fr.drawString(text, (x1 + x2) / 2f, cy, fgColor, fr.CENTREX, fr.CENTREY);
    }

    /** A Material linear progress bar with an optional trailing fill, for example damage taken. */
    public static void progressBar(float x1, float y1, float x2, float y2,
                                   float progress, float trailProgress,
                                   int activeColor, int trailColor, int trackColor) {
        float w = x2 - x1;
        float h = y2 - y1;
        if (w <= 0f || h <= 0f)
            return;
        float r = h / 2f;
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, r, trackColor);
        float pct = MathUtils.clamp01(progress);
        float trailPct = MathUtils.clamp01(trailProgress);
        if (trailPct > pct + 0.005f && (trailColor >>> 24) > 0)
            DrawUtils.drawRoundedRect(x1, y1, x1 + Math.max(h, w * trailPct), y2, r, trailColor);
        if (pct > 0.005f)
            DrawUtils.drawRoundedRect(x1, y1, x1 + Math.max(h, w * pct), y2, r, activeColor);
    }

    public static void divider(float x1, float x2, float y) {
        DrawUtils.drawRect(x1, y, x2, y + 1f, M3.alpha(M3.outlineVariant(), 95));
    }
}

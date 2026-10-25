package arsenic.gui.design;

import arsenic.gui.click.GuiStyle;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.MathUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

public final class M3Components {

    private M3Components() {}

    public static void card(float x1, float y1, float x2, float y2, float radius, int fill) {
        glass(x1, y1, x2, y2, radius, fill, 0f);
    }

    public static void elevated(float x1, float y1, float x2, float y2, float radius, int fill, float alpha) {
        glass(x1, y1, x2, y2, radius, fill, 0.5f * alpha);
    }

    public static void window(float x1, float y1, float x2, float y2, float radius) {
        glass(x1, y1, x2, y2, radius, M3.alpha(M3.surfaceContainerLow(), 225), 1f);
    }

    /**
     * An M3 surface in the glass material: soft drop shadow by elevation, a fill frosted by the
     * preset, the liquid-glass sheen and rim, and an accent edge glow. With the Performance
     * preset it falls back to a flat tonal surface.
     *
     * @param elevation 0 for a flat card, 1 for the floating window
     */
    public static void glass(float x1, float y1, float x2, float y2, float radius, int fill, float elevation) {
        if (x2 <= x1 || y2 <= y1)
            return;
        boolean light = M3.isLight();
        if (elevation > 0.01f) {
            int shadow = GuiStyle.shadowAlpha((int) ((light ? 70 : 140) * elevation));
            DrawUtils.drawShadow(x1, y1, x2, y2, radius, GuiStyle.shadowSpread(22f * elevation), shadow);
        }

        DrawUtils.drawRoundedRect(x1, y1, x2, y2, radius, GuiStyle.glassify(fill));

        if (GuiStyle.glassEnabled()) {
            float strength = GuiStyle.glassStrength() * (0.55f + 0.45f * elevation) * (light ? 0.8f : 1f);
            DrawUtils.drawGlassRect(x1, y1, x2, y2, radius,
                    M3.alpha(M3.primary(), light ? 10 : 16), 0xFFFFFFFF, strength);
        }

        DrawUtils.drawRoundedOutline(x1, y1, x2, y2, radius, 1f,
                M3.alpha(light ? 0xFFFFFFFF : M3.outlineVariant(), light ? 190 : 120));
        if (elevation > 0.01f)
            DrawUtils.drawEdgeHighlight(x1, y1, x2, y2, radius, M3.primary(),
                    GuiStyle.edgeAlpha((int) (26 * elevation)));
    }

    public static void divider(float x1, float y, float x2) {
        DrawUtils.drawRect(x1, y, x2, y + 0.8f, M3.alpha(M3.outlineVariant(), 120));
    }

    public static void navItem(FontRendererExtension<?> fr, String label, String icon,
                               float x1, float y1, float x2, float y2,
                               float selected, float hover, boolean showIcon) {
        float radius = Math.min(10f, (y2 - y1) / 2f);
        if (hover > 0.01f)
            DrawUtils.drawRoundedRect(x1, y1, x2, y2, radius,
                    M3.alpha(M3.surfaceContainerHighest(), (int) (120 * hover)));
        if (selected > 0.01f)
            DrawUtils.drawRoundedRect(x1, y1, x2, y2, radius,
                    M3.alpha(M3.secondaryContainer(), (int) (235 * selected)));

        int fg = M3.mix(M3.onSurfaceVariant(), M3.onSecondaryContainer(), selected);
        float pad = (y2 - y1) * 0.34f;
        float textX = x1 + pad;
        if (showIcon && icon != null) {
            float size = (y2 - y1) * 0.46f;
            float ix = x1 + pad;
            float iy = (y1 + y2) / 2f - size / 2f;
            M3Icons.drawCentered(icon, ix + size / 2f, iy + size / 2f,
                    M3.mix(M3.onSurfaceVariant(), M3.onSecondaryContainer(), selected));
            textX = ix + size + pad * 0.55f;
        }
        fr.drawString(label, textX, (y1 + y2) / 2f, fg, fr.CENTREY);
        RenderUtils.resetColorText();
    }

    public static void button(FontRendererExtension<?> fr, String label,
                              float x1, float y1, float x2, float y2, float hover, boolean filled) {
        float radius = (y2 - y1) / 2f;
        int fill = filled
                ? M3.mix(M3.primaryContainer(), M3.primary(), hover * 0.28f)
                : M3.alpha(M3.surfaceContainerHighest(), 150);
        int fg = filled ? M3.onPrimaryContainer() : M3.onSurface();
        if (hover > 0.01f)
            DrawUtils.drawRoundedRect(x1, y1, x2, y2, radius, M3.alpha(fill, 255));
        else
            DrawUtils.drawRoundedRect(x1, y1, x2, y2, radius, fill);
        if (!filled)
            DrawUtils.drawRoundedOutline(x1, y1, x2, y2, radius, 1f, M3.alpha(M3.outline(), 130));
        fr.drawString(label, (x1 + x2) / 2f, (y1 + y2) / 2f, fg, fr.CENTREX, fr.CENTREY);
        RenderUtils.resetColorText();
    }

    /**
     * Material 3 switch (52x32dp spec, scaled from the control height).
     * @return the track width so callers can lay out around it.
     */
    public static float switchControl(float x1, float y1, float height, float pct, float hover) {
        float trackH = height * 0.56f;
        float trackW = trackH * 1.625f;                     // 32 -> 52dp
        float trackX2 = x1 + trackW;
        float cy = y1 + height / 2f;
        float ty1 = cy - trackH / 2f;
        float radius = trackH / 2f;

        int track = M3.mix(M3.surfaceContainerHighest(), M3.primary(), pct);
        int fg = M3.mix(M3.outline(), M3.onPrimary(), pct);
        DrawUtils.drawRoundedRect(x1, ty1, trackX2, ty1 + trackH, radius, track);
        if (pct < 0.02f)                                     // outlined track only while off
            DrawUtils.drawRoundedOutline(x1, ty1, trackX2, ty1 + trackH, radius, 1f,
                    M3.alpha(M3.outline(), 130));

        float thumbRest = trackH * 0.5f;                    // 16dp of 32
        float thumbOn = trackH * 0.75f;                     // 24dp of 32
        float thumbR = (thumbRest + (thumbOn - thumbRest) * pct) / 2f;
        float insetOff = trackH * 0.25f;
        float insetOn = (trackH - thumbOn) / 2f;
        float inset = insetOff + (insetOn - insetOff) * pct;
        float kx = x1 + thumbR + inset + (trackW - 2 * thumbR - 2 * inset) * pct;
        float ky = cy;

        if (hover > 0.01f)
            DrawUtils.drawCircle(kx, ky, thumbR * (1.62f + 0.22f * hover),
                    M3.alpha(M3.onSurface(), (int) (26 * hover)));
        DrawUtils.drawCircle(kx, ky, thumbR, fg);
        return trackW;
    }

    // ---- slider (md.slider spec) ----

    /**
     * Material 3 slider. Collapsed 4dp handle at rest; expands to a 20dp circle on hover/press
     * with a surface "gap" around the handle. Draw a value bubble above the handle while dragging.
     * @return the x of the expanded handle centre.
     */
    public static float sliderControl(float x1, float cy, float trackLen, float percent, float active) {
        float p = Math.max(0f, Math.min(1f, percent));
        float h = TRACK_H;
        float hx = x1 + trackLen * p;

        DrawUtils.drawRoundedRect(x1, cy - h / 2f, x1 + trackLen, cy + h / 2f, h / 2f, inactiveTrack());
        if (hx - x1 > 0.5f)
            DrawUtils.drawRoundedRect(x1, cy - h / 2f, Math.max(hx, x1 + h), cy + h / 2f, h / 2f, M3.primary());
        sliderHandle(hx, cy, active);
        return hx;
    }

    public static final float TRACK_H = 4f;
    private static final float HANDLE_R = 6f;

    public static int inactiveTrack() {
        return M3.alpha(M3.onSurfaceVariant(), M3.isLight() ? 55 : 60);
    }

    /**
     * A fixed-size handle: it never resizes under the cursor (which made drags feel unsteady);
     * hover and drag show as a soft primary halo instead.
     */
    public static void sliderHandle(float hx, float cy, float active) {
        float a = Math.max(0f, Math.min(1f, active));
        if (a > 0.01f)
            DrawUtils.drawCircle(hx, cy, HANDLE_R + 5f * a, M3.alpha(M3.primary(), (int) (46 * a)));
        DrawUtils.drawCircle(hx, cy, HANDLE_R, M3.primary());
    }

    /**
     * The draggable value bubble shown above the handle while sliding.
     * @return the bubble's bottom y (so callers can inset it from the row).
     */
    public static float sliderLabel(FontRendererExtension<?> fr, String text, float cx, float cyTop) {
        float pad = 6f, h = 20f;
        float tw = fr.getWidth(text);
        float x1 = cx - tw / 2f - pad, x2 = cx + tw / 2f + pad;
        DrawUtils.drawRoundedRect(x1, cyTop, x2, cyTop + h, 5f, M3.inverseSurface());
        fr.drawString(text, x2 - pad, cyTop + h / 2f, M3.inverseOnSurface(), fr.CENTREY);
        RenderUtils.resetColorText();
        return cyTop + h;
    }

    // ---- selection controls ----

    public static void checkbox(float cx, float cy, float size, float pct, float hover) {
        float r = size * 0.18f;
        if (hover > 0.01f)
            DrawUtils.drawRoundedRect(cx - size * 1.6f, cy - size * 1.6f, cx + size * 1.6f, cy + size * 1.6f,
                    size * 1.6f, M3.alpha(pct > 0.02f ? M3.primary() : M3.onSurface(), (int) (24 * hover)));
        float d = size + (size * 0.1f) * pct;
        if (pct > 0.98f) {
            DrawUtils.drawRoundedRect(cx - d, cy - d, cx + d, cy + d, r, M3.primary());
            M3Icons.drawCentered(M3Icons.CHECK, cx, cy, M3.onPrimary(), Math.min(0.78f, d / 12f));
        } else if (pct > 0.02f) {
            DrawUtils.drawRoundedRect(cx - d, cy - d, cx + d, cy + d, r,
                    M3.mix(M3.surface(), M3.primary(), pct));
            DrawUtils.drawRoundedOutline(cx - d, cy - d, cx + d, cy + d, r,
                    1.5f * (1f - pct) + 0.001f,
                    M3.mix(M3.onSurfaceVariant(), M3.primary(), pct));
            if (pct > 0.4f)
                M3Icons.drawCentered(M3Icons.CHECK, cx, cy,
                        M3.alpha(M3.onPrimary(), (int) (255 * MathUtils.clamp01((pct - 0.4f) * 2.5f))),
                        Math.min(0.78f, d / 12f));
        } else {
            DrawUtils.drawRoundedRect(cx - size, cy - size, cx + size, cy + size, r, M3.surface());
            DrawUtils.drawRoundedOutline(cx - size, cy - size, cx + size, cy + size, r, 1.5f,
                    M3.mix(M3.onSurfaceVariant(), M3.primary(), hover));
        }
    }

    public static void radio(float cx, float cy, float size, float pct, float hover) {
        float r = size / 2f;
        if (hover > 0.01f && pct < 0.02f)
            DrawUtils.drawCircle(cx, cy, r * 1.9f, M3.alpha(M3.onSurface(), (int) (22 * hover)));
        if (pct > 0.02f) {
            DrawUtils.drawCircle(cx, cy, r, M3.mix(M3.onSurfaceVariant(), M3.primary(), pct));
            DrawUtils.drawCircle(cx, cy, r * (0.68f * pct), M3.surface());
            DrawUtils.drawCircle(cx, cy, r * 0.55f * pct, M3.primary());
        } else {
            DrawUtils.drawCircle(cx, cy, r, M3.surface());
            DrawUtils.drawCircleOutline(cx, cy, r, 1.5f,
                    M3.mix(M3.onSurfaceVariant(), M3.primary(), hover));
        }
    }

    // ---- chips / segments / badges ----

    /** A filter chip; `selected` 0..1 morphs the fill and grows the leading check. */
    public static float chipSelected(FontRendererExtension<?> fr, String text, float x1, float cy,
                                     float h, float selected, float hover) {
        float pad = h * 0.55f;
        float checkW = selected * (h * 0.5f + 3f);
        float w = pad + checkW + fr.getWidth(text) + pad;
        float radius = h / 2f;
        int fill = M3.mix(M3.surfaceContainerLow(), M3.secondaryContainer(), selected);
        DrawUtils.drawRoundedRect(x1, cy - h / 2f, x1 + w, cy + h / 2f, radius, fill);
        if (hover > 0.01f)
            DrawUtils.drawRoundedRect(x1, cy - h / 2f, x1 + w, cy + h / 2f, radius,
                    M3.alpha(M3.onSurface(), (int) (16 * hover)));
        if (selected < 0.02f)
            DrawUtils.drawRoundedOutline(x1, cy - h / 2f, x1 + w, cy + h / 2f, radius, 1f,
                    M3.alpha(M3.outline(), 130));
        if (selected > 0.02f) {
            M3Icons.drawCentered(M3Icons.CHECK, x1 + pad + h * 0.25f, cy,
                    M3.mix(M3.onSurfaceVariant(), M3.onSecondaryContainer(), selected), 0.56f);
        }
        int fg = M3.mix(M3.onSurfaceVariant(), M3.onSecondaryContainer(), selected);
        fr.drawString(text, x1 + pad + checkW, cy, fg, fr.CENTREY);
        RenderUtils.resetColorText();
        return w;
    }

    /** One connected segment of a segmented button; segments should touch (x2 of n meets x1 of n+1). */
    public static void segment(FontRendererExtension<?> fr, String text, String glyph,
                               float x1, float y1, float x2, float y2,
                               float selected, boolean first, boolean last, float hover) {
        int fill = M3.mix(M3.surface(), M3.secondaryContainer(), selected);
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, 0f, fill);
        if (hover > 0.01f)
            DrawUtils.drawRoundedRect(x1, y1, x2, y2, 0f, M3.alpha(M3.onSurface(), (int) (14 * hover)));
        float radius = (y2 - y1) / 2f;
        // re-round the outer corners only; neighbours stay flush
        boolean[] outer = {first, last, last, first};
        DrawUtils.drawRoundedOutline(x1, y1, x2, y2, radius, 1f, M3.alpha(M3.outline(), 150), outer);

        float cx = (x1 + x2) / 2f, cy = (y1 + y2) / 2f;
        if (selected > 0.02f) {
            M3Icons.drawCentered(M3Icons.CHECK, cx - fr.getWidth(text) / 2f - y2 + y1 * 0.4f, cy,
                    M3.onSecondaryContainer(), 0.5f);
        }
        fr.drawString(text, cx, cy, M3.mix(M3.onSurfaceVariant(), M3.onSecondaryContainer(), selected),
                fr.CENTREX, fr.CENTREY);
        RenderUtils.resetColorText();
    }

    /** Material 3 badge; pass "" for the 6dp dot, otherwise an 18dp count pill. */
    public static void badge(FontRendererExtension<?> fr, String text, float cx, float cy,
                             int fill, int fg) {
        if (text == null || text.isEmpty()) {
            DrawUtils.drawCircle(cx, cy, 3f, fill);
            return;
        }
        float pad = 4f;
        float w = fr.getWidth(text) + pad * 2f, h = 16f;
        DrawUtils.drawRoundedRect(cx - w - 2f, cy - h - 2f, cx - 2f, cy - 2f, h / 2f, fill);
        fr.drawString(text, cx - 2f - pad, cy - 2f - h / 2f, fg, fr.CENTREY);
        RenderUtils.resetColorText();
    }

    // ---- feedback ----

    public static void linearProgress(float x1, float cy, float trackLen, float pct, int fill) {
        float h = 4f;
        float p = Math.max(0f, Math.min(1f, pct));
        DrawUtils.drawRoundedRect(x1, cy - h / 2f, x1 + trackLen, cy + h / 2f, h / 2f,
                M3.alpha(fill, 90));
        if (p > 0.004f)
            DrawUtils.drawRoundedRect(x1, cy - h / 2f, x1 + Math.max(trackLen * p, h),
                    cy + h / 2f, h / 2f, fill);
        DrawUtils.drawCircle(x1 + 2f, cy, 2f, fill);
    }

    /** A snack that slides up from the bottom of its container. Returns its height. */
    public static float snackbar(FontRendererExtension<?> fr, String text, String action,
                                 float x1, float y1, float x2, float pct) {
        if (pct <= 0.01f)
            return 0f;
        float h = 48f;
        float y2 = y1 + h * pct;
        float yTop = y1 + h - y2;
        DrawUtils.drawRoundedRect(x1, yTop, x2, y2, 4f, M3.alpha(M3.inverseSurface(), (int) (255 * pct)));
        fr.drawString(text, x1 + 16f * pct + 16f, (yTop + y2) / 2f, M3.alpha(M3.inverseOnSurface(), (int) (255 * pct)),
                fr.CENTREY);
        RenderUtils.resetColorText();
        if (action != null && pct > 0.5f)
            fr.drawString(action, x2 - 16f - fr.getWidth(action) * pct, (yTop + y2) / 2f,
                    M3.alpha(M3.primary(), (int) (255 * MathUtils.clamp01((pct - 0.5f) * 2f))), fr.CENTREY);
        RenderUtils.resetColorText();
        return y2 - yTop;
    }

    /** A plain tooltip; draw after the content so it floats above. */
    public static void tooltip(FontRendererExtension<?> fr, String text, float cx, float cyBottom) {
        float pad = 8f;
        float h = 24f;
        float w = fr.getWidth(text) + pad * 2f;
        float y2 = cyBottom;
        DrawUtils.drawRoundedRect(cx - w / 2f, y2 - h, cx + w / 2f, y2, 4f, M3.inverseSurface());
        fr.drawString(text, cx, y2 - h / 2f, M3.inverseOnSurface(), fr.CENTREX, fr.CENTREY);
        RenderUtils.resetColorText();
    }

    // ---- fab ----

    /** Material 3 FAB: 56dp square, 16dp corners, primary-container fill. */
    public static void fab(FontRendererExtension<?> unused, String glyph, float cx, float cy,
                           float size, int fill, int fg) {
        float r = size * 16f / 56f;
        DrawUtils.drawRoundedRect(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f, r, fill);
        M3Icons.drawCentered(glyph, cx, cy, fg, Math.min(0.75f, size / 42f));
    }

    // ---- text field ----

    /** A filled text field return the inset x where the text should start. */
    public static float textFieldBg(float x1, float y1, float x2, float y2, float hover, float focus) {
        float radius = 4f;
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, radius,
                M3.mix(M3.surfaceVariant(), M3.surfaceContainerHighest(), hover * 0.5f));
        // bottom indicator: 1dp inactive, 2dp focused, outline -> primary
        float bh = 1f + focus;
        DrawUtils.drawRect(x1, y2 - bh - 1f, x2, y2 - 1f,
                M3.mix(M3.alpha(M3.onSurfaceVariant(), 160), M3.primary(), focus));
        return x1 + 12f;
    }

    public static void icon(ResourceLocation texture, float x, float y, float size, int color) {
        texture(texture, x, y, size, color);
    }

    private static void texture(ResourceLocation texture, float x, float y, float size, int color) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GlStateManager.enableBlend();
        RenderUtils.color2(color, 1f);
        Gui.drawModalRectWithCustomSizedTexture((int) x, (int) y, 0, 0,
                (int) Math.ceil(size), (int) Math.ceil(size), (int) Math.ceil(size), (int) Math.ceil(size));
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    public static void scrollbar(float x, float y, float height, float maxScroll, float scroll) {
        if (maxScroll <= 0)
            return;
        float thumb = Math.max(height * 0.1f, height * (height / (height + maxScroll)));
        float ty = y + (scroll / -maxScroll) * (height - thumb);
        DrawUtils.drawRoundedRect(x - 3f, y, x - 1f, y + height, 1f, M3.alpha(M3.surfaceContainerHighest(), 110));
        DrawUtils.drawRoundedRect(x - 3f, ty, x - 1f, ty + thumb, 1f, M3.alpha(M3.primary(), 220));
    }

    public static float pill(FontRendererExtension<?> fr, String text, float x1, float cy, float h,
                             int fill, int fg) {
        float pad = h * 0.52f;
        float w = fr.getWidth(text) + pad * 2f;
        DrawUtils.drawRoundedRect(x1, cy - h / 2f, x1 + w, cy + h / 2f, h / 2f, fill);
        fr.drawString(text, x1 + pad, cy, fg, fr.CENTREY);
        RenderUtils.resetColorText();
        return w;
    }
}

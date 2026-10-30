package arsenic.gui.design;

import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;

/** Drawing helpers shared by the setting widgets (properties) that M3Components does not cover. */
public final class M3Controls {

    private M3Controls() {}

    /**
     * Call every frame of a drag. Gives the cursor to {@code key} (even outside its rect) and stops
     * anything drawn after it from taking the cursor, so rows under the dragging cursor stay quiet.
     */
    public static boolean dragHold(Object key) {
        boolean owns = Hover.test(key, -100000f, -100000f, 100000f, 100000f);
        Hover.suppress(true);
        return owns;
    }

    /** A small value pill. {@code active} 0..1 morphs it from tonal to primary. Returns its width. */
    public static float valueChip(FontRendererExtension<?> fr, String text, float rightX, float cy,
                                  float h, float active) {
        return valueChip(fr, text, rightX, cy, h, active, 0f);
    }

    /**
     * As above, at least {@code minTextW} wide (text centred), so a slider's track keeps its length
     * while the value's digit count changes under a drag.
     */
    public static float valueChip(FontRendererExtension<?> fr, String text, float rightX, float cy,
                                  float h, float active, float minTextW) {
        int fill = M3.mix(M3.alpha(M3.surfaceContainerHighest(), 230), M3.primary(), active);
        int fg = M3.mix(M3.onSurfaceVariant(), M3.onPrimary(), active);
        float pad = h * 0.42f;
        float textW = fr.getWidth(text);
        float w = Math.max(textW, minTextW) + pad * 2f;
        DrawUtils.drawRoundedRect(rightX - w, cy - h / 2f, rightX, cy + h / 2f, h * 0.3f, fill);
        fr.drawString(text, rightX - w / 2f - textW / 2f, cy, fg, fr.CENTREY);
        RenderUtils.resetColorText();
        return w;
    }

    /**
     * Material 3 range slider: one inactive track, an active part between two handles.
     * {@code activeMin}/{@code activeMax} (0..1) expand each handle on hover or drag.
     */
    public static void rangeSlider(float x1, float cy, float len, float pMin, float pMax,
                                   float activeMin, float activeMax) {
        float h = M3Components.TRACK_H;
        float a = x1 + len * Math.max(0f, Math.min(1f, pMin));
        float b = x1 + len * Math.max(0f, Math.min(1f, pMax));

        DrawUtils.drawRoundedRect(x1, cy - h / 2f, x1 + len, cy + h / 2f, h / 2f, M3Components.inactiveTrack());
        DrawUtils.drawRoundedRect(a, cy - h / 2f, Math.max(b, a + h), cy + h / 2f, h / 2f, M3.primary());

        M3Components.sliderHandle(a, cy, activeMin);
        M3Components.sliderHandle(b, cy, activeMax);
    }

    /** A transparency checkerboard that reads in light and dark. */
    public static void checker(float x1, float y1, float x2, float y2, float radius, float cell) {
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, radius, M3.mix(M3.surface(), M3.onSurface(), 0.18f));
        boolean odd = false;
        int dark = M3.mix(M3.surface(), M3.onSurface(), 0.42f);
        for (float yy = y1; yy < y2; yy += cell) {
            boolean d = odd;
            for (float xx = x1; xx < x2; xx += cell) {
                if (d)
                    DrawUtils.drawRect(xx, yy, Math.min(xx + cell, x2), Math.min(yy + cell, y2), dark);
                d = !d;
            }
            odd = !odd;
        }
    }

    /** Outlined dropdown/field container. {@code focus} 0..1 turns the outline primary. */
    public static void field(float x1, float y1, float x2, float y2, float radius, float hover, float focus) {
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, radius,
                M3.mix(M3.surfaceContainerHigh(), M3.surfaceContainerHighest(), Math.max(hover * 0.6f, focus)));
        DrawUtils.drawRoundedOutline(x1, y1, x2, y2, radius, 1f,
                M3.mix(M3.alpha(M3.outline(), 150), M3.primary(), focus));
    }
}

package arsenic.gui.design;

import arsenic.main.Arsenic;
import arsenic.utils.render.DrawUtils;
import net.minecraft.resources.Identifier;


/**
 * Brand mark: the animated pixel flask ({@link PixelFlask}, shared with the rail mascot) plus the
 * theme's modern ARSENIC wordmark, its letters as tall as the flask.
 */
public final class LabLogo {

    private LabLogo() {}

    // ------------------------------------------------------------------ logo

    /** The modern wordmark texture is 588 x 166; its letters only fill rows 37..129, so we crop to those. */
    private static final float TEX_W = 588f, TEX_H = 166f, INK_TOP = 37f, INK_BOTTOM = 130f;
    private static final float INK_ASPECT = TEX_W / (INK_BOTTOM - INK_TOP);
    private static final float GAP = 0.22f;

    /** The mark is the same pixel flask as the rail mascot's, so the two always match. */
    private static final PixelFlask PIXEL_FLASK = new PixelFlask(0x10C0L);
    private static final float PIXEL_ASPECT = PixelFlask.W / (float) PixelFlask.H;
    private static float wordHover;

    public static float width(float height) {
        return height * (PIXEL_ASPECT + GAP + INK_ASPECT);
    }

    /** The tallest logo that fits in {@code maxWidth}, capped at {@code maxHeight}. */
    public static float heightFor(float maxWidth, float maxHeight) {
        return Math.min(maxHeight, maxWidth / (PIXEL_ASPECT + GAP + INK_ASPECT));
    }

    /** Briefly bursts bubbles (e.g. on module toggle). */
    public static void pulse() {
        PIXEL_FLASK.pulse();
    }

    public static void draw(float x, float y, float height, boolean hovered) {
        float H = height;
        // ARSENIC, then the flask; the letters stand as tall as the flask
        wordHover += ((hovered ? 1f : 0f) - wordHover) * 0.2f;
        drawWordmark(x, y, H * INK_ASPECT, H, wordHover);
        PIXEL_FLASK.draw(x + H * (INK_ASPECT + GAP), y, H / PixelFlask.H, hovered);
    }

    private static void drawWordmark(float x, float y, float w, float h, float hover) {
        Identifier logo = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getAltLogoPath();
        int alpha = (int) (255 * (0.93f + 0.07f * hover));
        DrawUtils.drawTextureRegion(logo, x, y, w, h, 0, (int) INK_TOP, (int) TEX_W, (int) (INK_BOTTOM - INK_TOP),
                (int) TEX_W, (int) TEX_H, (alpha << 24) | (M3.onSurface() & 0xFFFFFF));
    }
}

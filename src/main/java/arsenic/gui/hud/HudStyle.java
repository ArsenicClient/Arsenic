package arsenic.gui.hud;

import arsenic.gui.design.M3;
import arsenic.gui.design.M3Icons;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import org.lwjgl.opengl.GL11;

/** Material You 3 tokens and drawing helpers shared by every HUD element. */
public final class HudStyle {

    public static final float RADIUS = 14f;
    public static final float RADIUS_SMALL = 9f;
    public static final float PAD = 7f;

    private HudStyle() {}

    public static FontRendererExtension<?> body() {
        FontRendererExtension<?> fr = M3.body();
        fr.setScale(0.95f);
        return fr;
    }

    public static FontRendererExtension<?> label() {
        FontRendererExtension<?> fr = M3.label();
        fr.setScale(0.84f);
        return fr;
    }

    public static FontRendererExtension<?> title() {
        FontRendererExtension<?> fr = M3.title();
        fr.setScale(1f);
        return fr;
    }

    public static FontRendererExtension<?> headline() {
        FontRendererExtension<?> fr = M3.headline();
        fr.setScale(0.96f);
        return fr;
    }

    public static void reset() {
        M3.body().resetScale();
        M3.label().resetScale();
        M3.title().resetScale();
        M3.headline().resetScale();
    }

    public static void card(float x1, float y1, float x2, float y2, float radius, int fill) {
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, radius, fill);
        DrawUtils.drawRoundedOutline(x1, y1, x2, y2, radius, 1f,
                M3.alpha(M3.outlineVariant(), 105));
    }

    public static void card(float x1, float y1, float x2, float y2, float radius, int fill, int outline) {
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, radius, fill);
        DrawUtils.drawRoundedOutline(x1, y1, x2, y2, radius, 1f, outline);
    }

    public static void divider(float x1, float y, float x2) {
        DrawUtils.drawRect(x1, y, x2, y + 0.75f, M3.alpha(M3.outlineVariant(), 105));
    }

    public static void iconBadge(String glyph, float cx, float cy, float radius, int fill, int foreground) {
        DrawUtils.drawCircle(cx, cy, radius, fill);
        M3Icons.drawCentered(glyph, cx, cy, foreground,
                Math.min(0.72f, Math.max(0.40f, radius / 19f)));
    }

    public static float pill(FontRendererExtension<?> fr, String text, float x, float cy, float height,
                             int fill, int foreground) {
        float pad = height * 0.58f;
        float w = fr.getWidth(text) + pad * 2f;
        DrawUtils.drawRoundedRect(x, cy - height / 2f, x + w, cy + height / 2f, height / 2f, fill);
        fr.drawString(text, x + pad, cy, foreground, fr.CENTREY);
        return w;
    }

    public static float pillWidth(FontRendererExtension<?> fr, String text, float height) {
        return fr.getWidth(text) + height * 0.58f * 2f;
    }

    public static float iconPillWidth(FontRendererExtension<?> fr, String text, float height) {
        return height * 0.46f * 2f + height * 0.66f + height * 0.32f + fr.getWidth(text);
    }

    public static float iconPill(FontRendererExtension<?> fr, String glyph, String text,
                                 float x, float cy, float height, int fill, int foreground) {
        float pad = height * 0.46f;
        float icon = height * 0.66f;
        float w = pad + icon + height * 0.32f + fr.getWidth(text) + pad;
        DrawUtils.drawRoundedRect(x, cy - height / 2f, x + w, cy + height / 2f, height / 2f, fill);
        M3Icons.drawCentered(glyph, x + pad + icon / 2f, cy, foreground,
                Math.min(0.72f, Math.max(0.30f, icon / 32f)));
        fr.drawString(text, x + pad + icon + height * 0.32f, cy, foreground, fr.CENTREY);
        return w;
    }

    public static void progress(float x1, float y1, float x2, float y2, float radius, float pct,
                                int track, int fill) {
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, radius, track);
        float width = (x2 - x1) * Math.max(0f, Math.min(1f, pct));
        if (width <= 0.05f)
            return;
        float r = Math.min(radius, Math.max(0.6f, width / 2f));
        DrawUtils.drawRoundedRect(x1, y1, x1 + Math.max(width, 1.2f), y2, r, fill);
    }

    /**
     * M3 linear progress: a 4dp round track, a 4dp round stop dot at the start, then the
     * round-capped active indicator with a 4dp gap. Track and fill come in as alpha-mixed tokens.
     */
    public static void md3Progress(float x1, float y, float x2, float pct, int track, int fill) {
        md3Progress(x1, y, x2, pct, pct, track, fill, fill);
    }

    /**
     * Same, with a lagging trail pill (e.g. a grey "recent damage" line that catches up slowly);
     * the trail is only drawn where it runs ahead of the active indicator.
     */
    public static void md3Progress(float x1, float y, float x2, float pct, float trailPct,
                                   int track, int trail, int fill) {
        float h = 4f;
        DrawUtils.drawRoundedRect(x1, y, x2, y + h, h / 2f, track);
        float keep = Math.max(0f, Math.min(1f, pct));
        float lag = Math.max(keep, Math.max(0f, Math.min(1f, trailPct)));
        float startX = x1 + h + h; // dot + 4dp gap
        if (lag > keep + 0.001f) {
            float trailEnd = startX + (x2 - startX) * lag;
            DrawUtils.drawRoundedRect(startX, y, trailEnd, y + h, h / 2f, trail);
        }
        DrawUtils.drawCircle(x1 + h / 2f, y + h / 2f, h / 2f, fill);
        float endX = startX + Math.max(0f, (x2 - startX) * keep);
        if (endX - startX >= 1.2f)
            DrawUtils.drawRoundedRect(startX, y, endX, y + h, h / 2f, fill);
    }

    public static void gradientProgress(float x1, float y1, float x2, float y2, float radius, float pct,
                                        int track, int left, int right) {
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, radius, track);
        float width = (x2 - x1) * Math.max(0f, Math.min(1f, pct));
        if (width <= 0.05f)
            return;
        float r = Math.min(radius, Math.max(0.6f, width / 2f));
        DrawUtils.drawGradientRoundedRect(x1, y1, x1 + Math.max(width, 1.2f), y2, r, left, left, right, right);
    }

    public static float shadowText(FontRendererExtension<?> fr, String text, float x, float cy, int color) {
        fr.drawStringWithShadow(text, x, cy, color, fr.CENTREY);
        return fr.getWidth(text);
    }

    public static void pushScale(float anchorX, float anchorY, float scale) {
        GL11.glPushMatrix();
        if (Math.abs(scale - 1f) > 0.001f) {
            GL11.glTranslatef(anchorX, anchorY, 0f);
            GL11.glScalef(scale, scale, 1f);
            GL11.glTranslatef(-anchorX, -anchorY, 0f);
        }
    }

    public static void popScale() {
        GL11.glPopMatrix();
    }

    public static void resetColor() {
        RenderUtils.resetColorText();
    }
}

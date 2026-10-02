package arsenic.utils.render;

import arsenic.utils.java.UtilityClass;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.awt.Color;

/**
 * 2D shape drawing for the HUD and the ClickGUI.
 * <p>
 * On 1.8 these were GLSL SDF shaders and immediate-mode GL. Modern Minecraft only draws GUI
 * geometry through its own pipelines, so every shape here is tessellated into float quads and
 * submitted via {@link QuadBatch}. Signatures are kept from the old class so call sites port as-is.
 * <p>
 * Radii keep their old units: the shaders took the radius in framebuffer pixels while everything
 * else was in GUI units, and the ClickGUI was laid out at GUI scale 2, so a radius of {@code r}
 * always looked like {@code r / 2} GUI units. {@link #RADIUS_SCALE} preserves that.
 */
public class DrawUtils extends UtilityClass {

    private static final float RADIUS_SCALE = 0.5f;
    private static final boolean[] ALL_CORNERS = {true, true, true, true};

    /** Kept for call-site compatibility; the new renderer has no scale-dependent masking. */
    public static float overrideScaleFactor = -1f;

    public static void drawRect(float x, float y, float x1, float y1, int color) {
        if (alphaOf(color) == 0)
            return;
        new QuadBatch().rect(Math.min(x, x1), Math.min(y, y1), Math.max(x, x1), Math.max(y, y1), color).submit();
    }

    public static void drawGradientRect(float x, float y, float x1, float y1, int top, int bottom) {
        new QuadBatch()
                .vertex(x, y, top).vertex(x, y1, bottom).vertex(x1, y1, bottom).vertex(x1, y, top)
                .submit();
    }

    public static void drawHorizontalGradientRect(float x, float y, float x1, float y1, int left, int right) {
        new QuadBatch()
                .vertex(x, y, left).vertex(x, y1, left).vertex(x1, y1, right).vertex(x1, y, right)
                .submit();
    }

    public static void drawRoundedRect(float x, float y, float x1, float y1, final float radius, final int color, boolean[] round) {
        if (alphaOf(color) == 0)
            return;
        fillPath(roundedPath(x, y, x1, y1, radius * RADIUS_SCALE, round), (px, py) -> color);
    }

    public static void drawRoundedRect(float x, float y, float x1, float y1, final float radius, final int color) {
        drawRoundedRect(x, y, x1, y1, radius, color, ALL_CORNERS);
    }

    public static void drawRoundedOutline(float x, float y, float x1, float y1, final float radius, final float borderSize, final int color, boolean[] round) {
        if (alphaOf(color) == 0 || borderSize <= 0)
            return;
        // Line widths were in framebuffer pixels too.
        float width = borderSize * RADIUS_SCALE;
        float r = radius * RADIUS_SCALE;
        float[] outer = roundedPath(x, y, x1, y1, r, round);
        float[] inner = roundedPath(x + width, y + width, x1 - width, y1 - width, Math.max(0, r - width), round);
        strokeBetween(outer, inner, color, color);
    }

    public static void drawRoundedOutline(float x, float y, float x1, float y1, final float radius, final float borderSize, final int color) {
        drawRoundedOutline(x, y, x1, y1, radius, borderSize, color, ALL_CORNERS);
    }

    public static void drawBorderedRoundedRect(float x, float y, float x1, float y1, float radius, float borderSize, int borderC, int insideC, boolean[] round) {
        drawRoundedRect(x, y, x1, y1, radius, insideC, round);
        drawRoundedOutline(x, y, x1, y1, radius, borderSize, borderC, round);
    }

    public static void drawBorderedRoundedRect(float x, float y, float x1, float y1, float radius, float borderSize, int borderC, int insideC) {
        drawBorderedRoundedRect(x, y, x1, y1, radius, borderSize, borderC, insideC, ALL_CORNERS);
    }

    public static void drawGradientRoundedRect(float x, float y, float x1, float y1, final float radius,
                                               final int bottomLeft, int topLeft, int bottomRight, int topRight) {
        float w = Math.max(1e-3f, x1 - x), h = Math.max(1e-3f, y1 - y);
        fillPath(roundedPath(x, y, x1, y1, radius * RADIUS_SCALE, ALL_CORNERS), (px, py) -> {
            float u = (px - x) / w, v = (py - y) / h;
            int top = RenderUtils.interpolateColoursInt(topLeft, topRight, u);
            int bottom = RenderUtils.interpolateColoursInt(bottomLeft, bottomRight, u);
            return RenderUtils.interpolateColoursInt(top, bottom, v);
        });
    }

    public static void drawGradientRound(float x, float y, float width, float height, float radius,
                                         Color bottomLeft, Color topLeft, Color bottomRight, Color topRight) {
        drawGradientRoundedRect(x, y, x + width, y + height, radius,
                bottomLeft.getRGB(), topLeft.getRGB(), bottomRight.getRGB(), topRight.getRGB());
    }

    public static void drawShaderRect(float x, float y, float width, float height, float radius, int c) {
        drawRoundedRect(x, y, x + width, y + height, radius, c);
    }

    /**
     * Soft elevation shadow - the element reads as hovering slightly above whatever is behind it.
     * Two feathered passes: a centred ambient halo and a key shadow with a small downward drop.
     * Draw this BEFORE the element's own fill.
     *
     * @param radius corner radius of the element being shadowed
     * @param spread elevation - how far (px) the shadow reaches
     * @param alpha  darkness of the shadow's core (0-255)
     */
    public static void drawShadow(float x1, float y1, float x2, float y2, float radius, float spread, int alpha) {
        drawShadow(x1, y1, x2, y2, radius, spread, alpha, 6);
    }

    /** {@code layers} is kept for call-site compatibility; the feather is a single smooth ramp. */
    public static void drawShadow(float x1, float y1, float x2, float y2, float radius, float spread, int alpha, int layers) {
        if (alpha <= 0 || spread <= 0f)
            return;
        drawBlurredShadow(x1, y1, x2, y2, radius, spread * 0.55f, (int) (alpha * 0.5f), 0f);
        drawBlurredShadow(x1, y1, x2, y2, radius, spread, (int) (alpha * 0.7f), spread * 0.35f);
    }

    /** One feathered pass: opaque at the element's edge, fading to nothing {@code spread} px out. */
    public static void drawBlurredShadow(float x1, float y1, float x2, float y2, float radius, float spread, int alpha, float dropY) {
        if (alpha <= 0 || spread <= 0f)
            return;
        int core = Math.min(255, alpha) << 24;
        float r = radius * RADIUS_SCALE;
        y1 += dropY;
        y2 += dropY;
        float[] inner = roundedPath(x1, y1, x2, y2, r, ALL_CORNERS);
        float[] outer = roundedPath(x1 - spread, y1 - spread, x2 + spread, y2 + spread, r + spread, ALL_CORNERS);
        strokeBetween(outer, inner, 0, core);
        fillPath(inner, (px, py) -> core);
    }

    /**
     * Subtle light rim around a raised element. Draw this AFTER the element's fill.
     *
     * @param color base RGB of the rim (alpha byte ignored)
     * @param alpha rim opacity (0-255)
     */
    public static void drawEdgeHighlight(float x1, float y1, float x2, float y2, float radius, int color, int alpha) {
        if (alpha <= 0)
            return;
        int inner = (Math.min(255, alpha) << 24) | (color & 0x00FFFFFF);
        int outer = (Math.min(255, alpha / 2) << 24) | (color & 0x00FFFFFF);
        drawRoundedOutline(x1, y1, x2, y2, radius, 3.0f, outer);
        drawRoundedOutline(x1, y1, x2, y2, radius, 1.5f, inner);
    }

    /**
     * Frosted-glass surface treatment drawn ON TOP of a panel's base fill. The 1.8 version was an
     * animated shader; this keeps its look in static form - a tint film, a vertical gloss and a
     * bright rim - which reads the same at a glance and costs nothing.
     */
    public static void drawGlassRect(float x, float y, float x1, float y1, float radius,
                                     int filmColor, int rimColor, float strength) {
        if (strength <= 0f)
            return;
        strength = Math.min(1f, strength);
        drawRoundedRect(x, y, x1, y1, radius, filmColor);
        int glossTop = ((int) (40 * strength) << 24) | 0xFFFFFF;
        float midY = y + (y1 - y) * 0.45f;
        drawGradientRoundedRect(x, y, x1, midY, radius, 0x00FFFFFF, glossTop, 0x00FFFFFF, glossTop);
        drawEdgeHighlight(x, y, x1, y1, radius, rimColor, (int) (90 * strength));
    }

    public static void drawBorderedCircle(float centrePointX, float centrePointY, float radius, float borderSize, int borderColour, int insideColour) {
        drawCircle(centrePointX, centrePointY, radius, insideColour);
        drawCircleOutline(centrePointX, centrePointY, radius, borderSize, borderColour);
    }

    public static void drawCircleOutline(float centrePointX, float centrePointY, float radius, float borderSize, int color) {
        drawRoundedOutline(centrePointX - radius, centrePointY - radius, centrePointX + radius, centrePointY + radius,
                radius / RADIUS_SCALE, borderSize, color);
    }

    public static void drawCircle(float centrePointX, float centrePointY, float radius, int color) {
        drawRoundedRect(centrePointX - radius, centrePointY - radius, centrePointX + radius, centrePointY + radius,
                radius / RADIUS_SCALE, color);
    }

    /** Draws a perfect triangle when height == width. A negative height points it upwards. */
    public static void drawTriangle(float x1, float y1, float width, float height, int colour) {
        float realHeight = (float) (height * Math.sqrt(3)) / 2f;
        new QuadBatch().triangle(x1, y1, x1 + width / 2f, y1 + realHeight, x1 + width, y1, colour).submit();
    }

    /** Draws a whole texture stretched over the rectangle, tinted by {@code color} (ARGB). */
    public static void drawTexture(Identifier texture, float x, float y, float width, float height, int color) {
        if (width <= 0 || height <= 0)
            return;
        GuiGraphicsExtractor graphics = RenderContext.graphics();
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(width, height);
        // a 1x1 quad over a "1x1" texture samples the full image; the pose stretches it into place
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0f, 0f, 1, 1, 1, 1, RenderContext.applyAlpha(color));
        graphics.pose().popMatrix();
    }

    /** Builds the outline of a rounded rectangle as x,y pairs, going around clockwise from the top-left. */
    private static float[] roundedPath(float x, float y, float x1, float y1, float radius, boolean[] round) {
        float maxRadius = Math.max(0, Math.min((x1 - x), (y1 - y)) / 2f);
        float r = Math.max(0, Math.min(radius, maxRadius));
        int segments = r <= 0 ? 0 : Math.max(3, Math.min(24, (int) (r * 3)));
        float[] path = new float[(segments + 1) * 4 * 2];
        int i = 0;
        // corner centres and the start angle of each arc (0 = +x, angles grow clockwise on screen)
        float[][] corners = {
                {x + r, y + r, 180, round[0] ? 1 : 0},   // top-left
                {x1 - r, y + r, 270, round[3] ? 1 : 0},  // top-right
                {x1 - r, y1 - r, 0, round[2] ? 1 : 0},   // bottom-right
                {x + r, y1 - r, 90, round[1] ? 1 : 0},   // bottom-left
        };
        float[][] square = {{x, y}, {x1, y}, {x1, y1}, {x, y1}};
        for (int c = 0; c < 4; c++) {
            float[] corner = corners[c];
            for (int s = 0; s <= segments; s++) {
                if (corner[3] == 0 || segments == 0) {
                    path[i++] = square[c][0];
                    path[i++] = square[c][1];
                } else {
                    double angle = Math.toRadians(corner[2] + 90.0 * s / segments);
                    path[i++] = corner[0] + (float) Math.cos(angle) * r;
                    path[i++] = corner[1] + (float) Math.sin(angle) * r;
                }
            }
        }
        return path;
    }

    private interface ColorAt {
        int at(float x, float y);
    }

    private static void fillPath(float[] path, ColorAt colorAt) {
        int points = path.length / 2;
        if (points < 3)
            return;
        float cx = 0, cy = 0;
        for (int i = 0; i < points; i++) {
            cx += path[i * 2];
            cy += path[i * 2 + 1];
        }
        cx /= points;
        cy /= points;
        int centre = colorAt.at(cx, cy);
        QuadBatch batch = new QuadBatch();
        for (int i = 0; i < points; i++) {
            int j = (i + 1) % points;
            float ax = path[i * 2], ay = path[i * 2 + 1];
            float bx = path[j * 2], by = path[j * 2 + 1];
            if (ax == bx && ay == by)
                continue;
            // fan triangles are wound the opposite way to quads so they are not culled
            batch.triangle(cx, cy, centre, bx, by, colorAt.at(bx, by), ax, ay, colorAt.at(ax, ay));
        }
        batch.submit();
    }

    /** Fills the band between two paths with the same point count, e.g. an outline or a feather. */
    private static void strokeBetween(float[] outer, float[] inner, int outerColor, int innerColor) {
        int points = Math.min(outer.length, inner.length) / 2;
        QuadBatch batch = new QuadBatch();
        for (int i = 0; i < points; i++) {
            int j = (i + 1) % points;
            batch.vertex(outer[i * 2], outer[i * 2 + 1], outerColor)
                    .vertex(inner[i * 2], inner[i * 2 + 1], innerColor)
                    .vertex(inner[j * 2], inner[j * 2 + 1], innerColor)
                    .vertex(outer[j * 2], outer[j * 2 + 1], outerColor);
        }
        batch.submit();
    }

    private static int alphaOf(int color) {
        return (color >>> 24) & 0xFF;
    }
}

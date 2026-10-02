package arsenic.gui.click;

import arsenic.gui.themes.ThemeManager;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import org.lwjgl.opengl.GL11;

/**
 * The single place the ClickGUI's look is defined.
 * <p>
 * Before this existed every component invented its own radii, insets and shadow spreads out of
 * whatever local {@code width / 15f} happened to be handy, so nothing lined up between a module
 * card, a property row and a dropdown. Everything visual now comes from here: a spacing scale, a
 * radius scale, animation durations, and a small set of surface primitives that already know about
 * the theme, the liquid-glass toggle and the 3D-depth sliders.
 * <p>
 * The primitives deliberately mirror the existing render stack rather than replacing it - they are
 * thin compositions of {@link DrawUtils} - so themes, the shader backdrop and the burn transition
 * all keep working untouched.
 */
public final class UITheme {

    private UITheme() {}

    // ---------------------------------------------------------------
    //  Scales
    //
    //  Sizes are expressed in "units" and resolved against the GUI height so
    //  the layout keeps its proportions at any resolution, exactly like the
    //  old `5 * (i / 100)` idiom did - just centrally and consistently.
    // ---------------------------------------------------------------

    /** Base unit: one percent of the GUI height, the atom every size is built from. */
    public static float unit(float guiHeight) {
        return guiHeight / 100f;
    }

    /** Spacing scale (4-point rhythm, resolution independent). */
    public static float space(float guiHeight, float steps) {
        return unit(guiHeight) * steps;
    }

    // Corner radii, in screen pixels relative to an element's own height.
    /** Fully rounded - pills, toggles, knobs. */
    public static float radiusPill(float height) { return height / 2f; }
    /** Cards, panels, dropdown bodies. */
    public static float radiusCard(float height) { return Math.max(3f, height * 0.28f); }
    /** Inputs, chips, small controls. */
    public static float radiusChip(float height) { return Math.max(2f, height * 0.34f); }

    // Animation durations, in ms. Short = state feedback, long = layout change.
    public static final int DUR_PRESS  = 90;
    public static final int DUR_HOVER  = 160;
    public static final int DUR_TOGGLE = 260;
    public static final int DUR_EXPAND = 320;

    /**
     * Duration for expanding something whose height depends on its contents - a module card, a
     * folder, a dropdown.
     * <p>
     * A fixed duration means a panel with eight settings travels eight times as fast as one with a
     * single setting, which is what makes big modules look like they snap open. Deriving the
     * duration from the distance gives every panel the same <em>speed</em> instead. The floor and
     * ceiling keep a one-row panel from feeling sluggish and a very tall one from dragging.
     */
    public static int expandDuration(float contentHeight) {
        return (int) Math.max(150, Math.min(420, 110 + Math.abs(contentHeight) * 1.15f));
    }

    // ---------------------------------------------------------------
    //  Colour helpers
    // ---------------------------------------------------------------

    public static int alpha(int argb, int a) {
        return ColorUtils.setColor(argb, 0, Math.max(0, Math.min(255, a)));
    }

    public static int alpha(int argb, float pct) {
        return alpha(argb, (int) (255 * Math.max(0f, Math.min(1f, pct))));
    }

    /** Scale an existing colour's own alpha (used to fade a whole element in). */
    public static int fade(int argb, float pct) {
        int a = (argb >>> 24) & 0xFF;
        return alpha(argb, (int) (a * Math.max(0f, Math.min(1f, pct))));
    }

    public static int accent() { return ThemeManager.getMainColor(); }
    public static int accentAlt() { return ThemeManager.getGradientColor(); }
    public static int textPrimary() { return ThemeManager.getTextPrimary(); }
    public static int textSecondary() { return ThemeManager.getTextSecondary(); }
    public static int textMuted() { return ThemeManager.getTextMuted(); }

    /** Blend between two ARGB colours. */
    /**
     * Relative luminance of a colour, 0 (black) to 1 (white), using the sRGB coefficients.
     */
    public static float luminance(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    /**
     * A text/glyph colour guaranteed to be legible on top of {@code background}.
     * <p>
     * Themes carry a "white" and a "black", but light themes swap them, so neither is reliably the
     * contrasting colour for an arbitrary surface. Mono's accent is pure black and its "white" is
     * also black, which is why the selected sidebar pill rendered black text on a black pill and
     * disappeared; Cloud had the same problem with its icons. Choosing by luminance removes the
     * guesswork - it is correct for every theme, including ones added later.
     */
    public static int readableOn(int background) {
        return luminance(background) > 0.55f ? 0xFF15161A : 0xFFF5F6F8;
    }

    public static int mix(int from, int to, float pct) {
        return RenderUtils.interpolateColoursInt(from, to, Math.max(0f, Math.min(1f, pct)));
    }

    // ---------------------------------------------------------------
    //  Elevation
    //
    //  Three named levels instead of every call site inventing a spread. Each
    //  routes through the active GuiStyle preset, so one preset switch still governs the
    //  whole GUI from one place.
    // ---------------------------------------------------------------

    public enum Elevation {
        /** Flush with its parent - rows, list items. */
        FLAT(0f, 0, 0),
        /** Sits on the panel - cards, pills, inputs. */
        RAISED(0.45f, 130, 20),
        /** Floats above everything - dropdown popups, dialogs, drag ghosts. */
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

    /**
     * The workhorse: one rounded surface with its shadow, fill, glass film and edge rim, in the
     * right order. Every panel, card, pill and popup in the GUI is drawn with this so they share a
     * single visual language and respond together to the theme and depth settings.
     *
     * @param fill      base ARGB fill; it is glassified so the backdrop shows through when Liquid
     *                  Glass is on
     * @param intensity 0..1 multiplier on shadow/rim, so a component can fade its own elevation in
     *                  and out with a hover or open animation
     */
    public static void surface(float x1, float y1, float x2, float y2, float radius,
                               int fill, Elevation elevation, float intensity) {
        if (x2 <= x1 || y2 <= y1)
            return;

        intensity = Math.max(0f, Math.min(1f, intensity));
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

    /** A translucent wash used for hover feedback on rows and list items. */
    public static void hoverWash(float x1, float y1, float x2, float y2, float radius, float pct) {
        if (pct <= 0.01f)
            return;
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, radius,
                alpha(ThemeManager.getModuleHover(), (int) (26 * pct)));
    }

    /**
     * The accent marker that runs down the left edge of an active card or row. Rounded only on the
     * left so it reads as part of the card rather than a floating bar.
     */
    public static void accentBar(float x1, float y1, float x2, float y2, float radius, float pct) {
        if (pct <= 0.01f)
            return;
        DrawUtils.drawGradientRoundedRect(x1, y1, x2, y2, radius,
                alpha(accent(), (int) (220 * pct)), alpha(accent(), (int) (220 * pct)),
                alpha(accentAlt(), (int) (220 * pct)), alpha(accentAlt(), (int) (220 * pct)));
    }

    /** Hairline divider. Deliberately sub-pixel-thin so it reads as a seam, not a border. */
    public static void divider(float x1, float y, float x2, float pct) {
        if (pct <= 0.01f)
            return;
        DrawUtils.drawRect(x1, y, x2, y + 0.75f, fade(ThemeManager.getSeparator(), pct));
    }

    /**
     * Disclosure chevron. {@code openPct} morphs it from pointing right (closed) to pointing down
     * (open) by interpolating the three points of the shape rather than rotating a matrix - at
     * these sizes a rotation lands vertices on half-pixels and the arms go soft.
     */
    public static void chevron(float cx, float cy, float size, float thickness, int color, float openPct) {
        float p = Math.max(0f, Math.min(1f, openPct));
        float h = size / 2f;

        // closed ">"        : A(-h/2,-h)  B(h/2,0)   C(-h/2,h)
        // open   "v"        : A(-h,-h/2)  B(0,h/2)   C(h,-h/2)
        float ax = lerp(-h / 2f, -h, p),  ay = lerp(-h, -h / 2f, p);
        float bx = lerp(h / 2f, 0f, p),   by = lerp(0f, h / 2f, p);
        float ccx = lerp(-h / 2f, h, p),  ccy = lerp(h, -h / 2f, p);

        line(cx + ax, cy + ay, cx + bx, cy + by, thickness, color);
        line(cx + bx, cy + by, cx + ccx, cy + ccy, thickness, color);
    }

    private static float lerp(float from, float to, float pct) {
        return from + (to - from) * pct;
    }

    /** Thick line segment drawn as a quad - GL_LINES ignores width reliably across drivers. */
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

    /** Tick mark, used to flag the selected item in a dropdown. */
    public static void check(float cx, float cy, float size, float thickness, int color) {
        float h = size / 2f;
        line(cx - h, cy, cx - h * 0.15f, cy + h * 0.75f, thickness, color);
        line(cx - h * 0.15f, cy + h * 0.75f, cx + h, cy - h * 0.7f, thickness, color);
    }

    /**
     * Rounded scroll indicator that fades out when there is nothing to scroll. Drawn on the content
     * edge rather than in its own gutter so it costs no horizontal space.
     */
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

    /**
     * Small rounded label used for keybinds and slider values. Returns the width it occupied so
     * callers can lay out to the left of it without measuring twice.
     */
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

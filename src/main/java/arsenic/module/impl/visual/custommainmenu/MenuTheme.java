package arsenic.module.impl.visual.custommainmenu;

import arsenic.gui.themes.ThemeManager;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.QuadBatch;
import arsenic.utils.timer.FrameClock;
import arsenic.utils.timer.HoverAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The look of the client's menus: the ocean (or Element 33) scene as the backdrop - the full scene
 * when there is no world behind the screen, a tinted veil with ambient effects when there is - and
 * the bordered pill shape the buttons and panels use. Every vanilla screen picks this up too (see
 * MixinScreen and the widget mixins): its backdrop, buttons, sliders and lists, and the one shared
 * scene keeps the fish where they were when you move between screens.
 */
public final class MenuTheme {

    private static final OceanScene SCENE = new OceanScene();
    private static final ElementScene ELEMENT_SCENE = new ElementScene();
    private static final FrameClock CLOCK = new FrameClock();
    private static float parX, parY;
    /** Per-widget hover animation. */
    private static final Map<AbstractWidget, HoverAnimation> HOVER = new WeakHashMap<>();

    private MenuTheme() {}

    /** A click on the backdrop, passed to whichever scene is showing. */
    public static boolean click(float x, float y) {
        if (arsenic.gui.click.GuiStyle.element()) return ELEMENT_SCENE.click(x, y);
        return SCENE.click(x, y);
    }

    public static OceanScene scene() {
        return SCENE;
    }

    public static float parX() {
        return parX;
    }

    public static float parY() {
        return parY;
    }

    // ---- colours ----------------------------------------------------------------------------------

    public static int main() {
        return ThemeManager.getMainColor() & 0xFFFFFF;
    }

    public static int bg() {
        return ThemeManager.getBlack() & 0xFFFFFF;
    }

    public static int accent(int alpha) {
        return ColorUtils.withAlpha(main(), alpha);
    }

    public static int ink(int alpha) {
        return ColorUtils.withAlpha(0xFFFFFF, alpha);
    }

    // ---- backdrop ---------------------------------------------------------------------------------

    public static void drawBackground(int w, int h, int mouseX, int mouseY, float fade) {
        Minecraft mc = Minecraft.getInstance();
        float dt = CLOCK.tick();
        parX = FrameClock.approach(parX, mouseX / (float) Math.max(1, w) - 0.5f, 4f, dt);
        parY = FrameClock.approach(parY, mouseY / (float) Math.max(1, h) - 0.5f, 4f, dt);

        int main = main(), bg = bg();
        if (arsenic.gui.click.GuiStyle.element()) {
            // Element 33: theme-coloured throughout, so the gradient is just the theme's background
            // warming toward its accent near the floor
            ELEMENT_SCENE.resize(w, h);
            ELEMENT_SCENE.update(dt, mouseX, mouseY);
            if (mc.level == null) {
                DrawUtils.drawVerticalGradient(0, 0, w, h, 0xFF000000 | ColorUtils.mixRgb(bg, main, 0.04f),
                        0xFF000000 | ColorUtils.mixRgb(bg, main, 0.16f));
                ELEMENT_SCENE.draw(main, bg, parX, parY, fade);
            } else {
                DrawUtils.drawVerticalGradient(0, 0, w, h, 0xB8000000 | ColorUtils.mixRgb(bg, main, 0.10f),
                        0xE8000000 | ColorUtils.mixRgb(bg, main, 0.04f));
                ELEMENT_SCENE.drawAmbient(main, bg, parX, parY, fade);
            }
            return;
        }
        SCENE.resize(w, h);
        if (mc.level == null) {
            int water = ColorUtils.mixRgb(bg, main, 0.5f);
            DrawUtils.drawVerticalGradient(0, 0, w, h, 0xFF000000 | ColorUtils.mixRgb(bg, water, 0.55f), 0xFF000000 | ColorUtils.mixRgb(bg, main, 0.04f));
            SCENE.update(dt, mouseX, mouseY);
            SCENE.draw(main, bg, parX, parY, fade);
        } else {
            // the world stays visible underneath, tinted like deep water
            DrawUtils.drawVerticalGradient(0, 0, w, h, 0xB0000000 | ColorUtils.mixRgb(bg, main, 0.22f),
                    0xE0000000 | ColorUtils.mixRgb(bg, main, 0.03f));
            SCENE.update(dt, mouseX, mouseY);
            SCENE.drawAmbient(main, bg, parX, parY, fade);
        }
    }

    /**
     * A rounded rectangle with a border, built from ONE set of vertices so the fill and the outline
     * cannot disagree: the outline is a ring between the outer curve and the same curve inset by the
     * border width, and the fill is the shape inside that inset curve. They share every vertex along
     * the seam, so there is no gap or overlap, and the outer edge has exactly the curvature asked
     * for. A half-pixel feather just outside the edge smooths it without moving it.
     *
     * @param radius corner radius in GUI units; half the height makes a full pill
     */
    public static void drawPill(float x1, float y1, float x2, float y2, float radius, float border, int fill, int borderColour) {
        float r = Math.min(radius, Math.min(x2 - x1, y2 - y1) / 2f);
        float ri = Math.max(0f, r - border);
        float feather = 0.6f / Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        final int seg = 14;                              // steps per quarter turn
        int n = (seg + 1) * 4;
        float[] ox = new float[n], oy = new float[n], ix = new float[n], iy = new float[n], fx = new float[n], fy = new float[n];
        // corner centres and the angle each corner starts at (screen space, y down): TL, TR, BR, BL
        float[] cx = {x1 + r, x2 - r, x2 - r, x1 + r};
        float[] cy = {y1 + r, y1 + r, y2 - r, y2 - r};
        float[] start = {180f, 270f, 0f, 90f};
        int k = 0;
        for (int c = 0; c < 4; c++) {
            for (int s = 0; s <= seg; s++, k++) {
                double a = Math.toRadians(start[c] + 90f * s / seg);
                float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
                ox[k] = cx[c] + cos * r;
                oy[k] = cy[c] + sin * r;
                ix[k] = cx[c] + cos * ri;
                iy[k] = cy[c] + sin * ri;
                fx[k] = cx[c] + cos * (r + feather);
                fy[k] = cy[c] + sin * (r + feather);
            }
        }
        QuadBatch batch = new QuadBatch();
        int faded = borderColour & 0xFFFFFF;
        float mx = (x1 + x2) / 2f, my = (y1 + y2) / 2f;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            // the fill: a fan over the inset shape
            batch.triangle(mx, my, fill, ix[i], iy[i], fill, ix[j], iy[j], fill);
            // the ring between the outer curve and the inset curve
            batch.quad(ox[i], oy[i], ix[i], iy[i], ix[j], iy[j], ox[j], oy[j], borderColour);
            // the feather, fading the border colour to nothing just outside the outer edge
            batch.quad(ox[i], oy[i], borderColour, fx[i], fy[i], faded, fx[j], fy[j], faded, ox[j], oy[j], borderColour);
        }
        batch.submit();
    }

    // ---- lists ------------------------------------------------------------------------------------

    /** The area behind a scrolling list: a dark, slightly tinted pane the ocean shows through. */
    public static void drawListPane(int left, int top, int right, int bottom) {
        DrawUtils.drawVerticalGradient(left, top, right, bottom, 0x70000000 | ColorUtils.mixRgb(0x000000, main(), 0.10f),
                0x90000000 | ColorUtils.mixRgb(0x000000, main(), 0.04f));
    }

    /** The edges above and below a list: an accent hairline over a deep-water band. */
    public static void drawListEdge(int left, int right, int y, boolean top) {
        int deep = 0xC0000000 | ColorUtils.mixRgb(bg(), 0x000000, 0.45f);
        DrawUtils.drawRect(left, y, right, y + 2, deep);
        float edge = top ? y + 1 : y;
        DrawUtils.drawRect(left, edge, right, edge + 1, accent(150));
    }

    // ---- buttons ----------------------------------------------------------------------------------

    /** A vanilla button (or a slider's track) as a pill with an animated hover. */
    public static void drawButton(AbstractWidget b, boolean hovered) {
        int bw = b.getWidth(), bh = b.getHeight();
        float hover = HOVER.computeIfAbsent(b, k -> new HoverAnimation()).update(hovered && b.active);
        float x1 = b.getX(), y1 = b.getY(), x2 = x1 + bw, y2 = y1 + bh;
        int baseFill = (b.active ? 125 : 75) << 24 | ColorUtils.mixRgb(0x000000, main(), 0.12f);
        int fill = ColorUtils.mixArgb(baseFill, accent(105), hover);
        drawPill(x1, y1, x2, y2, Math.min(bh, bw) / 2f, 1f, fill,
                ColorUtils.mixArgb(ink(b.active ? 55 : 28), accent(235), hover));
    }

    /**
     * A vanilla slider's filled track and knob. Plain rectangles: rounded shapes break down at this
     * size and draw stray brackets.
     */
    public static void drawSlider(int left, int knobX, int y, int height) {
        // the filled part of the track, kept inside the pill's rounded ends
        int fillFrom = left + 8, fillTo = knobX + 4;
        if (fillTo > fillFrom)
            DrawUtils.drawRect(fillFrom, y + 5, fillTo, y + height - 5, accent(110));
        // the knob: an accent outline around a bright face
        DrawUtils.drawRect(knobX, y + 2, knobX + 8, y + height - 2, accent(255));
        DrawUtils.drawRect(knobX + 1, y + 3, knobX + 7, y + height - 3, ink(245));
    }
}

package arsenic.module.impl.visual.custommainmenu;

import arsenic.gui.themes.ThemeManager;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.java.MathUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.timer.FrameClock;
import arsenic.utils.timer.HoverAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.input.Mouse;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * One look for every screen the game draws: the ocean as the backdrop (the full scene when there is
 * no world behind the screen, a tinted veil with light rays and bubbles when there is), pill buttons
 * with an animated hover, and solid deep-water bars for the headers and footers of list screens.
 * The vanilla drawing is replaced through mixins, so every screen picks it up, and the one shared
 * scene keeps the fish where they were when you move between screens.
 */
public final class MenuTheme {

    private static final OceanScene SCENE = new OceanScene();
    private static final ElementScene ELEMENT_SCENE = new ElementScene();
    private static final FrameClock CLOCK = new FrameClock();
    private static float parX, parY;
    /** Per-button hover animation. */
    private static final Map<GuiButton, HoverAnimation> HOVER = new WeakHashMap<>();

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

    /** The backdrop for a vanilla screen, with the mouse read straight from LWJGL. */
    public static void drawBackground(int w, int h) {
        Minecraft mc = Minecraft.getMinecraft();
        int mx = mc.displayWidth == 0 ? 0 : Mouse.getX() * w / mc.displayWidth;
        int my = mc.displayHeight == 0 ? 0 : h - Mouse.getY() * h / mc.displayHeight - 1;
        drawBackground(w, h, mx, my, 1f);
    }

    public static void drawBackground(int w, int h, int mouseX, int mouseY, float fade) {
        Minecraft mc = Minecraft.getMinecraft();
        float dt = CLOCK.tick();
        parX = FrameClock.approach(parX, mouseX / (float) Math.max(1, w) - 0.5f, 4f, dt);
        parY = FrameClock.approach(parY, mouseY / (float) Math.max(1, h) - 0.5f, 4f, dt);

        int main = main(), bg = bg();
        if (arsenic.gui.click.GuiStyle.element()) {
            // Element 33: theme-coloured throughout, so the gradient is just the theme's background
            // warming toward its accent near the floor
            ELEMENT_SCENE.resize(w, h);
            ELEMENT_SCENE.update(dt, mouseX, mouseY);
            if (mc.theWorld == null) {
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
        if (mc.theWorld == null) {
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

    // ---- lists ------------------------------------------------------------------------------------

    /** The area behind a scrolling list: a dark, slightly tinted pane the ocean shows through. */
    public static void drawListPane(int left, int top, int right, int bottom) {
        GlStateManager.disableTexture2D();
        DrawUtils.drawVerticalGradient(left, top, right, bottom, 0x70000000 | ColorUtils.mixRgb(0x000000, main(), 0.10f), 0x90000000 | ColorUtils.mixRgb(0x000000, main(), 0.04f));
        GlStateManager.enableTexture2D();
    }

    /** The bars above and below a list, which also hide rows scrolled past the edge, so they are near opaque. */
    public static void drawListBar(int left, int right, int startY, int endY) {
        int deep = 0xF8000000 | ColorUtils.mixRgb(bg(), 0x000000, 0.45f);
        DrawUtils.drawVerticalGradient(left, startY, right, endY, deep, deep);
        // an accent hairline on the edge facing the list
        float edge = startY == 0 ? endY - 1 : startY;
        GlStateManager.disableTexture2D();
        DrawUtils.drawRect(left, edge, right, edge + 1, accent(150));
        GlStateManager.enableTexture2D();
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
        float feather = 0.6f / Math.max(1, new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor());
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
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        // the fill: a fan over the inset shape
        wr.begin(6, DefaultVertexFormats.POSITION_COLOR);
        colour(wr.pos((x1 + x2) / 2f, (y1 + y2) / 2f, 0), fill);
        for (int i = 0; i <= n; i++)
            colour(wr.pos(ix[i % n], iy[i % n], 0), fill);
        tess.draw();

        // the ring between the outer curve and the inset curve
        wr.begin(5, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= n; i++) {
            colour(wr.pos(ox[i % n], oy[i % n], 0), borderColour);
            colour(wr.pos(ix[i % n], iy[i % n], 0), borderColour);
        }
        tess.draw();

        // the feather, fading the border colour to nothing just outside the outer edge
        wr.begin(5, DefaultVertexFormats.POSITION_COLOR);
        int faded = borderColour & 0xFFFFFF;
        for (int i = 0; i <= n; i++) {
            colour(wr.pos(ox[i % n], oy[i % n], 0), borderColour);
            colour(wr.pos(fx[i % n], fy[i % n], 0), faded);
        }
        tess.draw();

        GlStateManager.shadeModel(7424);
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    private static void colour(WorldRenderer wr, int argb) {
        wr.color(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24).endVertex();
    }
    // ---- buttons ----------------------------------------------------------------------------------

    /** Draws the button and returns whether the mouse is over it. */
    public static boolean drawButton(GuiButton b, Minecraft mc, int mx, int my) {
        boolean over = MathUtils.insideSized(mx, my, b.xPosition, b.yPosition, b.width, b.height);
        float hover = HOVER.computeIfAbsent(b, k -> new HoverAnimation()).update(over && b.enabled);

        float x1 = b.xPosition, y1 = b.yPosition, x2 = x1 + b.width, y2 = y1 + b.height;
        int baseFill = (b.enabled ? 125 : 75) << 24 | ColorUtils.mixRgb(0x000000, main(), 0.12f);
        int fill = ColorUtils.mixArgb(baseFill, accent(105), hover);
        drawPill(x1, y1, x2, y2, Math.min(b.height, b.width) / 2f, 1f, fill,
                ColorUtils.mixArgb(ink(b.enabled ? 55 : 28), accent(235), hover));

        int textColour = !b.enabled ? ink(110) : ColorUtils.mixArgb(ink(225), ink(255), hover);
        int tw = mc.fontRendererObj.getStringWidth(b.displayString);
        mc.fontRendererObj.drawStringWithShadow(b.displayString, x1 + (b.width - tw) / 2f, y1 + (b.height - 8) / 2f, textColour);
        return over;
    }

    /**
     * A vanilla slider's filled track and knob. Plain rectangles: the rounded-rect shader breaks down
     * at this size and draws stray brackets.
     */
    public static void drawSlider(int left, int knobX, int y, int height) {
        // the filled part of the track, kept inside the pill's rounded ends
        int fillFrom = left + 8, fillTo = knobX + 4;
        if (fillTo > fillFrom)
            Gui.drawRect(fillFrom, y + 5, fillTo, y + height - 5, accent(110));
        // the knob: an accent outline around a bright face
        Gui.drawRect(knobX, y + 2, knobX + 8, y + height - 2, accent(255));
        Gui.drawRect(knobX + 1, y + 3, knobX + 7, y + height - 3, ink(245));
    }
}

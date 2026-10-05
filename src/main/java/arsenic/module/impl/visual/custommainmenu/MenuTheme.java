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
    private static final FrameClock CLOCK = new FrameClock();
    private static float parX, parY;
    /** Per-button hover animation. */
    private static final Map<GuiButton, HoverAnimation> HOVER = new WeakHashMap<>();

    private MenuTheme() {}

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

    // ---- buttons ----------------------------------------------------------------------------------

    /** Draws the button and returns whether the mouse is over it. */
    public static boolean drawButton(GuiButton b, Minecraft mc, int mx, int my) {
        boolean over = MathUtils.insideSized(mx, my, b.xPosition, b.yPosition, b.width, b.height);
        float hover = HOVER.computeIfAbsent(b, k -> new HoverAnimation()).update(over && b.enabled);

        float x1 = b.xPosition, y1 = b.yPosition, x2 = x1 + b.width, y2 = y1 + b.height;
        int baseFill = (b.enabled ? 125 : 75) << 24 | ColorUtils.mixRgb(0x000000, main(), 0.12f);
        int fill = ColorUtils.mixArgb(baseFill, accent(105), hover);
        float radius = Math.min(b.height, b.width) / 2f;
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, radius, fill);
        DrawUtils.drawRoundedOutline(x1, y1, x2, y2, radius, 1f, ColorUtils.mixArgb(ink(b.enabled ? 45 : 25), accent(235), hover));

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

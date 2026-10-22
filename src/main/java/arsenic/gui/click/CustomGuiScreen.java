package arsenic.gui.click;

import arsenic.utils.render.GuiCanvas;
import arsenic.utils.render.RenderContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A screen laid out in a fixed coordinate space, independent of the GUI scale setting.
 * <p>
 * Layout and hit testing happen in "design units" of {@link #scale} framebuffer pixels each, so the
 * ClickGUI looks identical on every GUI scale. 1.8 did this by swapping the projection matrix;
 * here the design space is mapped onto the vanilla GUI space by scaling the pose, and mouse
 * coordinates are mapped back the other way. Subclasses implement the {@code drawScr} /
 * {@code mouseClick} / ... hooks and only ever see design-unit coordinates.
 * <p>
 * Screens that return true from {@link #fixedCanvas()} lay out on the {@link GuiCanvas} instead: the design
 * units are canvas units, scaled to fit the window and centred, so they look the same on every monitor.
 */
public class CustomGuiScreen extends Screen {

    public float scale;
    public float curWidth = 0;
    public float curHeight = 0;

    public CustomGuiScreen() {
        super(Component.empty());
        this.scale = 2;
    }

    public void doInit() {}

    public void drawScr(int mouseX, int mouseY, float partialTicks) {}

    public void mouseClick(int mouseX, int mouseY, int mouseButton) {}

    public void mouseRelease(int mouseX, int mouseY, int state) {}

    public void mouseDrag(int mouseX, int mouseY, int mouseButton) {}

    /** Wheel movement, positive away from the user - one notch is 1. */
    public void mouseScroll(int mouseX, int mouseY, double amount) {}

    /** A key went down. {@code key} is an SDL scancode, as in {@link com.mojang.blaze3d.platform.InputConstants}. */
    public boolean keyTyped(int key, KeyEvent event) {
        return false;
    }

    /** A character was typed. Text input arrives here, separately from key presses. */
    public boolean charTyped(char c) {
        return false;
    }

    /** Screens that lay out on the fixed {@link GuiCanvas} override this, so they look the same on every monitor. */
    protected boolean fixedCanvas() {
        return false;
    }

    /** Window pixels per design unit. */
    private float pixelsPerUnit() {
        return fixedCanvas() ? GuiCanvas.scale() : scale;
    }

    /** Design units per vanilla GUI unit. */
    protected float factor() {
        return pixelsPerUnit() / (float) minecraft.getWindow().getGuiScale();
    }

    @Override
    protected final void init() {
        if (fixedCanvas()) {
            scale = GuiCanvas.scale();
            width = (int) GuiCanvas.WIDTH;
            height = (int) GuiCanvas.HEIGHT;
        } else {
            float f = factor();
            width = Math.round(width / f);
            height = Math.round(height / f);
        }
        this.doInit();
    }

    @Override
    public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        boolean canvas = fixedCanvas();
        if (canvas) {
            GuiCanvas.setActive(true);
            scale = GuiCanvas.scale();
        }
        float f = factor();
        float guiScale = (float) minecraft.getWindow().getGuiScale();
        curWidth = minecraft.getWindow().getWidth() / pixelsPerUnit();
        curHeight = minecraft.getWindow().getHeight() / pixelsPerUnit();
        graphics.pose().pushMatrix();
        if (canvas)
            graphics.pose().translate(GuiCanvas.offsetX() / guiScale, GuiCanvas.offsetY() / guiScale);
        graphics.pose().scale(f, f);
        try (RenderContext ignored = RenderContext.begin(graphics)) {
            this.drawScr(getRealMouseX(), getRealMouseY(), partialTicks);
        } finally {
            graphics.pose().popMatrix();
            if (canvas)
                GuiCanvas.setActive(false);
        }
    }

    /** The ClickGUI draws its own backdrop; vanilla's dim/blur would sit on top of the game view. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.mouseClick(getRealMouseX(event.x()), getRealMouseY(event.y()), arsenic.utils.io.Keys.fromSdlButton(event.button()));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.mouseRelease(getRealMouseX(event.x()), getRealMouseY(event.y()), arsenic.utils.io.Keys.fromSdlButton(event.button()));
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        this.mouseDrag(getRealMouseX(event.x()), getRealMouseY(event.y()), arsenic.utils.io.Keys.fromSdlButton(event.button()));
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        this.mouseScroll(getRealMouseX(x), getRealMouseY(y), scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (keyTyped(event.key(), event))
            return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        String s = event.codepointAsString();
        boolean handled = false;
        for (int i = 0; i < s.length(); i++)
            handled |= charTyped(s.charAt(i));
        return handled;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int toDesign(double guiCoord) {
        return (int) (guiCoord / factor());
    }

    public int getRealMouseX(double guiX) {
        if (fixedCanvas())
            return (int) GuiCanvas.unitX((float) (guiX * minecraft.getWindow().getGuiScale()));
        return toDesign(guiX);
    }

    public int getRealMouseY(double guiY) {
        if (fixedCanvas())
            return (int) GuiCanvas.unitY((float) (guiY * minecraft.getWindow().getGuiScale()));
        return toDesign(guiY);
    }

    /** Pointer position in design units, for code that polls instead of handling events. */
    public int getRealMouseX() {
        return getRealMouseX(minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()));
    }

    public int getRealMouseY() {
        return getRealMouseY(minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()));
    }
}

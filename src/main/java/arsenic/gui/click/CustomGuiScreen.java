package arsenic.gui.click;

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

    /** Design units per vanilla GUI unit. */
    protected float factor() {
        return scale / (float) minecraft.getWindow().getGuiScale();
    }

    @Override
    protected final void init() {
        float f = factor();
        width = Math.round(width / f);
        height = Math.round(height / f);
        this.doInit();
    }

    @Override
    public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        float f = factor();
        curWidth = minecraft.getWindow().getWidth() / scale;
        curHeight = minecraft.getWindow().getHeight() / scale;
        graphics.pose().pushMatrix();
        graphics.pose().scale(f, f);
        try (RenderContext ignored = RenderContext.begin(graphics)) {
            this.drawScr(toDesign(mouseX), toDesign(mouseY), partialTicks);
        } finally {
            graphics.pose().popMatrix();
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
        return toDesign(guiX);
    }

    public int getRealMouseY(double guiY) {
        return toDesign(guiY);
    }

    /** Pointer position in design units, for code that polls instead of handling events. */
    public int getRealMouseX() {
        return toDesign(minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()));
    }

    public int getRealMouseY() {
        return toDesign(minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()));
    }
}

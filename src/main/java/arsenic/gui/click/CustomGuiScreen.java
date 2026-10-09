package arsenic.gui.click;

import java.io.IOException;

import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import arsenic.utils.render.GuiCanvas;

public class CustomGuiScreen extends GuiScreen {
    public float scale;
    public float curWidth = 0;
    public float curHeight = 0;

    public CustomGuiScreen() {
        this.scale = 2;
    }



    public void doInit() {}

    public void drawScr(int mouseX, int mouseY, float partialTicks) {}

    public void mouseClick(int mouseX, int mouseY, int mouseButton) {}

    public void mouseRelease(int mouseX, int mouseY, int state) {}

    /** Screens that lay out on the fixed {@link GuiCanvas} override this, so they look the same on every monitor. */
    protected boolean fixedCanvas() {
        return false;
    }

    @Override
    public final void initGui() {
        this.doInit();
        if (fixedCanvas()) {
            width = (int) GuiCanvas.WIDTH;
            height = (int) GuiCanvas.HEIGHT;
        } else {
            int sf = new ScaledResolution(mc).getScaleFactor();
            height = (int) ((height * sf) / scale);
            width = (int) ((width * sf) / scale);
        }
        super.initGui();
    }

    @Override
    public final void drawScreen(int mouseX, int mouseY, float partialTicks) {
        boolean canvas = fixedCanvas();
        if (canvas)
            GuiCanvas.setActive(true);
        try {
            rescaleGui();
            curWidth = mc.displayWidth / scale;
            curHeight = mc.displayHeight / scale;

            this.drawScr(this.getRealMouseX(), this.getRealMouseY(), partialTicks);

            rescaleMC();
            super.drawScreen(mouseX, mouseY, partialTicks);
        } finally {
            if (canvas)
                GuiCanvas.setActive(false);
        }
    }

    @Override
    protected final void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        this.mouseClick(this.getRealMouseX(), this.getRealMouseY(), mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected final void mouseReleased(int mouseX, int mouseY, int state) {
        this.mouseRelease(this.getRealMouseX(), this.getRealMouseY(), state);
        super.mouseReleased(mouseX, mouseY, state);
    }

    public int getRealMouseX() {
        if (fixedCanvas())
            return (int) GuiCanvas.unitX(Mouse.getX());
        return (int) ((Mouse.getX() * (mc.displayWidth / scale)) / mc.displayWidth);
    }

    public int getRealMouseY() {
        if (fixedCanvas())
            return (int) GuiCanvas.unitY(mc.displayHeight - Mouse.getY());
        float scaleHeight = (mc.displayHeight / scale);
        return (int) (scaleHeight - (Mouse.getY() * scaleHeight) / mc.displayHeight);
    }

    /** Sets the projection for this screen's own units: the canvas when it is fixed, otherwise the usual scale. */
    public void rescaleGui() {
        if (!fixedCanvas()) {
            rescale(this.scale);
            return;
        }
        float s = GuiCanvas.scale();
        this.scale = s;
        double left = -GuiCanvas.offsetX() / s;
        double top = -GuiCanvas.offsetY() / s;
        rescaleBounds(left, left + mc.displayWidth / s, top + mc.displayHeight / s, top);
    }

    public void rescale(double factor) {
        rescale(mc.displayWidth / factor, mc.displayHeight / factor);
    }

    public void rescaleMC() {
        ScaledResolution resolution = new ScaledResolution(mc);
        rescale(mc.displayWidth / resolution.getScaleFactor(), mc.displayHeight / resolution.getScaleFactor());
    }

    public void rescale(double width, double height) {
        rescaleBounds(0.0D, width, height, 0.0D);
    }

    private void rescaleBounds(double left, double right, double bottom, double top) {
        GlStateManager.clear(256);
        GlStateManager.matrixMode(5889);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(left, right, bottom, top, 1000.0D, 3000.0D);
        GlStateManager.matrixMode(5888);
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0F, 0.0F, -2000.0F);
    }
}

package arsenic.gui;

import arsenic.main.Arsenic;
import arsenic.utils.render.NVGWrapper;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

import static org.lwjgl.opengl.GL11.*;

public abstract class NVGScreen extends GuiScreen {

    public abstract void init();
    public abstract void render(float ticks, float mouseX, float mouseY);
    public abstract void click(int button, float mouseX, float mouseY);
    public abstract void release(int button, float mouseX, float mouseY);

    protected int dWidth = 0, dHeight = 0;
    protected NVGWrapper ui = Arsenic.getInstance().getNvg();

    @Override
    public void initGui() {
        dWidth = Display.getWidth();
        dHeight = Display.getHeight();
        super.initGui();
        init();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float ticks) {
        dWidth = Display.getWidth();
        dHeight = Display.getHeight();
        GL11.glDisable(GL_ALPHA_TEST);
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        ui.beginFrame();
        render(ticks, Mouse.getX(), Display.getHeight() - Mouse.getY());
        ui.endFrame();
        glPopAttrib();
        GL11.glEnable(GL_ALPHA_TEST);
        super.drawScreen(mouseX, mouseY, ticks);
    }

    @Override
    protected void mouseClicked(int p_73864_0_, int mouseX, int mouseY) {
        click(mouseY, Mouse.getX(), Display.getHeight() - Mouse.getY());
        try {
            super.mouseClicked(p_73864_0_, mouseX, mouseY);
        } catch (IOException e) {}
    }

    @Override
    protected void mouseReleased(int p_146286_0_, int mouseX, int mouseY) {
        release(mouseY, Mouse.getX(), Display.getHeight() - Mouse.getY());
        super.mouseReleased(p_146286_0_, mouseX, mouseY);
    }
}

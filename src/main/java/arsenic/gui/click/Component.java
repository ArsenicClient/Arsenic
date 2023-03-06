package arsenic.gui.click;

import org.lwjgl.opengl.GL11;

import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import net.minecraft.client.renderer.GlStateManager;

public abstract class Component implements IContainable {

    protected int x1, y1, x2, y2, width, height, expandY, expandX;


    // returns height
    public int updateComponent(PosInfo pi, RenderInfo ri) {

        width = getWidth(ri.getGuiScreen().width);
        height = getHeight(ri.getGuiScreen().height);
        x1 = pi.getX();
        x2 = x1 + width;
        y1 = pi.getY();
        y2 = y1 + height;

        mouseUpdate(ri.getMouseX(), ri.getMouseY());

        GL11.glPushMatrix();
        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        int r = drawComponent(ri);
        GlStateManager.popMatrix();
        GlStateManager.popAttrib();
        GL11.glPopMatrix();

        return r;
    }

    public void handleClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseX > (x1) && mouseX < (x2 + expandX) && mouseY > (y1) && mouseY < (y2 + expandY)) {
            clickComponent(mouseX, mouseY, mouseButton);
            if (this instanceof IContainer) {
                ((IContainer) this).getContents()
                        .forEach(component -> ((Component) component).handleClick(mouseX, mouseY, mouseButton));
            }
        }
    }

    protected abstract int drawComponent(RenderInfo ri);

    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {}

    public void mouseUpdate(int x, int y) {

    }

    protected int getHeight(int i) {
        return 5 * (i/100);
    }

    protected int getWidth(int i) {
        return 5 * (i/100);
    }

}

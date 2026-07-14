package arsenic.gui.click;

import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.utils.render.RenderUtils;
import org.lwjgl.opengl.GL11;

import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;

public abstract class Component implements IContainable {

    protected float x1, y1, x2, y2, width, height, expandY, expandX, midPointY;

    // returns height
    public float updateComponent(PosInfo pi, RenderInfo ri) {
        width = getWidth(ri.getGuiScreen().width);
        height = getHeight(ri.getGuiScreen().height);
        x1 = pi.getX();
        x2 = x1 + width;
        y1 = pi.getY();
        y2 = y1 + height;
        midPointY = y1 + (height/2f);

        mouseUpdate(ri.getMouseX(), ri.getMouseY());

        GL11.glPushMatrix();
        RenderUtils.resetColorText();
        float r = drawComponent(ri);
        GL11.glPopMatrix();

        return r;
    }

    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseX < x1 || mouseY < y1)
            return false;
        if(mouseX < x2 && mouseY < y2) {
            clickComponent(mouseX, mouseY, mouseButton);
            playClickSound();
            return true;
        } else if(mouseX < (x2 + expandX) && mouseY < (y2 + expandY)) {
            if (this instanceof IContainer) {
                for(Component component : ((IContainer<Component>) this).getContents()) {
                    if(component.handleClick(mouseX, mouseY, mouseButton))
                        return true;
                }
            }
        }
        return false;
    }


    public final void handleRelease(int mouseX, int mouseY, int state) {
        mouseReleased(mouseX, mouseY, state);
        if (this instanceof IContainer) {
            ((IContainer) this).getContents()
                    .forEach(component -> ((Component) component).handleRelease(mouseX, mouseY, state));
        }
    }

    protected boolean isMouseInArea(float mouseX, float mouseY) {
        return mouseX > x1 && mouseY > y1 && mouseX < x2 && mouseY < y2;
    }

    protected int getEnabledColor() {
        return ThemeManager.getMainColor();
    }

    protected int getGradientColor() {
        return ThemeManager.getGradientColor();
    }
    protected int getDisabledColor() {
        return ThemeManager.getBlack();
    }

    protected int getWhite() {
        return ThemeManager.getWhite();
    }

    protected int getDarkerColor() {
        return ThemeManager.getDarkerColor();
    }

    protected abstract float drawComponent(RenderInfo ri);

    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {}

    // Plays a click sound for any component. SoundUtils debounces so nested
    // dispatch (a module row containing a toggle) only makes one sound.
    protected void playClickSound() {
        arsenic.utils.java.SoundUtils.cmajStep();
    }

    public void mouseUpdate(int mouseX, int mouseY) {}

    public void mouseReleased(int mouseX, int mouseY, int state) {}

    public int getHeight(int i) {
        return 5 * (i / 100);
    }

    public int getWidth(int i) {
        return 5 * (i / 100);
    }

}

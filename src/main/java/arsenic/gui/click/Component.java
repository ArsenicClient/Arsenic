package arsenic.gui.click;

import arsenic.utils.render.RenderInfo;

public class Component {

    protected final int WIDTH = 118, HEIGHT = 14;

    public void drawComponent(int x, int y, RenderInfo ri) {}
    public void clickComponent(int x, int y, int mouseX, int mouseY, int mouseButton) {}

    public final int getWidth() {
        return WIDTH;
    }

    public final int getHeight() {
        return HEIGHT;
    }

}

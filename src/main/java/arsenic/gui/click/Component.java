package arsenic.gui.click;

import arsenic.utils.functionalinterfaces.IInt;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.render.DimensionInfo;
import arsenic.utils.render.RenderInfo;

public abstract class Component implements IContainable {

    protected IInt WIDTH = (i) -> 118, HEIGHT = (i) -> 14;

    //useless but makes it look nice imo
    //returns height
    public int updateComponent(DimensionInfo di, RenderInfo ri) {
        mouseUpdate(ri.getMouseX(), ri.getMouseY());
        return drawComponent(di, ri);
    }

    protected abstract int drawComponent(DimensionInfo di, RenderInfo ri);
    protected abstract void clickComponent(int mouseX, int mouseY, int mouseButton);

    public void mouseUpdate(int x, int y) {

    }

    public final int getWidth(int i) {
        return WIDTH.getValue(i);
    }

    public final int getHeight(int i) {
        return HEIGHT.getValue(i);
    }

    public int getWidth() {
        return getHeight(0);
    }

    public int getHeight() {
        return getWidth(0);
    }

}

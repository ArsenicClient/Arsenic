package arsenic.gui.click;

import arsenic.utils.functionalinterfaces.IInt;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.io.MouseButton;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;

public abstract class Component implements IContainable {

    protected int x1, y1, x2, y2, width, height, expandY, expandX;

    //percent * (Height or width)/100
    protected final IInt widthP = (i) -> 5 * (i/100), heightP = (i) -> 5 * (i/100);

    //returns height
    public int updateComponent(PosInfo pi, RenderInfo ri) {

        width = widthP.getValue(ri.getGuiScreen().width);
        height = heightP.getValue(ri.getGuiScreen().height);
        x1 = pi.getX();
        x2 = x1 + width;
        y1 = pi.getY();
        y2 = y1 + height;

        mouseUpdate(ri.getMouseX(), ri.getMouseY());
        return drawComponent(ri);
    }

    public final void handleClick(int mouseX, int mouseY, int mouseButton) {
        MouseButton button = MouseButton.getButton(mouseButton);

        if(mouseX > (x1) && mouseX < (x2 + expandX) && mouseY > (y1) && mouseY < (y2 + expandY)) {
            clickComponent(mouseX, mouseY, mouseButton);
        }
    }

    protected abstract int drawComponent(RenderInfo ri);
    protected abstract void clickComponent(int mouseX, int mouseY, int mouseButton);
    public void mouseUpdate(int x, int y) {

    }

}

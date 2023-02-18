package arsenic.gui.click;

import arsenic.utils.functionalinterfaces.IInt;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;

import javax.swing.text.Position;
import java.awt.*;

public abstract class Component implements IContainable {

    protected int x1, y1, x2, y2, width, height, wholeHeight, wholeY2;

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

    protected abstract int drawComponent(RenderInfo ri);
    protected abstract void clickComponent(int mouseX, int mouseY, int mouseButton);

    public void mouseUpdate(int x, int y) {

    }

}

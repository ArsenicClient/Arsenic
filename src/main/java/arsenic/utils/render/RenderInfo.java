package arsenic.utils.render;

import java.awt.*;

public class RenderInfo {

    private int index = 0;
    private final int mouseX, mouseY;

    public RenderInfo(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    public final int getIndex() {
        return index;
    }

    public final void setIndex(int index) {
        this.index = index;
    }

    public final int getMouseX() {
        return mouseX;
    }

    public final int getMouseY() {
        return mouseY;
    }

    public final Color getLighterColorByLevel(Color color) {
        return getColorByLevel(color, -index);
    }

    public final Color getDarkerColorByLevel(Color color) {
        return getColorByLevel(color, index);
    }

    private Color getColorByLevel(Color color, int level) {
        for (int i = 0; i < level; i++) {
            color = color.darker();
        }
        return color;
    }

}

package arsenic.gui;

import arsenic.main.Arsenic;
import arsenic.utils.render.NVGWrapper;
import lombok.Data;

@Data
public abstract class Comp {
    protected NVGWrapper ui = Arsenic.getInstance().getNvg();
    private float x,y,width,height;
    public Comp(float x,float y,float width,float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    public abstract void render(float mx, float my, float delta);
    public abstract void click(float mx, float my, int button);
    public void keyTyped(char character, int keycode) {}
    public boolean hovered(float mx, float my) {
        return hovered(mx,my,x,y,width,height);
    }

    public boolean hovered(float mx, float my, float x, float y, float width, float height) {
        return mx > x && mx < x + width && my > y && my < y + height;
    }
}

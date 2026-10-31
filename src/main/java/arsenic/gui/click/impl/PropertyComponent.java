package arsenic.gui.click.impl;

import arsenic.gui.click.Component;
import arsenic.gui.click.UITheme;
import arsenic.gui.design.M3;
import arsenic.module.property.Property;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;

public abstract class PropertyComponent<T extends Property> extends Component implements IContainable {

    private final String name;
    protected final T self;

    protected PropertyComponent(T p) {
        self = p;
        name = p.getName();
    }

    protected final float controlX1() {
        return x2 - width * 0.42f;
    }

    protected final float pad() {
        return height * 0.36f;
    }

    @Override
    protected final float drawComponent(RenderInfo ri) {
        if (!self.isVisible())
            return 0f;

        float hover = interactive() ? hoverPct() : 0f;
        if (hover > 0.01f)
            UITheme.hoverWash(x1 - pad() * 0.5f, y1, x2 + pad() * 0.5f, y2,
                    UITheme.radiusChip(height), hover * 0.8f);

        ri.getFr().drawString(fit(ri, name, labelRight() - x1), x1, midPointY,
                M3.mix(M3.onSurfaceVariant(), M3.onSurface(), hover),
                ri.getFr().CENTREY);

        return draw(ri);
    }

    protected abstract float draw(RenderInfo ri);

    /** False for rows that only show text: no hover highlight. */
    protected boolean interactive() {
        return true;
    }

    /** Where the label has to stop so it never runs under the control. */
    protected float labelRight() {
        return controlX1() - pad();
    }

    /** Trims {@code text} with ".." so it fits in {@code room}. */
    protected static String fit(RenderInfo ri, String text, float room) {
        if (room <= 0 || ri.getFr().getWidth(text) <= room)
            return text;
        String s = text;
        while (s.length() > 1 && ri.getFr().getWidth(s + "..") > room)
            s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    @Override
    protected final void clickComponent(int mouseX, int mouseY, int mouseButton) {
        if (self.isVisible())
            click(mouseX, mouseY, mouseButton);
    }

    protected void click(int mouseX, int mouseY, int mouseButton) {}

    public String getName() { return name; }

    /** Set by the enclosing card (or folder) while it draws its settings: how wide a row is. */
    public static float rowWidth = -1f;

    @Override
    public int getWidth(int i) {
        if (!self.isVisible())
            return 0;
        return rowWidth > 0 ? (int) rowWidth : (int) UITheme.space(i, 24);
    }

    @Override
    public int getHeight(int i) {
        return self.isVisible() ? (int) UITheme.space(i, 4.6f) : 0;
    }
}

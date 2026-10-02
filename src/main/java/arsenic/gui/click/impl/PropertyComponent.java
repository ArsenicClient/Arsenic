package arsenic.gui.click.impl;

import arsenic.gui.click.Component;
import arsenic.gui.click.UITheme;
import arsenic.module.property.Property;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;

/**
 * One settings row inside an expanded module card.
 * <p>
 * This class owns the row itself - hover wash, label, vertical rhythm - and hands the subclass only
 * the control area on the right. That split is what makes every property type line up: a slider, a
 * dropdown and a toggle all get their label drawn by the same code at the same baseline, and each
 * one only has to know how to draw itself inside {@link #controlX1()}..{@code x2}.
 * <p>
 * Invisible properties (those gated by {@code @PropertyInfo}) collapse to zero height and stop
 * accepting clicks, exactly as before.
 */
public abstract class PropertyComponent<T extends Property> extends Component implements IContainable {

    private final String name;
    protected final T self;

    protected PropertyComponent(T p) {
        self = p;
        name = p.getName();
    }

    /** Left edge of the control area. The label owns everything to the left of it. */
    protected final float controlX1() {
        return x2 - width * 0.42f;
    }

    /** Row inset used for the label and for any control that needs to breathe off the edge. */
    protected final float pad() {
        return height * 0.36f;
    }

    @Override
    protected final float drawComponent(RenderInfo ri) {
        if (!self.isVisible())
            return 0f;

        float hover = hoverPct();
        if (hover > 0.01f)
            UITheme.hoverWash(x1 - pad() * 0.5f, y1, x2 + pad() * 0.5f, y2,
                    UITheme.radiusChip(height), hover * 0.8f);

        ri.getFr().drawString(name, x1, midPointY,
                UITheme.mix(UITheme.textSecondary(), UITheme.textPrimary(), hover),
                ri.getFr().CENTREY);
        RenderUtils.resetColorText();

        return draw(ri);
    }

    protected abstract float draw(RenderInfo ri);

    @Override
    protected final void clickComponent(int mouseX, int mouseY, int mouseButton) {
        if (self.isVisible())
            click(mouseX, mouseY, mouseButton);
    }

    protected void click(int mouseX, int mouseY, int mouseButton) {}

    public String getName() { return name; }

    @Override
    public int getWidth(int i) {
        return self.isVisible() ? (int) UITheme.space(i, 24) : 0;
    }

    @Override
    public int getHeight(int i) {
        return self.isVisible() ? (int) UITheme.space(i, 4.6f) : 0;
    }
}

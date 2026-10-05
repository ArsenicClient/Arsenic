package arsenic.module.property.impl;

import arsenic.gui.click.UITheme;
import arsenic.gui.click.impl.PropertyComponent;
import arsenic.gui.themes.ThemeManager;
import arsenic.module.property.Property;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderInfo;

public class ButtonProperty extends Property<String> {

    private final Runnable action;
    private final String label;

    public ButtonProperty(String value) {
        this(value, value, null);
    }

    public ButtonProperty(String name, Runnable action) {
        this(name, name, action);
    }

    public ButtonProperty(String name, String label, Runnable action) {
        super(name);
        this.label = label;
        this.action = action;
    }

    public void fire() {
        if (action == null)
            return;
        try {
            action.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public PropertyComponent<ButtonProperty> createComponent() {
        return new PropertyComponent<ButtonProperty>(this) {

            private float buttonX1;

            @Override
            protected float draw(RenderInfo ri) {
                float hover = hoverPct();
                float press = pressPct();

                float bh = height * 0.62f;
                float by1 = midPointY - bh / 2f;
                float by2 = midPointY + bh / 2f;
                float radius = UITheme.radiusChip(bh);
                float padding = bh * 0.55f;

                float textWidth = ri.getFr().getWidth(label);
                buttonX1 = Math.min(controlX1(), x2 - textWidth - padding * 2f);

                float sink = press * bh * 0.06f;

                UITheme.surface(buttonX1, by1 + sink, x2, by2 + sink, radius,
                        UITheme.alpha(UITheme.accent(), (int) (40 + 55 * hover)),
                        UITheme.Elevation.RAISED, 0.5f + 0.5f * hover - press * 0.4f);
                DrawUtils.drawRoundedOutline(buttonX1, by1 + sink, x2, by2 + sink, radius, 1f,
                        UITheme.alpha(UITheme.accent(), (int) (90 + 110 * hover)));

                ri.getFr().drawString(label, (buttonX1 + x2) / 2f, midPointY + sink,
                        UITheme.mix(UITheme.textSecondary(), ThemeManager.getWhite(), hover),
                        ri.getFr().CENTREX, ri.getFr().CENTREY);

                return height;
            }

            @Override
            protected void click(int mouseX, int mouseY, int mouseButton) {
                if (mouseButton != 0 || mouseX < buttonX1)
                    return;
                fire();
            }
        };
    }
}

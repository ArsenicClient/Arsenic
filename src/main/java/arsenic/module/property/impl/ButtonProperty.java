package arsenic.module.property.impl;

import arsenic.gui.click.UITheme;
import arsenic.gui.click.impl.PropertyComponent;
import arsenic.gui.design.Hover;
import arsenic.gui.design.M3Components;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import arsenic.module.property.Property;
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

            private float buttonX1 = Float.MAX_VALUE;
            private boolean down;
            private final Object buttonKey = new Object();
            private boolean buttonHovered;
            private final AnimationTimer buttonHover =
                    new AnimationTimer(UITheme.DUR_HOVER, () -> buttonHovered, TickMode.CUBIC);

            @Override
            protected float labelRight() {
                return x2 - width * 0.3f;
            }

            @Override
            protected float draw(RenderInfo ri) {
                float bh = height * 0.62f;
                float by1 = midPointY - bh / 2f;
                float by2 = midPointY + bh / 2f;
                float padding = bh * 0.55f;

                float textWidth = ri.getFr().getWidth(label);
                buttonX1 = Math.min(controlX1(), x2 - textWidth - padding * 2f);

                buttonHovered = Hover.test(buttonKey, buttonX1, by1, x2, by2);
                if (down && !pressed)
                    down = false;
                float sink = down ? bh * 0.04f : 0f;

                M3Components.button(ri.getFr(), label, buttonX1, by1 + sink, x2, by2 + sink,
                        buttonHover.getPercent(), true);
                return height;
            }

            @Override
            protected void click(int mouseX, int mouseY, int mouseButton) {
                if (mouseButton != 0 || mouseX < buttonX1)
                    return;
                down = true;
                fire();
            }

            @Override
            public void mouseReleased(int mouseX, int mouseY, int state) {
                down = false;
            }
        };
    }
}

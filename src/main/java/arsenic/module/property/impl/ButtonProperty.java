package arsenic.module.property.impl;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.module.property.Property;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderInfo;

public class ButtonProperty extends Property<String> {

    //mostly here as a reminder to make this at some point in the future
    public ButtonProperty(String value) { super(value); }

    @Override
    public PropertyComponent createComponent() {
        return new PropertyComponent<ButtonProperty>(this) {
            @Override
            protected int draw(RenderInfo ri) {
                ri.getFr().drawString(getValue(), x1, y1 + (height/2f) - ((ri.getFr().getHeight(getValue())/2)), 0xFFFFFFFE);
                return height;
            }
        };
    }
}
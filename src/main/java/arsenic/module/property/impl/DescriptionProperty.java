package arsenic.module.property.impl;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.module.property.Property;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;

public class DescriptionProperty extends Property<String> {
    public DescriptionProperty(String value) { super(value); }

    @Override
    public PropertyComponent createComponent() {
        return new PropertyComponent<DescriptionProperty>(this) {
            @Override
            protected int draw(RenderInfo ri) {
                RenderUtils.drawRect(x1, y1, x2, y2, 0xFF00FF00);
                ri.getFr().drawString(getName(), x1, y1 + (height) / 2, 0xFF00FFFF);
                return height;
            }
        };
    }
}

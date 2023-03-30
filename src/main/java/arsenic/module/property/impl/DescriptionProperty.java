package arsenic.module.property.impl;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.module.property.Property;
import arsenic.utils.render.RenderInfo;

public class DescriptionProperty extends Property<String> {
    public DescriptionProperty(String value) { super(value); }

    @Override
    public PropertyComponent<DescriptionProperty> createComponent() {
        return new PropertyComponent<DescriptionProperty>(this) {
            @Override
            protected float draw(RenderInfo ri) {
                return height;
            }
        };
    }
}

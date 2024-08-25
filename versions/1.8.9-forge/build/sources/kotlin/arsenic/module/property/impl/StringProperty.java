package arsenic.module.property.impl;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.module.property.Property;
import arsenic.utils.render.RenderInfo;
import cc.polyfrost.oneconfig.config.elements.BasicOption;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigHeader;

import java.lang.reflect.Field;

public class StringProperty extends Property<String> {

    public boolean dummyVar;

    public StringProperty(String value) { super(value); }

    @Override
    public PropertyComponent<StringProperty> createComponent() {
        return new PropertyComponent<StringProperty>(this) {
            @Override
            protected float draw(RenderInfo ri) {
                return height;
            }
        };
    }

    @Override
    public BasicOption getOption() {
        try {
            Field field = getClass().getField("dummyVar");
            return new ConfigHeader(field, dummyVar, getName(), "General", "", 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}

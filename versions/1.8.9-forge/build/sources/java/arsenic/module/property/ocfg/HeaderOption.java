package arsenic.module.property.ocfg;

import cc.polyfrost.oneconfig.gui.elements.config.ConfigHeader;

import java.lang.reflect.Field;

public class HeaderOption extends ConfigHeader {
    public HeaderOption(Field field, Object parent, String name, String category, String subcategory, int size) {
        super(field, parent, name, category, subcategory, size);
    }
}

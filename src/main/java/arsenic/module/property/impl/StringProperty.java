package arsenic.module.property.impl;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.module.property.Property;
import arsenic.utils.render.RenderInfo;

/**
 * A plain line of text in a module's settings - a heading or a note.
 * <p>
 * {@link Property#getName()} returns the value for a non-serialisable property, so the row label
 * the base component draws already <em>is</em> this property's text; there is deliberately nothing
 * to add on the control side.
 */
public class StringProperty extends Property<String> {

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
}

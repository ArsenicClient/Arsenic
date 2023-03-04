package arsenic.gui.click.impl;

import arsenic.gui.click.Component;
import arsenic.module.property.Property;
import arsenic.module.property.SerializableProperty;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.render.RenderInfo;

public abstract class PropertyComponent<T extends Property> extends Component implements IContainable {

    // placeholder class
    private final String name;
    protected final T self;

    public PropertyComponent(T p) {
        self = p;
        if (p instanceof SerializableProperty) {
            name = ((SerializableProperty<?>) p).getName();
        } else {
            name = p.getValue().toString();
        }
    }

    @Override
    protected final int drawComponent(RenderInfo ri) {
        if (self.isVisible()) { return draw(ri); }
        return 0;
    }

    protected abstract int draw(RenderInfo ri);

    @Override
    protected final void clickComponent(int mouseX, int mouseY, int mouseButton) {
        if (self.isVisible()) { click(mouseX, mouseY, mouseButton); }
    }

    protected void click(int mouseX, int mouseY, int mouseButton) {

    }

    public String getName() { return name; }

    @Override
    protected int getWidth(int i) {
        return 30 * (i/100);
    }
    protected int getHeight(int i) {
        return 5 * (i/100);
    }
}

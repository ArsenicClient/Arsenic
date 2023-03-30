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

    protected PropertyComponent(T p) {
        self = p;
        if (p instanceof SerializableProperty) {
            name = ((SerializableProperty<?>) p).getName();
        } else {
            name = p.getValue().toString();
        }
    }

    @Override
    protected final float drawComponent(RenderInfo ri) {
        if (self.isVisible()) {
            //name
            ri.getFr().drawYCenteredString(name, x1, midPointY, 0xFFFFFFFE);
            return draw(ri);
        }
        return 0f;
    }

    protected abstract float draw(RenderInfo ri);

    @Override
    protected final void clickComponent(int mouseX, int mouseY, int mouseButton) {
        if (self.isVisible()) { click(mouseX, mouseY, mouseButton); }
    }

    protected void click(int mouseX, int mouseY, int mouseButton) {

    }

    public String getName() { return name; }

    @Override
    protected int getWidth(int i) {
        return 23 * (i / 100);
    }

    @Override
    protected int getHeight(int i) {
        return 5 * (i / 100);
    }
}

package arsenic.module.property.impl;

import java.util.List;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.module.property.Property;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderInfo;
import scala.actors.threadpool.Arrays;

public class FolderProperty extends Property<List<Property>> {

    // does not save the config of properties inside the folder
    // properties inside the folder cannot use @PropertyInfo
    // very unfinished just here to remind me that at some point i should make it

    private boolean open;

    protected FolderProperty(Property... values) {
        super(Arrays.asList(values));
    }

    public boolean isOpen() { return open; }

    public void toggleOpen() {
        open = !open;
    }

    public void setOpen(boolean open) { this.open = open; }

    @Override
    public PropertyComponent createComponent() {
        return new PropertyComponent<FolderProperty>(this) {
            @Override
            protected int draw(RenderInfo ri) {
                DrawUtils.drawRect(x1, y1, x2, y2, 0xFF00FF00);
                ri.getFr().drawString(getName(), x1, y1 + (height) / 2, 0xFF00FFFF);
                return height;
            }
        };
    }
}

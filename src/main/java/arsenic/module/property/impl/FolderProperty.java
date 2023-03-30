package arsenic.module.property.impl;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.module.property.Property;
import arsenic.utils.render.RenderInfo;
import scala.actors.threadpool.Arrays;

import java.util.List;

public class FolderProperty extends Property<List<Property<?>>> {

    // does not save the config of properties inside the folder
    // properties inside the folder cannot use @PropertyInfo
    // very unfinished just here to remind me that at some point i should make it

    private boolean open;
    private final String name;

    //this will cause an issue with its name
    protected FolderProperty(String name, Property<?>... values) {
        super(Arrays.asList(values));
        this.name = name;
    }

    public boolean isOpen() { return open; }

    public void toggleOpen() {
        open = !open;
    }

    public void setOpen(boolean open) { this.open = open; }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public PropertyComponent<FolderProperty> createComponent() {
        return new PropertyComponent<FolderProperty>(this) {
            @Override
            protected float draw(RenderInfo ri) {
                return height;
            }
        };
    }
}

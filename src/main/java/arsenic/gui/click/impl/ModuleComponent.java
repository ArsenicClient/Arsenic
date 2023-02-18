package arsenic.gui.click.impl;

import arsenic.gui.click.Component;
import arsenic.module.Module;
import arsenic.module.property.Property;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.render.DimensionInfo;
import arsenic.utils.render.RenderInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class ModuleComponent extends Component implements IContainer<Property> {

    private final Module self;
    private final Collection<Property> subcomponents;
    private final String name;

    private boolean expanded;

    public ModuleComponent(@NotNull Module self, Collection<Property> subcomponents) {
        this.self = self;
        this.subcomponents = subcomponents;
        this.name = self.getName();
    }

    @Override
    protected int drawComponent(DimensionInfo di, RenderInfo ri) {
        return 0;
    }

    @Override
    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {

    }

    public final Collection<Property> getContents() {
        return subcomponents;
    }

    public final String getName() {
        return name;
    }

}

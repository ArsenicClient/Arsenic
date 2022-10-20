package arsenic.gui.click.impl;

import arsenic.gui.click.Component;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.interfaces.IExpandable;
import arsenic.utils.render.RenderInfo;

import java.util.Collection;

public class ContainerComponent extends Component implements IExpandable<Component> {

    private final IContainer self;
    private final Collection<Component> subcomponents;
    private final String name;

    private boolean expanded;

    public ContainerComponent(IContainer self, Collection<Component> subcomponents) {
        this.self = self;
        this.subcomponents = subcomponents;
        this.name = self.getName();
    }

    @Override
    public void drawComponent(int x, int y, RenderInfo ri) {

    }

    public final IContainer getSelf() {
        return self;
    }

    public final Collection<Component> getSubcomponents() {
        return subcomponents;
    }

    public final String getName() {
        return name;
    }

    @Override
    public boolean isExpanded() {
        return expanded;
    }

    @Override
    public void setExpanded(boolean expanded) {
        expanded = !expanded;
    }

    @Override
    public Collection<Component> getContents() {
        return subcomponents;
    }

}

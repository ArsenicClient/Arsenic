package arsenic.gui.click;

import arsenic.gui.click.impl.ContainerComponent;
import arsenic.gui.click.impl.ModuleComponent;
import arsenic.module.Module;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;

import java.util.ArrayList;
import java.util.Collection;

public class Panel {

    private final String name;
    private final Collection<Component> components = new ArrayList<>();

    public Panel(String name) {
        this.name = name;
    }

    public final String getName() {
        return name;
    }

    public final Collection<Component> getComponents() {
        return components;
    }

    public final void addComponent(Component component) {
        components.add(component);
    }

    public final void addAsComponent(IContainable ic) {
        if(ic instanceof IContainer) {
            if(ic instanceof Module) {
                components.add(new ModuleComponent(((Module) ic), new ArrayList<>()));
            } else {
                components.add(new ContainerComponent(((IContainer) ic), new ArrayList<>()));
            }
        } else {

        }
    }

}

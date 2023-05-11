package arsenic.module;

import arsenic.main.Arsenic;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;

import java.util.ArrayList;
import java.util.Collection;

public enum ModuleCategory implements IContainer<Module>, IContainable {
    PLAYERS("Players"), WORLD("World"), CLIENT("Client"), GHOST("Ghost"), BLATANT("Blatant"), OTHER("Other"),
    MOVEMENT("Movement");

    private final String name;

    ModuleCategory(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    @Override
    public Collection<Module> getContents() {
        return new ArrayList<>(Arsenic.getInstance().getModuleManager().getModulesByCategory(this));
    }

}

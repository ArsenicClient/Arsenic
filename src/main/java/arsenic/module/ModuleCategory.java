package arsenic.module;

import arsenic.main.Arsenic;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;

import java.util.ArrayList;
import java.util.Collection;

public enum ModuleCategory implements IContainer<Module>, IContainable {
    COMBAT,
    MOVEMENT,
    PLAYER,
    RENDER,
    CLIENT,
    CONFIGS,
    GUI("GUI"),
    SEARCH {
        @Override
        public Collection<Module> getContents() {
            return new ArrayList<>(Arsenic.getArsenic().getModuleManager().getModules());
        }
    };

    private final String name;

    ModuleCategory() {
        name = name().substring(0, 1).toUpperCase() + name().substring(1).toLowerCase();
    }

    ModuleCategory(String displayName) {
        name = displayName;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Collection<Module> getContents() {
        return new ArrayList<>(Arsenic.getArsenic().getModuleManager().getModulesByCategory(this));
    }

}

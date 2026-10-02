package arsenic.module;

import arsenic.main.Arsenic;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;

import java.util.ArrayList;
import java.util.Collection;

/**
 * The five categories every module belongs to, plus the pseudo-categories the ClickGUI uses for its
 * own panes (Configs, Presets, Search), which hold no modules and exist only to give those panes a
 * sidebar entry.
 * <p>
 * There used to be six real ones, and the split never held up: Ghost and Blatant were the same
 * modules sorted by how obvious they looked, and World and Settings both held a mix of render
 * modules and client plumbing. They collapse into Combat and Render respectively, which is one
 * fewer thing to navigate and one fewer judgement call when a new module is written.
 */
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
        // Title-case the constant: COMBAT -> "Combat".
        name = name().substring(0, 1).toUpperCase() + name().substring(1).toLowerCase();
    }

    /** For names the title-case rule gets wrong - GUI would become "Gui". */
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

package arsenic.module;

import arsenic.main.Arsenic;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public enum ModuleCategory implements IContainer {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    VISUAL("Visual"),
    EXPLOIT("Exploit"),
    MINIGAMES("Minigames"),
    PIT("Pit"), //i am going to make a shit ton of things for pit
    MISC("Misc");

    private final String name;

    ModuleCategory(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Contract(" -> new")
    @Override
    public @NotNull Collection<IContainable> getContents() {
        return new ArrayList<>(Arsenic.getInstance().getModuleManager().getModulesByCategory(this));
    }

}

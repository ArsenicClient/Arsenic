package arsenic.module;

import arsenic.main.Arsenic;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public enum ModuleCategory implements IContainer<Module>, IContainable {
    PLAYERS("Players"),
    WORLD("World"),
    CLIENT("Client"),
    GHOST("Ghost"),
    BLATANT("Blatant"),
    OTHER("Other"),
    MOVEMENT("Movement");
    private final String name;

    ModuleCategory(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Contract(" -> new")
    @Override
    public @NotNull Collection<Module> getContents() {
        return new ArrayList<>(Arsenic.getInstance().getModuleManager().getModulesByCategory(this));
    }

}

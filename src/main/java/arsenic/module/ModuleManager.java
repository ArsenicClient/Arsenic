package arsenic.module;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventKey;
import arsenic.main.Arsenic;
import arsenic.module.impl.misc.TestModule;
import arsenic.module.impl.misc.TestRotation;
import arsenic.module.impl.movement.Sprint;
import arsenic.module.impl.visual.ClickGui;
import arsenic.module.impl.visual.HUD;

public class ModuleManager {

    private final Map<Class<? extends Module>, Module> modules;

    public ModuleManager() {
        this.modules = new HashMap<>();
    }

    public final int initialize() {

        add(
                new TestModule(),
                new HUD(),
                new Sprint(),
                new ClickGui()
        );

        Arsenic.getInstance().getEventManager().subscribe(this);

        return modules.size();
    }

    private void add(Module @NotNull ... modules) {
        for(Module module : modules) {
            this.modules.put(module.getClass(), module);
            module.registerProperties();
        }
    }

    public final Map<Class<? extends Module>, Module> getModulesMap() {
        return modules;
    }

    @Contract(pure = true)
    public final @NotNull Collection<Module> getModules() {
        return modules.values();
    }

    public final Collection<Module> getEnabledModules() {
        return modules.values().stream().filter(Module::isEnabled).collect(Collectors.toList());
    }

    public final Collection<Module> getModules(ModuleCategory category) {
        return modules.values().stream().filter(m -> m.getCategory() == category).collect(Collectors.toList());
    }

    public final <T extends Module> T getModule(Class<T> moduleClass) {
        return (T) modules.get(moduleClass);
    }

    @EventLink
    public final Listener<EventKey> onKeyPress = event -> {
        getModules().forEach(module -> {
            if(event.getKeycode() == module.getKeybind()) {
                module.setEnabled(!module.isEnabled());
            }
        });
    };

}

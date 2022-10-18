package arsenic.module;

import arsenic.event.bus.Listener;
import arsenic.event.impl.EventKey;
import arsenic.main.Arsenic;
import arsenic.module.impl.misc.TestModule;
import arsenic.module.impl.visual.HUD;
import arsenic.event.bus.annotations.EventLink;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ModuleManager {

    private final Map<Class<? extends Module>, Module> modules;

    public ModuleManager() {
        this.modules = new HashMap<>();
    }

    public int initialize() {

        add(
                new TestModule(),
                new HUD()

        );

        Arsenic.getInstance().getEventManager().subscribe(this);

        return modules.size();
    }

    private void add(Module... modules) {
        for(Module module : modules) {
            this.modules.put(module.getClass(), module);
        }
    }

    public Map<Class<? extends Module>, Module> getModulesMap() {
        return modules;
    }

    public Collection<Module> getModules() {
        return modules.values();
    }

    public Collection<Module> getEnabledModules() {
        return modules.values().stream().filter(Module::isEnabled).collect(Collectors.toList());
    }

    public <T extends Module> T getModule(Class<T> moduleClass) {
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

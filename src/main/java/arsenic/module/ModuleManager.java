package arsenic.module;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventKey;
import arsenic.main.Arsenic;
import arsenic.module.impl.client.PostProcessing;
import org.reflections.Reflections;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.reflections.scanners.Scanners.SubTypes;

public class ModuleManager {

    private final Map<Class<? extends Module>, Module> modules = new HashMap<>();

    public final int initialize() {
        if(modules.size() != 0)
            throw new RuntimeException("Double initialization of Module Manager.");

        Reflections reflections = new Reflections("arsenic.module");
        reflections.get(SubTypes.of(Module.class).asClass()).forEach(module -> addModule((Class<? extends Module>) module));
        if(System.getProperty("os.name").toLowerCase().contains("mac"))
            modules.remove(PostProcessing.class);
        Arsenic.getInstance().getEventManager().subscribe(this);
        return modules.size();
    }

    public final Collection<Module> getModules() { return modules.values(); }

    public final Collection<Module> getEnabledModules() {
        return getModules().stream().filter(Module::isEnabled).collect(Collectors.toList());
    }


    public List<String> getClosestModuleName(String name) {
        return Arsenic.getArsenic().getModuleManager().getModules()
                .stream().map(Module::getName).filter(cName -> cName.toLowerCase().startsWith(name.toLowerCase()))
                .sorted(Comparator.naturalOrder()).collect(Collectors.toList());
    }

    public final Collection<Module> getModulesByCategory(ModuleCategory category) {
        return getModules().stream().filter(m -> m.getCategory() == category).collect(Collectors.toList());
    }

    public <T extends Module> T getModuleByClass(Class<T> moduleClass) {
        return (T) modules.get(moduleClass);
    }

    public final Module getModuleByName(String str) {
        for (Module module : getModules()) {
            if (module.getName().equalsIgnoreCase(str))
                return module;
        }
        return null;
    }

    @EventLink
    public final Listener<EventKey> onKeyPress = event -> {
        AtomicBoolean saveConfig = new AtomicBoolean(false); // for eff

        getModules().stream().filter(m -> m.getKeybind() == event.getKeycode())
                .forEach(m -> {
                    m.setEnabled(!m.isEnabled());
                    saveConfig.set(true);
                });

        if (saveConfig.get()) { Arsenic.getArsenic().getConfigManager().saveConfig(); }
    };

    private void addModule(Class<? extends Module> moduleClass) {
        try {
            Module module = moduleClass.newInstance();
            module.registerProperties();
            modules.put(moduleClass, module);
        } catch (Exception e) {e.printStackTrace();}
    }
}

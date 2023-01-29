package arsenic.module;

import java.io.File;
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
import arsenic.utils.java.JavaUtils;

public class ModuleManager {

    private final Map<Class<? extends Module>, Module> modules;

    public ModuleManager() {
        modules = new HashMap<>();
    }

    public final int initialize() {

        // might not work on obfuscation -> could search whole project for anything that
        // extends module / search for the annotation however that seems harder
        for (File folder : JavaUtils.getFilesFromPackage("arsenic.module.impl")) {
            String packageName = "arsenic.module.impl." + folder.getName();
            for (File file : JavaUtils.getFilesFromPackage(packageName)) {
                String className = file.getName().replaceAll(".class$", "");
                Class<?> cls = null;
                try {
                    cls = Class.forName(packageName + "." + className);
                    if (Module.class.isAssignableFrom(cls)) {
                        add((Module) cls.newInstance());
                    }
                } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                }
            }
        }

        Arsenic.getInstance().getEventManager().subscribe(this);

        return modules.size();
    }

    private void add(Module @NotNull... modules) {
        for (Module module : modules) {
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
            if (event.getKeycode() == module.getKeybind()) {
                module.setEnabled(!module.isEnabled());
            }
        });
    };

}

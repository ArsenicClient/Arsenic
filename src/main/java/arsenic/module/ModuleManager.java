package arsenic.module;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventKey;
import arsenic.main.Arsenic;
import arsenic.module.impl.visual.PostProcessing;
import net.minecraft.client.Minecraft;
import org.reflections.Reflections;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.reflections.scanners.Scanners.SubTypes;
import org.lwjgl.input.Keyboard;

public class ModuleManager {

    private final Map<Class<? extends Module>, Module> modules = new HashMap<>();

    public final int initialize() {
        if(modules.size() != 0)
            throw new RuntimeException("Double initialization of Module Manager.");

        Reflections reflections = new Reflections("arsenic.module");
        reflections.get(SubTypes.of(Module.class).asClass()).forEach(module -> addModule((Class<? extends Module>) module));

        if(System.getProperty("os.name").toLowerCase().contains("mac"))
            modules.remove(PostProcessing.class);

        if (!isDevBuild()) {
            modules.entrySet().removeIf(entry -> {
                ModuleInfo info = entry.getValue().getClass().getAnnotation(ModuleInfo.class);
                return info != null && info.tier() == ModuleTier.DEV;
            });
        }

        Arsenic.getInstance().getAddonManager().installDefaults();
        Arsenic.getInstance().getAddonManager().load(this);

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
        int clickGuiKey = arsenic.gui.click.GuiStyle.get().getClickGuiKey();
        if (clickGuiKey != 0 && event.getKeycode() == clickGuiKey) {
            Minecraft.getMinecraft().displayGuiScreen(Arsenic.getArsenic().getClickGuiScreen());
            return;
        }

        AtomicBoolean saveConfig = new AtomicBoolean(false);

        getModules().stream().filter(m -> m.getKeybind() == event.getKeycode())
                .forEach(m -> {
                    m.setEnabled(!m.isEnabled());
                    saveConfig.set(true);
                });

        if (saveConfig.get()) { Arsenic.getArsenic().getConfigManager().saveConfig(); }
    };

    /** Registers a module that did not come from the client jar (an addon). @return why it was rejected, or null. */
    /** devmode.properties is only packaged into the "-dev" jar (see build.gradle devJar). */
    public static boolean isDevBuild() {
        return ModuleManager.class.getResource("/devmode.properties") != null;
    }

    public String registerExternal(Module module) {
        // dev-tier modules must stay out of normal builds, addons included
        if (module.getTier() == ModuleTier.DEV && !isDevBuild())
            return "dev-tier modules only load in the dev jar";
        if (getModuleByName(module.getName()) != null)
            return "a module named " + module.getName() + " already exists";
        try {
            module.registerProperties();
        } catch (Exception e) {
            return "could not register properties: " + e;
        }
        module.markAddon();
        modules.put(module.getClass(), module);
        module.getCommands().forEach(Arsenic.getArsenic().getCommandManager()::add);
        return null;
    }

    public void unregisterExternal(Module module) {
        modules.remove(module.getClass());
        module.getCommands().forEach(Arsenic.getArsenic().getCommandManager()::remove);
    }

    private void addModule(Class<? extends Module> moduleClass) {
        try {
            Module module = moduleClass.newInstance();
            module.registerProperties();
            modules.put(moduleClass, module);
        } catch (Exception e) {e.printStackTrace();}
    }
}

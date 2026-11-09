package arsenic.module;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventGameLoop;
import arsenic.event.impl.EventKey;
import arsenic.main.Arsenic;
import arsenic.module.impl.visual.PostProcessing;
import arsenic.utils.java.BindUtils;
import net.minecraft.client.Minecraft;
import org.reflections.Reflections;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.reflections.scanners.Scanners.SubTypes;
import org.lwjgl.opengl.Display;

public class ModuleManager {

    private final Map<Class<? extends Module>, Module> modules = new ConcurrentHashMap<>();
    /**
     * Built-in modules of a tier this build leaves out. They are never listed, enabled or saved, but the engine reads
     * them by class (getModuleByClass), so they still exist and stay disabled.
     */
    private final Map<Class<? extends Module>, Module> unavailable = new ConcurrentHashMap<>();
    /** Modules a held key switched on, so its release can switch them off again. */
    private final Set<Module> heldModules = new HashSet<>();

    public final int initialize() {
        if (!modules.isEmpty())
            throw new RuntimeException("Double initialization of Module Manager.");

        Reflections reflections = new Reflections("arsenic.module");
        reflections.get(SubTypes.of(Module.class).asClass()).forEach(module -> addModule((Class<? extends Module>) module));

        if (System.getProperty("os.name").toLowerCase().contains("mac"))
            modules.remove(PostProcessing.class);

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
        return getModules().stream().map(Module::getName).filter(cName -> cName.toLowerCase().startsWith(name.toLowerCase()))
                .sorted(Comparator.naturalOrder()).collect(Collectors.toList());
    }

    public final Collection<Module> getModulesByCategory(ModuleCategory category) {
        return getModules().stream().filter(m -> m.getCategory() == category).collect(Collectors.toList());
    }

    public <T extends Module> T getModuleByClass(Class<T> moduleClass) {
        Module module = modules.get(moduleClass);
        return (T) (module != null ? module : unavailable.get(moduleClass));
    }

    public final Module getModuleByName(String str) {
        for (Module module : getModules()) {
            if (module.getName().equalsIgnoreCase(str))
                return module;
        }
        return null;
    }

    /**
     * Keys whose press was already handled. The press event comes from a bytecode hook in runTick, which other
     * clients' patches to runTick (OneClient's) can break, so onGameLoop polls the bound keys too and skips any
     * press this set shows the hook delivered.
     */
    private final Set<Integer> handledKeys = new HashSet<>();

    @EventLink
    public final Listener<EventKey> onKeyPress = event -> {
        // the poll below can see a press before the runTick hook delivers it; whichever comes second is a repeat
        if (!handledKeys.contains(event.getKeycode()))
            handleKey(event.getKeycode());
    };

    private void handleKey(int keycode) {
        handledKeys.add(keycode);
        int clickGuiKey = arsenic.gui.click.GuiStyle.get().getClickGuiKey();
        if (clickGuiKey != 0 && keycode == clickGuiKey) {
            if (Minecraft.getMinecraft().currentScreen instanceof arsenic.gui.click.ClickGuiScreen) {
                ((arsenic.gui.click.ClickGuiScreen) Minecraft.getMinecraft().currentScreen).startClosing();
            } else {
                Minecraft.getMinecraft().displayGuiScreen(Arsenic.getArsenic().getClickGuiScreen());
            }
            return;
        }

        // a key press is rare enough to just walk the modules; a bind cache would also have to follow config loads
        boolean toggled = false;
        for (Module m : getModules()) {
            if (m.getKeybind() != keycode || m.getKeybindMode() != KeybindMode.TOGGLE)
                continue;
            m.setEnabled(!m.isEnabled());
            toggled = true;
        }
        if (toggled)
            Arsenic.getArsenic().getConfigManager().saveConfig();
    }

    /** Presses of bound keys the runTick hook did not deliver. */
    private void pollPresses() {
        boolean live = Display.isActive() && Minecraft.getMinecraft().currentScreen == null;
        List<Integer> keys = new ArrayList<>();
        int clickGuiKey = arsenic.gui.click.GuiStyle.get().getClickGuiKey();
        if (clickGuiKey != 0)
            keys.add(clickGuiKey);
        for (Module m : getModules()) {
            if (m.getKeybind() != 0 && m.getKeybindMode() == KeybindMode.TOGGLE)
                keys.add(m.getKeybind());
        }
        // presses of keys nothing is bound to must not stay marked, or binding one later would miss its first press
        handledKeys.retainAll(keys);
        for (int key : keys) {
            boolean down = BindUtils.isDown(key);
            if (!down) {
                handledKeys.remove(key);
            } else if (!live) {
                // held through a screen or an unfocused window: not a new press once the game is live again
                handledKeys.add(key);
            } else if (!handledKeys.contains(key)) {
                handleKey(key);
            }
        }
    }

    /**
     * Hold binds are polled instead of read from presses: the module is on exactly while its key is down. They stay
     * silent, and they let go when the window loses focus or a screen opens, so a key cannot stick.
     */
    @EventLink
    public final Listener<EventGameLoop> onGameLoop = event -> {
        if (modules.isEmpty())
            return;
        pollPresses();
        boolean windowActive = Display.isActive();
        for (Module module : getModules()) {
            if (module.getKeybindMode() != KeybindMode.HOLD || module.getKeybind() == 0)
                continue;
            boolean down = windowActive && Minecraft.getMinecraft().currentScreen == null
                    && BindUtils.isDown(module.getKeybind());
            if (down) {
                if (!module.isEnabled()) {
                    module.setEnabledQuietly(true);
                    heldModules.add(module);
                }
            } else if (heldModules.remove(module) && module.isEnabled()) {
                module.setEnabledQuietly(false);
            }
        }
    };

    /** Registers a module that did not come from the client jar (an addon). @return why it was rejected, or null. */
    public String registerExternal(Module module) {
        // modules of a tier this jar was not built with stay out, addons included
        if (!BuildTiers.allows(module.getTier()))
            return module.getTier().getDisplayName() + " modules are not in this build";
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
            ModuleInfo info = moduleClass.getAnnotation(ModuleInfo.class);
            if (info != null && !BuildTiers.allows(info.tier())) {
                unavailable.put(moduleClass, module);
            } else {
                modules.put(moduleClass, module);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

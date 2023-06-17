package arsenic.module;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventKey;
import arsenic.main.Arsenic;
import arsenic.module.impl.blatant.AutoBlock;
import arsenic.module.impl.blatant.NoSlow;
import arsenic.module.impl.blatant.Timer;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.impl.combat.Aura;
import arsenic.module.impl.combat.AutoSpacer;
import arsenic.module.impl.combat.Criticals;
import arsenic.module.impl.ghost.*;
import arsenic.module.impl.misc.Blink;
import arsenic.module.impl.misc.CustomFOV;
import arsenic.module.impl.misc.Sprint;
import arsenic.module.impl.movement.Flight;
import arsenic.module.impl.movement.Speed;
import arsenic.module.impl.visual.*;
import arsenic.module.impl.world.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class ModuleManager {

    private final HashMap<Class<? extends Module>, Module> modules = new HashMap<>();

    public final int initialize() {
	if(modules.size() != 0)
	    throw new RuntimeException("Double initialization of Module Manager.");

        addModule(FullBright.class,
                Sprint.class,
                HUD.class,
                ClickGui.class,
                ChestStealer.class,
                FastPlace.class,
                AimAssist.class,
                SafeWalk.class,
                Velocity.class,
                Reach.class,
                ESP.class,
                AutoClicker.class,
                ScaffoldTest.class,
                HitBox.class,
                AntiBot.class,
                Sprint.class,
                AutoClicker.class,
                NoSlow.class,
                Criticals.class,
                Blink.class,
                Flight.class,
                CustomFOV.class,
                CustomWorld.class,
                InvManager.class,
                NewHud.class,
                Timer.class,
                Aura.class,
                AutoBlock.class,
                Speed.class,
                AutoSpacer.class,
                TargetHud.class,
                ScaffoldTest2.class);

        Arsenic.getInstance().getEventManager().subscribe(this);
        return modules.size();
    }

    public final Collection<Module> getModules() { return modules.values(); }

    public final Collection<Module> getEnabledModules() {
        return getModules().stream().filter(Module::isEnabled).collect(Collectors.toList());
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

    private void addModule(Class<? extends Module>... moduleClassArray) {
        for(Class<? extends Module> moduleClass : moduleClassArray) {
            try {
                Module module = moduleClass.newInstance();
                module.registerProperties();
                modules.put(moduleClass, module);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }
}

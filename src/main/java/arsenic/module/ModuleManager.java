package arsenic.module;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventKey;
import arsenic.main.Arsenic;
import arsenic.module.impl.blatant.AutoBlock;
import arsenic.module.impl.blatant.NoSlow;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.impl.ghost.*;
import arsenic.module.impl.misc.Sprint;
import arsenic.module.impl.movement.Speed;
import arsenic.module.impl.visual.ClickGui;
import arsenic.module.impl.visual.ESP;
import arsenic.module.impl.visual.FullBright;
import arsenic.module.impl.visual.HUD;
import arsenic.module.impl.world.ChestStealer;
import arsenic.module.impl.world.FastPlace;
import arsenic.module.impl.world.SafeWalk;
import arsenic.module.impl.world.ScaffoldTest;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class ModuleManager {

    private final Set<Module> modules = Arrays.stream(Modules.values()).map(Modules::getModule)
            .collect(Collectors.toSet());

    public final int initialize() {
        Arsenic.getInstance().getEventManager().subscribe(this);
        return modules.size();
    }

    public final Set<Module> getModulesSet() { return modules; }

    public final Collection<Module> getEnabledModules() {
        return modules.stream().filter(Module::isEnabled).collect(Collectors.toList());
    }

    public final Collection<Module> getModulesByCategory(ModuleCategory category) {
        return modules.stream().filter(m -> m.getCategory() == category).collect(Collectors.toList());
    }

    public final Module getModuleByName(String str) {
        for (Module module : modules) {
            if (module.getName().equalsIgnoreCase(str))
                return module;
        }
        return null;
    }

    @EventLink
    public final Listener<EventKey> onKeyPress = event -> {
        AtomicBoolean saveConfig = new AtomicBoolean(false); // for eff
        modules.forEach(module -> {
            if (event.getKeycode() == module.getKeybind()) {
                module.setEnabled(!module.isEnabled());
                saveConfig.set(true);
            }
        });
        if (saveConfig.get()) { Arsenic.getArsenic().getConfigManager().saveConfig(); }
    };

    public enum Modules {
        FULLBRIGHT(FullBright.class), SPRINT(Sprint.class), HUD(HUD.class), CLICKGUI(ClickGui.class),
        CHESTSTEALER(ChestStealer.class), FASTPLACE(FastPlace.class), AIMASSIST(AimAssist.class),
        SAFEWALK(SafeWalk.class), VELOCITY(Velocity.class), REACH(Reach.class), ESP(ESP.class),
        AUTOCLICKER(AutoClicker.class), SCAFFOLDTEST(ScaffoldTest.class), HITBOX(HitBox.class),
        ANTIBOT(AntiBot.class), SPEED(Speed.class), AUTOBLOCK(AutoBlock.class), NOSLOW(NoSlow.class);

        private Module module;

        Modules(Class<? extends Module> module) {
            try {
                this.module = module.newInstance();
                this.module.registerProperties();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public Module getModule() { return module; }
    }
}

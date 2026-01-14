package arsenic.module.impl.movement;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.world.Scaffold;
import arsenic.module.property.impl.EnumProperty;
import net.minecraft.client.settings.KeyBinding;

@ModuleInfo(name = "Sprint",category = ModuleCategory.OTHER, hidden = true)
public class Sprint extends Module {
    public final EnumProperty<sMode> sprintMode = new EnumProperty<>("Mode: ", sMode.Legit);

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if(scaffoldDisabler())
            return;
        sprintMode.getValue().setSprinting();
    };

    @Override
    protected void onDisable() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        mc.thePlayer.setSprinting(false);
    }

    private boolean scaffoldDisabler(){
        return Arsenic.getArsenic().getModuleManager().getModuleByClass(Scaffold.class).isEnabled();
    }
    public enum sMode {
        Omni(() -> mc.thePlayer.setSprinting(true)),
        Legit(() -> KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true));

        private final Runnable v;
        sMode(Runnable v) {
            this.v = v;
        }

        public void setSprinting() {
            v.run();
        }
    }
}

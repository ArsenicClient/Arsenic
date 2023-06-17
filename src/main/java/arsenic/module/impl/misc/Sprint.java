package arsenic.module.impl.misc;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.functionalinterfaces.IVoidFunction;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

@ModuleInfo(name = "Sprint",category = ModuleCategory.OTHER, keybind = Keyboard.KEY_V)
//KEY_V more like KV // kv pls stop i beg
public class Sprint extends Module {
    public final EnumProperty<sMode> sprintMode = new EnumProperty<>("Mode: ", sMode.Legit);

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if(Arsenic.getArsenic().getModuleManager().getModuleByClass(Sprint.class).isEnabled())
            sprintMode.getValue().setSprinting();
    };

    @Override
    protected void onDisable() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        mc.thePlayer.setSprinting(false);
    }

    public enum sMode {
        Omni(() -> mc.thePlayer.setSprinting(true)),
        Legit(() -> KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true));

        private final IVoidFunction v;
        sMode(IVoidFunction v) {
            this.v = v;
        }

        public void setSprinting() {
            v.voidFunction();
        }
    }
}

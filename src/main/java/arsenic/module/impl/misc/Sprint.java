package arsenic.module.impl.misc;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleManager;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.functionalinterfaces.IVoidFunction;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

@ModuleInfo(name = "Sprint",category = ModuleCategory.OTHER, keybind = Keyboard.KEY_V)
//KEY_V more like KV
public class Sprint extends Module {
    public final EnumProperty<sMode> sprintMode = new EnumProperty<>("Mode: ", sMode.Legit);

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if(!ModuleManager.Modules.SCAFFOLDTEST.getModule().isEnabled())
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

        private IVoidFunction v;
        sMode(IVoidFunction v) {
            this.v = v;
        }

        public void setSprinting() {
            v.voidFunction();
        }
    }
}

package arsenic.module.impl.misc;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

@ModuleInfo(name = "Sprint",category = ModuleCategory.OTHER, keybind = Keyboard.KEY_V)
//KEY_V more like KV
public class Sprint extends Module {
    public final EnumProperty<sMode> sprintMode = new EnumProperty<>("Mode: ",sMode.Legit);

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (sprintMode.getValue().equals(sMode.Rage)) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        }
        if (sprintMode.getValue().equals(sMode.Omni)) {
            if (!mc.thePlayer.isCollided)
                if (mc.gameSettings.keyBindForward.isPressed() || mc.gameSettings.keyBindBack.isPressed()|| mc.gameSettings.keyBindRight.isPressed()||mc.gameSettings.keyBindLeft.isPressed()) {
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
                }
        }
        if (sprintMode.getValue().equals(sMode.Legit)) {
            if (mc.thePlayer.moveForward != 0) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
            }
        }
    };
    public enum sMode {
        Legit,Rage,Omni
    }
}

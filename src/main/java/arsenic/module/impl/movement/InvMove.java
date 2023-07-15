package arsenic.module.impl.movement;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventJump;
import arsenic.event.impl.EventMove;
import arsenic.event.impl.EventUpdate;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

@ModuleInfo(name = "InvMove",category = ModuleCategory.MOVEMENT)
public class InvMove extends Module {
    public final BooleanProperty guiOnly = new BooleanProperty("ClickGUI Only",true);
    public final BooleanProperty sprint = new BooleanProperty("Sprint",false);


    @EventLink
    public final Listener<EventMove> eventMoveListener = event -> {
        if ((guiOnly.getValue() && mc.currentScreen == Arsenic.getArsenic().getClickGuiScreen()) || (mc.currentScreen != null && !guiOnly.getValue())) {
            if (mc.currentScreen instanceof GuiChat) {
                return;
            }
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(),sprint.getValue());
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()));
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode()));
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode()));
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode()));
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()));
        }
    };
}

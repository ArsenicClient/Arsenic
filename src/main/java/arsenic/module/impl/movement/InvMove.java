package arsenic.module.impl.movement;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

@ModuleInfo(name = "InvMove", category = ModuleCategory.MOVEMENT)
public class InvMove extends Module {

    public final BooleanProperty onlyClickGui = new BooleanProperty("Only ClickGui", true);

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.currentScreen != null) {
            if (mc.currentScreen instanceof GuiChat) return;

            if (onlyClickGui.getValue() && mc.currentScreen != Arsenic.getArsenic().getClickGuiScreen())
                return;

            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),
                    Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()));
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(),
                    Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode()));
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(),
                    Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode()));
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(),
                    Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode()));
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(),
                    Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()));

            if (Keyboard.isKeyDown(208) && mc.thePlayer.rotationPitch < 90.0F) {
                mc.thePlayer.rotationPitch += 6.0F;
            }
            if (Keyboard.isKeyDown(200) && mc.thePlayer.rotationPitch > -90.0F) {
                mc.thePlayer.rotationPitch -= 6.0F;
            }
            if (Keyboard.isKeyDown(205)) {
                mc.thePlayer.rotationYaw += 6.0F;
            }
            if (Keyboard.isKeyDown(203)) {
                mc.thePlayer.rotationYaw -= 6.0F;
            }
        }
    };
}

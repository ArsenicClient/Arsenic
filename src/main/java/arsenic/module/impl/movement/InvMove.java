package arsenic.module.impl.movement;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.gui.click.impl.SearchComponent;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.minecraft.MoveUtil;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import java.util.ArrayList;
import java.util.Arrays;

@ModuleInfo(name = "InvMove", category = ModuleCategory.MOVEMENT)
public class InvMove extends Module {
    public final BooleanProperty cguiOnly = new BooleanProperty("ClickGui Only", false);
    public final BooleanProperty sprint = new BooleanProperty("Sprint", false);
    private final ArrayList<Integer> keys = new ArrayList<>(Arrays.asList(
            mc.gameSettings.keyBindJump.getKeyCode(),
            mc.gameSettings.keyBindForward.getKeyCode(),
            mc.gameSettings.keyBindBack.getKeyCode(),
            mc.gameSettings.keyBindLeft.getKeyCode(),
            mc.gameSettings.keyBindRight.getKeyCode()
    ));

    @RequiresPlayer
    @EventLink
    public final Listener<EventGameLoop> eventGameLoopListener = event -> {
        if (isInInventory()) {
            if (MoveUtil.isMoving()) {
                mc.thePlayer.setSprinting(sprint.getValue() && mc.thePlayer.moveForward > 0);
            }
            for (int keyCode : keys){
                KeyBinding.setKeyBindState(keyCode, Keyboard.isKeyDown(keyCode));
            }
        }
    };

    private boolean isInInventory() {
        return (mc.currentScreen != null
                && !(mc.currentScreen instanceof GuiChat)
                && (!cguiOnly.getValue() || (mc.currentScreen == Arsenic.getArsenic().getClickGuiScreen() && !(Arsenic.getArsenic().getClickGuiScreen().getCmcc() instanceof SearchComponent))));
    }
}

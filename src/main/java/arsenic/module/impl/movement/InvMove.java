package arsenic.module.impl.movement;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.minecraft.MoveUtil;
import arsenic.utils.minecraft.PacketUtil;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.potion.Potion;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Arrays;


@ModuleInfo(name = "InvMove", category = ModuleCategory.MOVEMENT)
public class InvMove extends Module {
    public final EnumProperty<iMode> mode = new EnumProperty<>("Mode", iMode.vanilla);
    public final BooleanProperty cguiOnly = new BooleanProperty("ClickGui Only", false);
    public final BooleanProperty sprint = new BooleanProperty("Sprint", false);
    private final ArrayList<Integer> keys = new ArrayList<>(Arrays.asList(
            mc.gameSettings.keyBindJump.getKeyCode(),
            mc.gameSettings.keyBindForward.getKeyCode(),
            mc.gameSettings.keyBindBack.getKeyCode(),
            mc.gameSettings.keyBindLeft.getKeyCode(),
            mc.gameSettings.keyBindRight.getKeyCode()
    ));

    @EventLink
    public final Listener<EventGameLoop> eventGameLoopListener = event -> {
        if (mc.thePlayer == null) return;
        if (isInInventory()) {
            if (MoveUtil.isMoving()) {
                mc.thePlayer.setSprinting(sprint.getValue() && mc.thePlayer.moveForward != 0);
            }
            for (int keyCode : keys){
                KeyBinding.setKeyBindState(keyCode, Keyboard.isKeyDown(keyCode));
            }
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> eventTickListener = event -> {
        if (isInInventory()) {
            if (mode.getValue() == iMode.hypixel) {
                if (mc.thePlayer.ticksExisted % (mc.thePlayer.isPotionActive(Potion.moveSpeed) ? 3 : 4) == 0) {
                    PacketUtil.send(new C0DPacketCloseWindow());
                    PacketUtil.send(new C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
                }
            }
        }
    };

    private boolean isInInventory() {
        return (mc.currentScreen != null
                && !(mc.currentScreen instanceof GuiChat)
                && (mc.currentScreen instanceof GuiInventory || !mode.getValue().equals(iMode.hypixel)))
                && (!cguiOnly.getValue() || mc.currentScreen == Arsenic.getArsenic().getClickGuiScreen());
    }

    public enum iMode {
        vanilla,
        hypixel
    }
}
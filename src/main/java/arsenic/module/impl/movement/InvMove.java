package arsenic.module.impl.movement;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
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


@ModuleInfo(name = "InvMove", category = ModuleCategory.MOVEMENT)
public class InvMove extends Module {
    public EnumProperty<iMode> mode = new EnumProperty<>("Mode", iMode.vanilla);
    public BooleanProperty sprint = new BooleanProperty("Sprint", false);

    @RequiresPlayer
    @EventLink
    public final Listener<EventGameLoop> eventGameLoopListener = event -> {
        switch (mode.getValue()) {
            case vanilla:
                if (isInInventory()) allowMove();
                break;
            case hypixel:
                if (isInInventory()) {
                    if (mc.currentScreen instanceof GuiInventory) {
                        allowMove();
                    }
                }
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventUpdate.Pre> preListener = event -> {
        if (isInInventory()) {
            if (mc.currentScreen instanceof GuiInventory) {
                if (mode.getValue() == iMode.hypixel) {
                    if (sprint.getValue() && MoveUtil.isMoving() && !mc.thePlayer.onGround) {
                        event.setOnGround(false);
                    }
                }
            }
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> eventTickListener = event -> {
        if (isInInventory()) {
            if (mc.currentScreen instanceof GuiInventory) {
                if (mode.getValue() == iMode.hypixel) {
                    if (mc.thePlayer.ticksExisted % (mc.thePlayer.isPotionActive(Potion.moveSpeed) ? 3 : 4) == 0) {
                        PacketUtil.send(new C0DPacketCloseWindow());
                        PacketUtil.send(new C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
                    }
                }
            }
        }
    };

    public enum iMode {
        vanilla,
        hypixel
    }

    private void allowMove() {
        if (MoveUtil.isMoving()) {
            mc.thePlayer.setSprinting(sprint.getValue());
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), sprint.getValue());
        }
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()));
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()));
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode()));
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode()));
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode()));
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()));
    }

    private boolean isInInventory() {
        return mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat || mc.currentScreen == client.getClickGuiScreen());
    }
}
package arsenic.module.impl.movement;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventGameLoop;
import arsenic.event.impl.EventLiving;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventUpdate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.utils.lag.LagManager;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C10PacketCreativeInventoryAction;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Arrays;

import static arsenic.utils.lag.LagManager.isHolding;

@ModuleInfo(name = "InvMove", category = ModuleCategory.MOVEMENT, tier = ModuleTier.DEV)
public class InvMove extends Module {

    private boolean pendingFlush = false;
    private boolean flushing = false;

    private final ArrayList<Integer> keys = new ArrayList<>(Arrays.asList(
            mc.gameSettings.keyBindJump.getKeyCode(),
            mc.gameSettings.keyBindForward.getKeyCode(),
            mc.gameSettings.keyBindBack.getKeyCode(),
            mc.gameSettings.keyBindLeft.getKeyCode(),
            mc.gameSettings.keyBindRight.getKeyCode()
    ));

    private boolean shouldBuffer() {
        return mc.currentScreen instanceof GuiInventory
                && mc.thePlayer != null
                && mc.thePlayer.openContainer instanceof ContainerPlayer;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventLiving> eventGameLoopListener = event -> {
        if (pendingFlush) {
            pendingFlush = false;
            flushing = true;
        }
        if (flushing) {
            for (int keyCode : keys) {
                KeyBinding.setKeyBindState(keyCode, false);
            }
            return;
        }

        if (mc.currentScreen == null) return;

        if (shouldBuffer()) {
            for (int keyCode : keys) {
                KeyBinding.setKeyBindState(keyCode, Keyboard.isKeyDown(keyCode));
            }

            if (!isHolding(getClass())) {
                LagManager.acquire(getClass(), p ->
                        p instanceof C0EPacketClickWindow
                                || p instanceof C0DPacketCloseWindow
                                || p instanceof C10PacketCreativeInventoryAction
                );
            }
        } else {
            if (LagManager.getHolders().contains(getClass())) {
                pendingFlush = true;
            }
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.OutGoing> onPacket = event -> {
        Packet<?> packet = event.getPacket();
        if (packet instanceof C0DPacketCloseWindow) {
            pendingFlush = true;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventUpdate.Post> onUpdatePost = event -> {
        if (!flushing)
            return;
        flushing = false;
        LagManager.release(getClass());
        for (int keyCode : keys) {
            KeyBinding.setKeyBindState(keyCode, Keyboard.isKeyDown(keyCode));
        }
    };

    @Override
    protected void onDisable() {
        pendingFlush = false;
        flushing = false;
        LagManager.release(getClass());
    }
}

package arsenic.module.impl.movement;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventGameLoop;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventUpdate;
import arsenic.gui.click.ClickGuiScreen;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C10PacketCreativeInventoryAction;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Arrays;

@ModuleInfo(name = "InvMove", category = ModuleCategory.MOVEMENT)
public class InvMove extends Module {

    private boolean isFlushing = false;
    private boolean pendingFlush = false;

    private final ArrayList<Integer> keys = new ArrayList<>(Arrays.asList(
            mc.gameSettings.keyBindJump.getKeyCode(),
            mc.gameSettings.keyBindForward.getKeyCode(),
            mc.gameSettings.keyBindBack.getKeyCode(),
            mc.gameSettings.keyBindLeft.getKeyCode(),
            mc.gameSettings.keyBindRight.getKeyCode()
    ));

    // Buffered inventory packets, held until the player interacts with their inventory
    private final ArrayList<Packet<?>> bufferedPackets = new ArrayList<>();

    @RequiresPlayer
    @EventLink
    public final Listener<EventGameLoop> eventGameLoopListener = event -> {
        if (mc.currentScreen == null) return;

        if (mc.currentScreen instanceof ClickGuiScreen || mc.currentScreen instanceof GuiInventory) {
            for (int keyCode : keys) {
                KeyBinding.setKeyBindState(keyCode, Keyboard.isKeyDown(keyCode));
            }
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.OutGoing> onPacket = event -> {
        if (mc.currentScreen == null || !(mc.thePlayer.openContainer instanceof ContainerPlayer)) return;
        if (isFlushing) return;

        Packet<?> packet = event.getPacket();

        if (packet instanceof C0DPacketCloseWindow) {
            // Buffer the close packet too, then flush everything together
            event.cancel();
            bufferedPackets.add(packet);
            for (int keyCode : keys) {
                KeyBinding.setKeyBindState(keyCode, false);
            }
            pendingFlush = true;
            return;
        }

        if (isInventoryPacket(packet)) {
            event.cancel();
            bufferedPackets.add(packet);
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventUpdate.Post> onUpdatePost = event -> {
        if (!pendingFlush) return;
        pendingFlush = false;
        flushPackets();

        // Repress keys based on actual current key state
        for (int keyCode : keys) {
            KeyBinding.setKeyBindState(keyCode, Keyboard.isKeyDown(keyCode));
        }
    };

    private boolean isInventoryPacket(Packet<?> packet) {
        return packet instanceof C0EPacketClickWindow
                || packet instanceof C0DPacketCloseWindow
                || packet instanceof C10PacketCreativeInventoryAction;
    }

    private void flushPackets() {
        if (bufferedPackets.isEmpty()) return;
        isFlushing = true;

        final ArrayList<Packet<?>> snapshot = new ArrayList<>(bufferedPackets);
        bufferedPackets.clear();
        for (Packet<?> packet : snapshot) {
            mc.getNetHandler().addToSendQueue(packet);
        }

        isFlushing = false;
    }


    @Override
    protected void onDisable() {
        flushPackets();
    }
}
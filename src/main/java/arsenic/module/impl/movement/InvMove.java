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

@ModuleInfo(name = "InvMove", category = ModuleCategory.MOVEMENT)
public class InvMove extends Module {

    private boolean pendingFlush = false;
    private boolean justFlushed = false;

    private final ArrayList<Integer> keys = new ArrayList<>(Arrays.asList(
            mc.gameSettings.keyBindJump.getKeyCode(),
            mc.gameSettings.keyBindForward.getKeyCode(),
            mc.gameSettings.keyBindBack.getKeyCode(),
            mc.gameSettings.keyBindLeft.getKeyCode(),
            mc.gameSettings.keyBindRight.getKeyCode()
    ));

    private boolean shouldBuffer() {
        // Only the player inventory with its 2x2 crafting grid. openContainer alone can't say that:
        // it stays the player's ContainerPlayer behind every screen that isn't a container - chat,
        // pause, the click GUI - so it's the screen itself that's checked. Creative uses its own
        // GuiContainerCreative, so it's excluded here too.
        return mc.currentScreen instanceof GuiInventory
                && mc.thePlayer != null
                && mc.thePlayer.openContainer instanceof ContainerPlayer;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventLiving> eventGameLoopListener = event -> {
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
    public final Listener<EventUpdate.Pre> onUpdatePre = event -> {
        if (!pendingFlush)
            return;
        pendingFlush = false;
        justFlushed = true;
        for (int keyCode : keys) {
            KeyBinding.setKeyBindState(keyCode, false);
        }
        // Send the held clicks and close now that the keys are up for this movement tick.
        LagManager.release(getClass());
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventUpdate.Post> onUpdatePost = event -> {
        if (!justFlushed)
            return;
        // One-shot: left set, this re-read the raw keyboard every tick from then on, so typing
        // W in chat walked you forward.
        justFlushed = false;
        for (int keyCode : keys) {
            KeyBinding.setKeyBindState(keyCode, Keyboard.isKeyDown(keyCode));
        }
    };



    @Override
    protected void onDisable() {
        LagManager.release(getClass());
    }
}
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
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import arsenic.utils.io.Keys;

import java.util.List;
import java.util.Arrays;

import static arsenic.utils.lag.LagManager.isHolding;

@ModuleInfo(name = "InvMove", category = ModuleCategory.MOVEMENT)
public class InvMove extends Module {

    private boolean pendingFlush = false;
    private boolean justFlushed = false;

    private final List<KeyMapping> keys = Arrays.asList(
            mc.options.keyJump,
            mc.options.keyUp,
            mc.options.keyDown,
            mc.options.keyLeft,
            mc.options.keyRight
    );

    private boolean shouldBuffer() {
        // Only the player inventory with its 2x2 crafting grid. openContainer alone can't say that:
        // it stays the player's InventoryMenu behind every screen that isn't a container - chat,
        // pause, the click GUI - so it's the screen itself that's checked. Creative uses its own
        // GuiContainerCreative, so it's excluded here too.
        return mc.gui.screen() instanceof InventoryScreen
                && mc.player != null
                && mc.player.containerMenu instanceof InventoryMenu;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventLiving> eventGameLoopListener = event -> {
        if (mc.gui.screen() == null) return;

        if (shouldBuffer()) {
            for (KeyMapping key : keys) {
                key.setDown(Keys.isPhysicallyDown(key));
            }

            if (!isHolding(getClass())) {
                LagManager.acquire(getClass(), p ->
                        p instanceof ServerboundContainerClickPacket
                                || p instanceof ServerboundContainerClosePacket
                                || p instanceof ServerboundSetCreativeModeSlotPacket
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
        if (packet instanceof ServerboundContainerClosePacket) {
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
        for (KeyMapping key : keys) {
            key.setDown(false);
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
        for (KeyMapping key : keys) {
            key.setDown(Keys.isPhysicallyDown(key));
        }
    };



    @Override
    protected void onDisable() {
        LagManager.release(getClass());
    }
}
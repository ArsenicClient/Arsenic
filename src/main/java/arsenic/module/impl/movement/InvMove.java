package arsenic.module.impl.movement;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventLiving;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventUpdate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.utils.io.Keys;
import arsenic.utils.lag.LagManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.Arrays;
import java.util.List;

import static arsenic.utils.lag.LagManager.isHolding;

@ModuleInfo(name = "InvMove", category = ModuleCategory.MOVEMENT, tier = ModuleTier.DEV)
public class InvMove extends Module {

    private boolean pendingFlush = false;
    private boolean flushing = false;

    // built on demand: modules are constructed before Minecraft has created its options
    private List<KeyMapping> keys() {
        return Arrays.asList(
                mc.options.keyJump,
                mc.options.keyUp,
                mc.options.keyDown,
                mc.options.keyLeft,
                mc.options.keyRight
        );
    }

    private boolean shouldBuffer() {
        return mc.gui.screen() instanceof InventoryScreen
                && mc.player != null
                && mc.player.containerMenu instanceof InventoryMenu;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventLiving> eventGameLoopListener = event -> {
        if (pendingFlush) {
            pendingFlush = false;
            flushing = true;
        }
        if (flushing) {
            for (KeyMapping key : keys()) {
                key.setDown(false);
            }
            return;
        }

        if (mc.gui.screen() == null) return;

        if (shouldBuffer()) {
            for (KeyMapping key : keys()) {
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
    public final Listener<EventUpdate.Post> onUpdatePost = event -> {
        if (!flushing)
            return;
        flushing = false;
        LagManager.release(getClass());
        for (KeyMapping key : keys()) {
            key.setDown(Keys.isPhysicallyDown(key));
        }
    };

    @Override
    protected void onDisable() {
        pendingFlush = false;
        flushing = false;
        LagManager.release(getClass());
    }
}

package arsenic.module.impl.movement;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventLiving;
import arsenic.event.impl.EventPacket;
import arsenic.injection.accessor.IMixinEntityPlayerSP;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.EnumProperty;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Movement in the player's own inventory only. A 1.8 client opens its own inventory without telling the server
 * anything but the C16 achievement packet, so once that is dropped the server only learns the inventory is open
 * from the first click.
 * <p>
 * Legit: move freely until the first click, then hold still until the inventory closes.
 * Blatant: move the whole time, hold every click and send them all together with the close packet.
 * <p>
 * Grim's MultiActionsC/D flag a click or close while the server thinks we sprint (on 1.8 there is no input packet,
 * so sprint is all it sees). Held packets therefore wait until a stop-sprint has gone out on an earlier tick, and
 * are sent from the pre-movement hook so they stay ahead of the movement packet (Post). The dropped C16 never shares
 * a tick with a click or close (PacketOrderK).
 */
@ModuleInfo(name = "InvMove", category = ModuleCategory.MOVEMENT, tier = ModuleTier.LEGIT)
public class InvMove extends Module {

    public enum Mode {
        Legit,
        Blatant
    }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Legit);

    private final List<Packet<?>> held = new ArrayList<>();
    /** A click reached the server (or is held for it) since the inventory opened. */
    private boolean clicked;
    /** Legit: movement is frozen until the inventory closes. */
    private boolean frozen;
    /** The own inventory was open on the previous living tick. */
    private boolean wasOpen;
    private boolean flushing;

    private KeyBinding[] movementKeys() {
        return new KeyBinding[]{
                mc.gameSettings.keyBindForward,
                mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindLeft,
                mc.gameSettings.keyBindRight,
                mc.gameSettings.keyBindJump,
                mc.gameSettings.keyBindSprint
        };
    }

    private void setKeys(boolean live) {
        for (KeyBinding key : movementKeys())
            KeyBinding.setKeyBindState(key.getKeyCode(), live && GameSettings.isKeyDown(key));
    }

    private boolean inOwnInventory() {
        return mc.currentScreen instanceof GuiInventory
                && mc.thePlayer.openContainer instanceof ContainerPlayer;
    }

    private boolean serverSprinting() {
        return ((IMixinEntityPlayerSP) mc.thePlayer).getServerSprintState();
    }

    private void flush() {
        flushing = true;
        try {
            for (Packet<?> packet : held)
                mc.getNetHandler().addToSendQueue(packet);
        } finally {
            held.clear();
            flushing = false;
        }
    }

    // Start of onLivingUpdate: before movement input is read and before this tick's movement packet.
    @RequiresPlayer
    @EventLink
    public final Listener<EventLiving> onLiving = event -> {
        boolean open = inOwnInventory();

        if (!held.isEmpty()) {
            boolean waitForClose = open && mode.getValue() == Mode.Blatant;
            if (!waitForClose && !serverSprinting()) {
                // The stop-sprint went out last tick, so the server has it before these.
                flush();
            } else if (!waitForClose) {
                // Stop for one tick; the stop-sprint goes out with this tick's movement packet.
                setKeys(false);
                mc.thePlayer.setSprinting(false);
                wasOpen = open;
                return;
            }
        }

        if (open) {
            setKeys(!frozen);
        } else if (wasOpen || held.isEmpty() && (frozen || clicked)) {
            // Keys held through the close never get a press event, so pick them back up.
            setKeys(true);
            frozen = false;
            clicked = false;
        }
        wasOpen = open;
    };

    @RequiresPlayer
    @EventLink(Priorities.VERY_LOW)
    public final Listener<EventPacket.OutGoing> onPacket = event -> {
        if (flushing || event.isCancelled())
            return;
        Packet<?> packet = event.getPacket();

        // The only packet that tells the server the own inventory opened.
        if (packet instanceof C16PacketClientStatus
                && ((C16PacketClientStatus) packet).getStatus() == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
            event.cancel();
            return;
        }

        if (packet instanceof C0EPacketClickWindow && inOwnInventory()) {
            clicked = true;
            if (mode.getValue() == Mode.Legit)
                frozen = true;
            if (mode.getValue() == Mode.Blatant || !held.isEmpty() || serverSprinting()) {
                held.add(packet);
                event.cancel();
            }
        } else if (packet instanceof C0DPacketCloseWindow && inOwnInventory()) {
            // Never clicked: the server never knew the inventory was open, so it has nothing to close.
            if (!clicked) {
                event.cancel();
            } else if (!held.isEmpty() || serverSprinting()) {
                held.add(packet);
                event.cancel();
            }
        }
    };

    @Override
    protected void onDisable() {
        if (!held.isEmpty() && mc.getNetHandler() != null)
            flush();
        held.clear();
        if (wasOpen)
            setKeys(false);
        clicked = false;
        frozen = false;
        wasOpen = false;
    }
}

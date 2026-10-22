package arsenic.utils.minecraft;

import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemAppleGold;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which kinds of action packets were sent since the last movement packet (that is, in the current
 * tick). Anticheats flag some combinations in one tick, such as an attack together with a slot change or a block
 * or release, so modules ask {@link #bad} before attacking.
 *
 * Packets a module sends on purpose as part of its own timing go through {@link #sendSilently} and are not counted.
 */
public final class BadPacketsManager {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Set<Packet<?>> silent = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static volatile boolean slot, attack, swing, block, inventory;
    /** The server sees an item in use until this player tick (from a use packet until a release or slot change). */
    private static volatile int usingUntil = Integer.MIN_VALUE;
    /** The player entity that use belongs to: a respawn or new world starts a new one, with nothing in use. */
    private static volatile Object usingPlayer;

    /**
     * Whether the server sees an item in use (Grim's slowed-by-using-item): from a use-item packet until a release, a
     * slot change or the use running out. Vanilla never swings or attacks then, and Grim's MultiActions checks flag it,
     * whatever the client's own item-use state says.
     */
    public static boolean serverUsingItem() {
        return mc.thePlayer != null && mc.thePlayer == usingPlayer && mc.thePlayer.ticksExisted < usingUntil;
    }

    public static boolean bad() {
        return bad(true, true, true, true, true);
    }

    /** Whether any of the selected kinds of packet went out this tick. */
    public static boolean bad(boolean slot, boolean attack, boolean swing, boolean block, boolean inventory) {
        return (BadPacketsManager.slot && slot)
                || (BadPacketsManager.attack && attack)
                || (BadPacketsManager.swing && swing)
                || (BadPacketsManager.block && block)
                || (BadPacketsManager.inventory && inventory);
    }

    /** Sends a packet without it counting towards this tick's flags. */
    public static void sendSilently(Packet<?> packet) {
        if (mc.getNetHandler() == null)
            return;
        silent.add(packet);
        mc.getNetHandler().addToSendQueue(packet);
    }

    private static void trackItemUse(Packet<?> packet) {
        if (mc.thePlayer == null)
            return;
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement use = (C08PacketPlayerBlockPlacement) packet;
            ItemStack stack = use.getStack();
            // 255: a use of the held item, not a click on a block
            if (use.getPlacedBlockDirection() != 255 || stack == null)
                return;
            EnumAction action = stack.getItemUseAction();
            boolean uses = action == EnumAction.BLOCK || action == EnumAction.BOW || action == EnumAction.DRINK
                    || (action == EnumAction.EAT && (mc.thePlayer.canEat(false) || stack.getItem() instanceof ItemAppleGold));
            if (uses) {
                usingPlayer = mc.thePlayer;
                usingUntil = mc.thePlayer.ticksExisted + stack.getMaxItemUseDuration();
            }
        } else if (packet instanceof C09PacketHeldItemChange) {
            usingUntil = Integer.MIN_VALUE;
        } else if (packet instanceof C07PacketPlayerDigging
                && ((C07PacketPlayerDigging) packet).getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
            usingUntil = Integer.MIN_VALUE;
        }
    }

    public static void reset() {
        slot = attack = swing = block = inventory = false;
    }

    @EventLink(Priorities.VERY_HIGH)
    public final Listener<EventPacket.OutGoing> onOutgoing = event -> {
        Packet<?> packet = event.getPacket();
        trackItemUse(packet);
        if (silent.remove(packet))
            return;
        if (packet instanceof C09PacketHeldItemChange) {
            slot = true;
        } else if (packet instanceof C0APacketAnimation) {
            swing = true;
        } else if (packet instanceof C02PacketUseEntity) {
            attack = true;
        } else if (packet instanceof C08PacketPlayerBlockPlacement || packet instanceof C07PacketPlayerDigging) {
            block = true;
        } else if (packet instanceof C0EPacketClickWindow
                || packet instanceof C0DPacketCloseWindow
                || (packet instanceof C16PacketClientStatus
                && ((C16PacketClientStatus) packet).getStatus() == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT)) {
            inventory = true;
        } else if (packet instanceof C03PacketPlayer) {
            reset();
        }
    };
}

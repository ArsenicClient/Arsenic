package arsenic.utils.minecraft;

import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which kinds of action packets were sent since the last movement or tick-end packet (that is, in the
 * current tick). Anticheats flag some combinations in one tick, such as an attack together with a slot change or a block
 * or release, so modules ask {@link #bad} before attacking.
 *
 * Packets a module sends on purpose as part of its own timing go through {@link #sendSilently} and are not counted.
 */
public final class BadPacketsManager {

    private static final Minecraft mc = Minecraft.getInstance();
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
        return mc.player != null && mc.player == usingPlayer && mc.player.tickCount < usingUntil;
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
        if (mc.getConnection() == null)
            return;
        silent.add(packet);
        mc.getConnection().send(packet);
    }

    private static void trackItemUse(Packet<?> packet) {
        if (mc.player == null)
            return;
        if (packet instanceof ServerboundUseItemPacket use) {
            ItemStack stack = mc.player.getItemInHand(use.hand());
            if (stack.isEmpty())
                return;
            ItemUseAnimation action = stack.getUseAnimation();
            FoodProperties food = stack.get(DataComponents.FOOD);
            boolean uses = action == ItemUseAnimation.BLOCK || action == ItemUseAnimation.BOW
                    || action == ItemUseAnimation.CROSSBOW || action == ItemUseAnimation.TRIDENT
                    || action == ItemUseAnimation.SPEAR || action == ItemUseAnimation.DRINK
                    || (action == ItemUseAnimation.EAT && (mc.player.canEat(false) || (food != null && food.canAlwaysEat())));
            if (uses) {
                usingPlayer = mc.player;
                usingUntil = mc.player.tickCount + stack.getUseDuration(mc.player);
            }
        } else if (packet instanceof ServerboundSetCarriedItemPacket) {
            usingUntil = Integer.MIN_VALUE;
        } else if (packet instanceof ServerboundPlayerActionPacket action
                && action.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
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
        if (packet instanceof ServerboundSetCarriedItemPacket) {
            slot = true;
        } else if (packet instanceof ServerboundPunchPacket) {
            swing = true;
        } else if (packet instanceof ServerboundAttackPacket || packet instanceof ServerboundInteractPacket) {
            attack = true;
        } else if (packet instanceof ServerboundUseItemOnPacket || packet instanceof ServerboundUseItemPacket
                || packet instanceof ServerboundPlayerActionPacket) {
            block = true;
        } else if (packet instanceof ServerboundContainerClickPacket || packet instanceof ServerboundContainerClosePacket) {
            inventory = true;
        } else if (packet instanceof ServerboundMovePlayerPacket || packet instanceof ServerboundClientTickEndPacket) {
            reset();
        }
    };
}

package arsenic.utils.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Switching the hotbar to an item for a moment, then back. A module owns one and calls {@link #tick()} every tick, which
 * puts the slot back the tick after {@link #use} or when {@link #release()} is called.
 */
public final class HotbarUse {

    private static final Minecraft mc = Minecraft.getInstance();

    private int restore = -1;
    private boolean oneShot;

    /** The hotbar slot (0-8) of the first stack that matches, or -1. */
    public static int find(Predicate<ItemStack> match) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && match.test(stack))
                return i;
        }
        return -1;
    }

    /** The inventory menu slot (9-44, hotbar last) of the first stack that matches, or -1. */
    public static int findMenuSlot(Predicate<ItemStack> match) {
        for (int i = 9; i <= 44; i++) {
            ItemStack stack = ContainerUtils.getItemStack(i);
            if (stack != null && !stack.isEmpty() && match.test(stack))
                return i;
        }
        return -1;
    }

    /** Whether this has moved the hotbar and not yet put it back. Modules return this from isSwappingHotbar. */
    public boolean active() {
        return restore != -1;
    }

    /** Goes back to the slot we left once a one-shot use is over. */
    public void tick() {
        if (oneShot)
            release();
    }

    /** Switches to the slot and keeps it until {@link #release()}. */
    public void select(int slot) {
        if (restore == -1)
            restore = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        oneShot = false;
    }

    /** Switches to the slot for this tick only, for an action the caller does itself. */
    public void switchOnce(int slot) {
        select(slot);
        oneShot = true;
    }

    /** Switches to the slot, uses the item once and goes back next tick. */
    public void use(int slot) {
        switchOnce(slot);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
    }

    public void release() {
        if (restore != -1 && mc.player != null)
            mc.player.getInventory().setSelectedSlot(restore);
        restore = -1;
        oneShot = false;
    }
}

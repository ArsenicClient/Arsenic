package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.io.Keys;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Switches to a crossbow (pulling one out of the inventory if needed) and keeps it loaded by holding use until it is
 * charged. Switching away from the crossbow, or turning the module off, puts your old slot back.
 */
@ModuleInfo(name = "InstantCrossbow", description = "Swaps to a crossbow, keeps it charged and restores your slot", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class InstantCrossbow extends Module {

    public final BooleanProperty fromInventory = new BooleanProperty("From Inventory", true);

    /** Hotbar slot selected before the module switched. */
    private int previousSlot = -1;
    /** Hotbar slot the crossbow sits in. */
    private int crossbowSlot = -1;
    /** Main inventory slot the crossbow was swapped out of, or -1 when it came from the hotbar. */
    private int inventorySlot = -1;
    /** The switch waits for the player to stop using an item (Grim PacketOrder). */
    private boolean pendingSwitch;
    private boolean pressing;

    @Override
    public boolean isSwappingHotbar() {
        return crossbowSlot != -1 || pendingSwitch;
    }

    @Override
    protected void onEnable() {
        reset();
        pendingSwitch = mc.player != null && mc.level != null;
    }

    @Override
    protected void onDisable() {
        boolean alive = mc.player != null && mc.level != null && mc.player.isAlive();
        releaseUse();
        if (alive) {
            if (mc.player.isUsingItem())
                mc.gameMode.releaseUsingItem(mc.player);
            restore();
        }
        reset();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (!mc.player.isAlive()) {
            reset();
            releaseUse();
            return;
        }

        if (pendingSwitch) {
            if (mc.player.isUsingItem())
                return;
            pendingSwitch = false;
            switchToCrossbow();
        }

        // the crossbow was moved or dropped: nothing left to restore
        if (crossbowSlot != -1 && !mc.player.getInventory().getItem(crossbowSlot).is(Items.CROSSBOW))
            reset();

        if (mc.gui.screen() != null) {
            releaseUse();
            return;
        }

        ItemStack held = mc.player.getMainHandItem();
        if (!held.is(Items.CROSSBOW)) {
            releaseUse();
            if (inventorySlot != -1 && crossbowSlot != -1)
                swap(inventorySlot, crossbowSlot);
            reset();
            if (isEnabled())
                setEnabled(false);
            return;
        }

        if (CrossbowItem.isCharged(held)) {
            releaseUse();
            return;
        }

        int pull = Math.max(1, CrossbowItem.getChargeDuration(held, mc.player));
        if (mc.player.isUsingItem() && mc.player.getTicksUsingItem() >= pull) {
            releaseUse();
        } else {
            mc.options.keyUse.setDown(true);
            pressing = true;
        }
    };

    private void switchToCrossbow() {
        if (mc.player.getMainHandItem().is(Items.CROSSBOW))
            return;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.CROSSBOW)) {
                previousSlot = mc.player.getInventory().getSelectedSlot();
                crossbowSlot = i;
                inventorySlot = -1;
                mc.player.getInventory().setSelectedSlot(i);
                return;
            }
        }
        if (!fromInventory.getValue())
            return;
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.CROSSBOW)) {
                int selected = mc.player.getInventory().getSelectedSlot();
                previousSlot = selected;
                crossbowSlot = selected;
                inventorySlot = i;
                swap(i, selected);
                return;
            }
        }
    }

    private void restore() {
        if (inventorySlot != -1 && crossbowSlot != -1) {
            swap(inventorySlot, crossbowSlot);
        } else if (previousSlot >= 0 && previousSlot <= 8) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
    }

    /** Swaps a main inventory slot (9-35, same index in the player menu) with a hotbar slot. */
    private void swap(int menuSlot, int hotbarSlot) {
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, menuSlot, hotbarSlot, ContainerInput.SWAP, mc.player);
    }

    /** Lets go of use, keeping it down only if the player is physically holding it. */
    private void releaseUse() {
        if (!pressing)
            return;
        pressing = false;
        mc.options.keyUse.setDown(mc.gui.screen() == null && Keys.isPhysicallyDown(mc.options.keyUse));
    }

    private void reset() {
        previousSlot = -1;
        crossbowSlot = -1;
        inventorySlot = -1;
        pendingSwitch = false;
    }
}

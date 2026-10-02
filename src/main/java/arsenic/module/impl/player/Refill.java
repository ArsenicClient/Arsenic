package arsenic.module.impl.player;

import arsenic.utils.minecraft.ItemUtils;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ItemStack;

@ModuleInfo(name = "Refill", category = ModuleCategory.PLAYER)
public class Refill extends Module {
    /** Gap between inventory clicks. Lower refills faster and looks less like a human. */
    public final DoubleProperty delay = new DoubleProperty("Delay (ms)", new DoubleValue(0, 500, 80, 10));



    private boolean openInv;
    private final MSTimer timer = new MSTimer();

    @Override
    protected void onEnable() {
        openInv = false;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        boolean inInv = mc.gui.screen() instanceof InventoryScreen;

        if (!inInv && !openInv) {
            int potsInHotbar = countPotsInHotbar();
            if (potsInHotbar <= (int) 0) {
                int slotsToFill = countEmptyOrNonPotHotbarSlots();
                int potsInInv = countPotsInInventory();
                if (slotsToFill > 0 && potsInInv > 0) {
                    openInv = true;
                }
            }
        }

        if (openInv && !inInv) {
            // 1.8 announced the open inventory with an achievement packet; modern servers track
            // the player inventory as always open, so there is nothing to send.
            openInv = false;
        }

        if (inInv && timer.hasTimeElapsed((long) delay.getValue().getInput())) {
            if (doRefill()) timer.reset();
        }
    };

    private boolean doRefill() {
        for (int hotbarSlot = 36; hotbarSlot <= 44; hotbarSlot++) {
            ItemStack stack = mc.player.containerMenu.getSlot(hotbarSlot).getItem();
            if (stack == null || !ItemUtils.isSplashPotion(stack)) {
                int potSlot = findPotionInInventory();
                if (potSlot != -1) {
                    mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, potSlot, 0, net.minecraft.world.inventory.ContainerInput.values()[1], mc.player);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    private int findPotionInInventory() {
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.containerMenu.getSlot(i).getItem();
            if (ItemUtils.isSplashPotion(stack)) return i;
        }
        return -1;
    }

    private int countPotsInHotbar() {
        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (ItemUtils.isSplashPotion(stack)) count++;
        }
        return count;
    }

    private int countPotsInInventory() {
        int count = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (ItemUtils.isSplashPotion(stack)) count++;
        }
        return count;
    }

    private int countEmptyOrNonPotHotbarSlots() {
        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack == null || !ItemUtils.isSplashPotion(stack)) count++;
        }
        return count;
    }
}

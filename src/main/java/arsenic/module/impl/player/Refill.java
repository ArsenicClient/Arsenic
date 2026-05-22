package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C16PacketClientStatus;

@ModuleInfo(name = "Refill", category = ModuleCategory.PLAYER)
public class Refill extends Module {

    public final BooleanProperty autoRefill = new BooleanProperty("Auto Refill", true);
    public final BooleanProperty invOpen = new BooleanProperty("Inv Open", false);
    public final DoubleProperty threshold = new DoubleProperty("Threshold", new DoubleValue(0, 8, 0, 1));
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
        boolean inInv = mc.currentScreen instanceof GuiInventory;

        if (autoRefill.getValue() && !inInv && !openInv) {
            int potsInHotbar = countPotsInHotbar();
            if (potsInHotbar <= (int) threshold.getValue().getInput()) {
                int slotsToFill = countEmptyOrNonPotHotbarSlots();
                int potsInInv = countPotsInInventory();
                if (slotsToFill > 0 && potsInInv > 0) {
                    openInv = true;
                }
            }
        }

        if (openInv && !inInv) {
            mc.thePlayer.sendQueue.addToSendQueue(new C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
            openInv = false;
        }

        if (inInv && (autoRefill.getValue() || invOpen.getValue()) && timer.hasTimeElapsed((long) delay.getValue().getInput())) {
            if (doRefill()) timer.reset();
        }
    };

    private boolean doRefill() {
        for (int hotbarSlot = 36; hotbarSlot <= 44; hotbarSlot++) {
            ItemStack stack = mc.thePlayer.openContainer.getSlot(hotbarSlot).getStack();
            if (stack == null || !(stack.getItem() instanceof ItemPotion)) {
                int potSlot = findPotionInInventory();
                if (potSlot != -1) {
                    mc.playerController.windowClick(mc.thePlayer.openContainer.windowId, potSlot, 0, 1, mc.thePlayer);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    private int findPotionInInventory() {
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.thePlayer.openContainer.getSlot(i).getStack();
            if (stack != null && stack.getItem() instanceof ItemPotion) return i;
        }
        return -1;
    }

    private int countPotsInHotbar() {
        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemPotion) count++;
        }
        return count;
    }

    private int countPotsInInventory() {
        int count = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemPotion) count++;
        }
        return count;
    }

    private int countEmptyOrNonPotHotbarSlots() {
        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null || !(stack.getItem() instanceof ItemPotion)) count++;
        }
        return count;
    }
}

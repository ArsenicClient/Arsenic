
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.timer.MSTimer;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemAppleGold;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemSoup;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@ModuleInfo(name = "AutoSoup", category = ModuleCategory.PLAYER, tier = ModuleTier.EXTRA)
public class AutoSoup extends Module {

    public final DoubleProperty health = new DoubleProperty("Health", new DoubleValue(0, 20, 7, 0.1));

    private final MSTimer actionTimer = new MSTimer();
    private final MSTimer refillTimer = new MSTimer();
    private State state = State.NONE;
    private int originalSlot;
    private boolean inInv;
    private List<Integer> sortedSlots = new ArrayList<>();

    private static final long RETURN_DELAY_MS = 500;
    private static final long EAT_COOLDOWN_MS = 1000;

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        switch (state) {
            case NONE:
                if (mc.currentScreen == null
                        && mc.thePlayer.getHealth() < health.getValue().getInput()
                        && actionTimer.hasTimeElapsed(EAT_COOLDOWN_MS)) {
                    int slot = getEdibleSlot();
                    if (slot != -1) {
                        originalSlot = mc.thePlayer.inventory.currentItem;
                        mc.thePlayer.inventory.currentItem = slot;
                        state = State.SWITCHED;
                    }
                }
                break;
            case SWITCHED:
                ItemStack held = mc.thePlayer.inventory.getCurrentItem();
                if (held != null) {
                    mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
                }
                actionTimer.reset();
                state = State.CLICKED;
                break;
            case CLICKED:
                if (actionTimer.hasTimeElapsed(RETURN_DELAY_MS)) {
                    if (mc.thePlayer.isUsingItem())
                        mc.playerController.onStoppedUsingItem(mc.thePlayer);
                    mc.thePlayer.inventory.currentItem = originalSlot;
                    state = State.NONE;
                }
                break;
        }

        if (mc.currentScreen != null && mc.thePlayer.openContainer instanceof ContainerPlayer) {
            if (!inInv) {
                refillTimer.reset();
                generatePath((ContainerPlayer) mc.thePlayer.openContainer);
                inInv = true;
            }
            if (!sortedSlots.isEmpty() && refillTimer.hasTimeElapsed((long) 75)) {
                mc.playerController.windowClick(mc.thePlayer.openContainer.windowId, sortedSlots.get(0), 0, 1, mc.thePlayer);
                refillTimer.reset();
                sortedSlots.remove(0);
            }
        } else {
            inInv = false;
        }
    };

    @Override
    public boolean isSwappingHotbar() {
        return state == State.SWITCHED || state == State.CLICKED;
    }

    private void generatePath(ContainerPlayer inv) {
        List<Integer> slots = new ArrayList<>();
        int slotsNeeded = 0;
        for (int i = 0; i <= 8; i++) {
            if (mc.thePlayer.inventory.getStackInSlot(i) == null) slotsNeeded++;
        }
        for (int i = 0; i < inv.getInventory().size(); i++) {
            if (!slots.isEmpty() && slots.size() >= slotsNeeded) break;
            ItemStack stack = inv.getInventory().get(i);
            if (stack != null && isEdible(stack) && !(i >= 36 && i <= 44)) {
                slots.add(i);
            }
        }
        this.sortedSlots = slots;
    }

    private int getEdibleSlot() {
        if (mc.thePlayer.getAbsorptionAmount() <= 0) {
            for (int slot = 0; slot <= 8; slot++) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
                if (stack != null && isHead(stack)) return slot;
            }
        }
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && isEdible(stack)) return slot;
        }
        return -1;
    }

    private static boolean isEdible(ItemStack stack) {
        if (stack.getItem() instanceof ItemSoup) return true;
        if (isPotato(stack)) return true;
        String name = stack.getDisplayName().toLowerCase(Locale.ROOT);
        if (isHead(stack)) return true;
        if (stack.getItem() instanceof ItemAppleGold) return name.contains("head");
        if (stack.getItem() instanceof ItemMonsterPlacer) return name.contains("chicken");
        return false;
    }

    private static boolean isPotato(ItemStack stack) {
        return stack.getItem() == Items.potato || stack.getItem() == Items.baked_potato;
    }

    private static boolean isHead(ItemStack stack) {
        return (stack.getItem() instanceof ItemSkull || stack.getItem() instanceof ItemAppleGold)
                && stack.getDisplayName().toLowerCase(Locale.ROOT).contains("golden head");
    }

    private enum State {
        NONE, SWITCHED, CLICKED
    }
}

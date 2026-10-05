package arsenic.module.impl.player;

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
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

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
                if (mc.gui.screen() == null
                        && mc.player.getHealth() < health.getValue().getInput()
                        && actionTimer.hasTimeElapsed(EAT_COOLDOWN_MS)) {
                    int slot = getEdibleSlot();
                    if (slot != -1) {
                        originalSlot = mc.player.getInventory().getSelectedSlot();
                        mc.player.getInventory().setSelectedSlot(slot);
                        state = State.SWITCHED;
                    }
                }
                break;
            case SWITCHED:
                ItemStack held = mc.player.getMainHandItem();
                if (held != null) {
                    mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                }
                actionTimer.reset();
                state = State.CLICKED;
                break;
            case CLICKED:
                if (actionTimer.hasTimeElapsed(RETURN_DELAY_MS)) {
                    if (mc.player.isUsingItem())
                        mc.gameMode.releaseUsingItem(mc.player);
                    mc.player.getInventory().setSelectedSlot(originalSlot);
                    state = State.NONE;
                }
                break;
        }

        if (mc.gui.screen() != null && mc.player.containerMenu instanceof InventoryMenu) {
            if (!inInv) {
                refillTimer.reset();
                generatePath((InventoryMenu) mc.player.containerMenu);
                inInv = true;
            }
            if (!sortedSlots.isEmpty() && refillTimer.hasTimeElapsed((long) 75)) {
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, sortedSlots.get(0), 0, net.minecraft.world.inventory.ContainerInput.values()[1], mc.player);
                refillTimer.reset();
                sortedSlots.remove(0);
            }
        } else {
            inInv = false;
        }
    };

    public boolean isSwapping() {
        return state == State.SWITCHED || state == State.CLICKED;
    }

    private void generatePath(InventoryMenu inv) {
        List<Integer> slots = new ArrayList<>();
        int slotsNeeded = 0;
        for (int i = 0; i <= 8; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) slotsNeeded++;
        }
        for (int i = 0; i < inv.getItems().size(); i++) {
            if (!slots.isEmpty() && slots.size() >= slotsNeeded) break;
            ItemStack stack = inv.getItems().get(i);
            if (stack != null && isEdible(stack) && !(i >= 36 && i <= 44)) {
                slots.add(i);
            }
        }
        this.sortedSlots = slots;
    }

    private int getEdibleSlot() {
        if (mc.player.getAbsorptionAmount() <= 0) {
            for (int slot = 0; slot <= 8; slot++) {
                ItemStack stack = mc.player.getInventory().getItem(slot);
                if (stack != null && isHead(stack)) return slot;
            }
        }
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack != null && isEdible(stack)) return slot;
        }
        return -1;
    }

    private static boolean isEdible(ItemStack stack) {
        if (isSoup(stack)) return true;
        if (isPotato(stack)) return true;
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        if (isHead(stack)) return true;
        if (stack.is(net.minecraft.world.item.Items.GOLDEN_APPLE)) return name.contains("head");
        if (stack.getItem() instanceof net.minecraft.world.item.SpawnEggItem) return name.contains("chicken");
        return false;
    }

    private static boolean isSoup(ItemStack stack) {
        return stack.is(net.minecraft.world.item.Items.MUSHROOM_STEW) || stack.is(net.minecraft.world.item.Items.BEETROOT_SOUP)
                || stack.is(net.minecraft.world.item.Items.RABBIT_STEW) || stack.is(net.minecraft.world.item.Items.SUSPICIOUS_STEW);
    }

    private static boolean isPotato(ItemStack stack) {
        return stack.getItem() == Items.POTATO || stack.getItem() == Items.BAKED_POTATO;
    }

    private static boolean isHead(ItemStack stack) {
        return (stack.is(net.minecraft.tags.ItemTags.SKULLS) || stack.is(net.minecraft.world.item.Items.GOLDEN_APPLE))
                && stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains("golden head");
    }

    private enum State {
        NONE, SWITCHED, CLICKED
    }
}

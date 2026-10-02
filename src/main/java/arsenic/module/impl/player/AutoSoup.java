package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@ModuleInfo(name = "AutoSoup", category = ModuleCategory.PLAYER)
public class AutoSoup extends Module {

    public final DoubleProperty health = new DoubleProperty("Health", new DoubleValue(0, 20, 7, 0.1));

    private final MSTimer actionTimer = new MSTimer();
    private final MSTimer refillTimer = new MSTimer();
    private State state = State.WAITING;
    private int originalSlot;
    private boolean inInv;
    private List<Integer> sortedSlots = new ArrayList<>();

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        boolean shouldEat = (mc.gui.screen() == null)
                && mc.player.getHealth() < health.getValue().getInput()
                && actionTimer.hasTimeElapsed(1);

        if (shouldEat) {
            switch (state) {
                case WAITING:
                    actionTimer.reset();
                    break;
                case NONE:
                    int slot = getEdibleSlot();
                    if (slot == -1) return;
                    originalSlot = mc.player.getInventory().getSelectedSlot();
                    mc.player.getInventory().setSelectedSlot(slot);
                    actionTimer.reset();
                    break;
                case SWITCHED:
                    KeyMapping.click(((arsenic.injection.accessor.IMixinKeyMapping) mc.options.keyUse).getBoundKey());
                    actionTimer.reset();
                    break;
                case CLICKED:
                    mc.player.getInventory().setSelectedSlot(originalSlot);
                    actionTimer.reset();
                    break;
            }
            state = state.next();
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
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack != null && isEdible(stack)) return slot;
        }
        return -1;
    }

    /**
     * Soup, a golden apple reskinned/renamed as a "Golden Head", or a chicken spawn egg -
     * BedWars' regen items, both right-clicked to use like any other item.
     */
    private static boolean isEdible(ItemStack stack) {
        if (isSoup(stack)) return true;
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        if (stack.is(net.minecraft.world.item.Items.GOLDEN_APPLE)) return name.contains("head");
        if (stack.getItem() instanceof net.minecraft.world.item.SpawnEggItem) return name.contains("chicken");
        return false;
    }

    private static boolean isSoup(ItemStack stack) {
        return stack.is(net.minecraft.world.item.Items.MUSHROOM_STEW) || stack.is(net.minecraft.world.item.Items.BEETROOT_SOUP)
                || stack.is(net.minecraft.world.item.Items.RABBIT_STEW) || stack.is(net.minecraft.world.item.Items.SUSPICIOUS_STEW);
    }

    private enum State {
        WAITING, NONE, SWITCHED, CLICKED;

        private static final State[] vals = values();

        public State next() {
            return vals[(this.ordinal() + 1) % vals.length];
        }
    }
}

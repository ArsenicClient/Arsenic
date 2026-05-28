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
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemSoup;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "AutoSoup", category = ModuleCategory.PLAYER)
public class AutoSoup extends Module {

    public final RangeProperty delay = new RangeProperty("Delay (ms)", new RangeValue(0, 200, 50, 100, 1));
    public final RangeProperty cooldown = new RangeProperty("Cooldown (ms)", new RangeValue(0, 5000, 1000, 1200, 1));
    public final DoubleProperty health = new DoubleProperty("Health", new DoubleValue(0, 20, 7, 0.1));
    public final BooleanProperty invConsume = new BooleanProperty("Consume in inv", false);
    public final BooleanProperty autoRefill = new BooleanProperty("Auto refill", true);
    public final RangeProperty invDelay = new RangeProperty("Inv Delay (ms)", new RangeValue(0, 200, 50, 100, 1));
    public final RangeProperty refillDelay = new RangeProperty("Refill Delay (ms)", new RangeValue(0, 200, 50, 100, 1));

    private final MSTimer actionTimer = new MSTimer();
    private final MSTimer refillTimer = new MSTimer();
    private State state = State.WAITING;
    private int originalSlot;
    private boolean inInv;
    private List<Integer> sortedSlots = new ArrayList<>();

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        boolean shouldEat = (invConsume.getValue() || mc.currentScreen == null)
                && mc.thePlayer.getHealth() < health.getValue().getInput()
                && actionTimer.hasTimeElapsed(1);

        if (shouldEat) {
            switch (state) {
                case WAITING:
                    actionTimer.reset();
                    break;
                case NONE:
                    int slot = getSoupSlot();
                    if (slot == -1) return;
                    originalSlot = mc.thePlayer.inventory.currentItem;
                    mc.thePlayer.inventory.currentItem = slot;
                    actionTimer.reset();
                    break;
                case SWITCHED:
                    KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
                    actionTimer.reset();
                    break;
                case CLICKED:
                    mc.thePlayer.inventory.currentItem = originalSlot;
                    actionTimer.reset();
                    break;
            }
            state = state.next();
        }

        if (autoRefill.getValue() && mc.currentScreen != null && mc.thePlayer.openContainer instanceof ContainerPlayer) {
            if (!inInv) {
                refillTimer.reset();
                generatePath((ContainerPlayer) mc.thePlayer.openContainer);
                inInv = true;
            }
            if (!sortedSlots.isEmpty() && refillTimer.hasTimeElapsed((long) refillDelay.getValue().getMin())) {
                mc.playerController.windowClick(mc.thePlayer.openContainer.windowId, sortedSlots.get(0), 0, 1, mc.thePlayer);
                refillTimer.reset();
                sortedSlots.remove(0);
            }
        } else {
            inInv = false;
        }
    };

    private void generatePath(ContainerPlayer inv) {
        List<Integer> slots = new ArrayList<>();
        int slotsNeeded = 0;
        for (int i = 0; i <= 8; i++) {
            if (mc.thePlayer.inventory.getStackInSlot(i) == null) slotsNeeded++;
        }
        for (int i = 0; i < inv.getInventory().size(); i++) {
            if (!slots.isEmpty() && slots.size() >= slotsNeeded) break;
            ItemStack stack = inv.getInventory().get(i);
            if (stack != null && stack.getItem() instanceof ItemSoup && !(i >= 36 && i <= 44)) {
                slots.add(i);
            }
        }
        this.sortedSlots = slots;
    }

    private int getSoupSlot() {
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof ItemSoup) return slot;
        }
        return -1;
    }

    private enum State {
        WAITING, NONE, SWITCHED, CLICKED;

        private static final State[] vals = values();

        public State next() {
            return vals[(this.ordinal() + 1) % vals.length];
        }
    }
}

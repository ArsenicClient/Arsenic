package arsenic.module.impl.player;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventDisplayGuiScreen;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.ContainerUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.timer.Timer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.*;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "InvManager", category = ModuleCategory.PLAYER)
public class InvManager extends Module {

    public final RangeProperty startDelay = new RangeProperty("StartDelay", new RangeValue(0, 500, 75, 150, 1));
    public final RangeProperty delay = new RangeProperty("Delay", new RangeValue(0, 500, 75, 150, 1));
    public final BooleanProperty closeOnFinish = new BooleanProperty("Close on finish", true);
    public final BooleanProperty drop = new BooleanProperty("Drop", true);

    private Timer timer = new Timer();
    private boolean shouldSteal;
    private List<Action> path;

    private Runnable nextAction;

    private final Runnable closeAction = () -> {
        if(closeOnFinish.getValue()) {
            mc.thePlayer.closeScreen();
            mc.currentScreen = null;
        }
    };

    private final Runnable executeAction = () -> {
        if(!path.isEmpty()) {
            Action action = path.remove(0);

            //skips the switch statement and moves onto the next action
            if(!drop.getValue() && action.type == ActionType.DROP) {
                getExecuteAction().run();
                return;
            }

            switch(action.type) {
                case DROP:
                    ContainerUtils.drop(action.slot);
                    break;
                case SWAP:
                    ContainerUtils.swap(action.slot, action.targetSlot);
                    break;
                case CLICK:
                    ContainerUtils.click(action.slot);
                    break;
            }

            timer.setCooldown((int) delay.getValue().getRandomInRange());
            nextAction = getExecuteAction();
        } else {
            timer.setCooldown((int) delay.getValue().getRandomInRange());
            nextAction = closeAction;
        }
    };

    private Runnable getExecuteAction() {
        return executeAction;
    }

    @EventLink
    public final Listener<EventDisplayGuiScreen> guiDisplayListener = event -> {
        shouldSteal = false;
        if(mc.thePlayer == null || event.getGuiScreen() == null || mc.thePlayer.openContainer == null)
            return;
        if(mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer || !(event.getGuiScreen() instanceof GuiContainer))
            return;

        ContainerPlayer container = (ContainerPlayer) mc.thePlayer.openContainer;
        path = generatePath(container);
        shouldSteal = true;
        timer.start();
        timer.setCooldown((int) startDelay.getValue().getRandomInRange());
        nextAction = executeAction;
    };

    @EventLink
    public final Listener<EventTick> tickListener = event -> {
        if(!shouldSteal)
            return;
        if(!timer.firstFinish())
            return;
        nextAction.run();
        timer.start();
    };

    @Override
    protected void onDisable() {
        shouldSteal = false;
    }

    public List<Action> generatePath(ContainerPlayer inv) {
        ArrayList<Action> actions = new ArrayList<>();

        // Track best items for each slot
        int[] bestItemSlots = new int[9]; // Hotbar slots 1-9 (indices 0-8 map to slots 1-9)
        for(int i = 0; i < 9; i++) {
            bestItemSlots[i] = -1;
        }
        int[] bestArmorSlots = new int[4]; // Armor slots
        for(int i = 0; i < 4; i++) {
            bestArmorSlots[i] = -1;
        }

        // Find best items using ContainerUtils

        // Slot 1: Sword
        bestItemSlots[0] = ContainerUtils.getBestWeapon();

        // Slot 2: Projectiles (eggs/snowballs)
        bestItemSlots[1] = ContainerUtils.getMostProjectiles();

        // Slot 3: Blocks
        bestItemSlots[2] = ContainerUtils.getMostBlocks();

        // Slot 4: Ender Pearls
        bestItemSlots[3] = ContainerUtils.getBiggestStack(Items.ender_pearl);

        // Slot 5: Golden Apples
        bestItemSlots[4] = ContainerUtils.getBiggestStack(Items.golden_apple);

        // Slot 6: Bow
        bestItemSlots[5] = ContainerUtils.getBestBow();

        // Slots 6-9: Best tool items (pickaxe, axe, shovel)
        bestItemSlots[6] = ContainerUtils.getBestTool(ItemPickaxe.class);
        bestItemSlots[8] = ContainerUtils.getBestTool(ItemSpade.class);
        Arsenic.getArsenic().getLogger().info(bestItemSlots[1] + "");
        bestItemSlots[7] = ContainerUtils.getBestTool(ItemAxe.class);

        // Find best armor
        for(int i = 0; i < 4; i++) {
            bestArmorSlots[i] = ContainerUtils.getBestArmor(i);
        }

        // Phase 1: Handle armor first
        for(int i = 0; i < 4; i++) {
            int curArmorSlot = i + 5; // Armor slots are 5-8
            int bestArmorSlot = bestArmorSlots[i];

            if(bestArmorSlot != -1 && bestArmorSlot != curArmorSlot) {
                ItemStack currentArmor = ContainerUtils.getItemStack(curArmorSlot);
                if(currentArmor != null) {
                    // Click to unequip current armor first
                    actions.add(new Action(ActionType.CLICK, curArmorSlot));
                }
                // Click to equip new armor
                actions.add(new Action(ActionType.CLICK, bestArmorSlot));
            }
        }

        //Phase 2: Drop items that should be removed
        for(int i = 9; i < 45; i++) {
            ItemStack stack = ContainerUtils.getItemStack(i);
            if(stack == null) continue;

            // Check if this item is one of our best items
            boolean isBestItem = false;
            for(int j = 0; j < bestItemSlots.length; j++) {
                if(bestItemSlots[j] == i) {
                    isBestItem = true;
                    break;
                }
            }
            for(int j = 0; j < bestArmorSlots.length; j++) {
                if(bestArmorSlots[j] == i) {
                    isBestItem = true;
                    break;
                }
            }

            // If not a best item and can be dropped, drop it
            if(!isBestItem) {
                actions.add(new Action(ActionType.DROP, i));
            }
        }

        // Phase 2: Move best items to their preferred hotbar slots
        // Now move items in order: Sword -> Projectiles -> Blocks -> Pearls -> Gapples -> Tools
        for(int i = 0; i < bestItemSlots.length; i++) {
            int bestSlot = bestItemSlots[i];
            int targetInvSlot = i + 36;
            if(bestSlot != -1 && bestSlot != targetInvSlot) {
                actions.add(new Action(ActionType.SWAP, bestSlot, i));
                for(int j = i; j < bestItemSlots.length; j++) {
                    if(bestItemSlots[j] == -1)
                        continue;
                    if(bestItemSlots[j] == targetInvSlot) {
                        bestItemSlots[j] = bestSlot;
                    }
                }
            }
        }

        return actions;
    }

    private static class Action {
        ActionType type;
        int slot;
        int targetSlot;

        public Action(ActionType type, int slot) {
            this.type = type;
            this.slot = slot;
            this.targetSlot = -1;
        }

        public Action(ActionType type, int slot, int targetSlot) {
            this.type = type;
            this.slot = slot;
            this.targetSlot = targetSlot;
        }
    }

    private enum ActionType {
        DROP,
        SWAP,
        CLICK
    }
}
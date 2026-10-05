package arsenic.module.impl.player;

import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventDisplayGuiScreen;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
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
    public final DoubleProperty delay = new DoubleProperty("Delay (ms)", new DoubleValue(0, 500, 110, 10));



    private Timer timer = new Timer();
    private boolean shouldSteal;
    private List<Action> path;

    private Runnable nextAction;

    private final Runnable closeAction = () -> {
        if(true) {
            mc.thePlayer.closeScreen();
            mc.currentScreen = null;
        }
    };

    private final Runnable executeAction = () -> {
        if(!path.isEmpty()) {
            Action action = path.remove(0);

            if(!true && action.type == ActionType.DROP) {
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

            timer.setCooldown((int) delay.getValue().getInput());
            nextAction = getExecuteAction();
        } else {
            timer.setCooldown((int) delay.getValue().getInput());
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
        timer.setCooldown((int) delay.getValue().getInput());
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

        int[] bestItemSlots = new int[9];
        for(int i = 0; i < 9; i++) {
            bestItemSlots[i] = -1;
        }
        int[] bestArmorSlots = new int[4];
        for(int i = 0; i < 4; i++) {
            bestArmorSlots[i] = -1;
        }


        bestItemSlots[0] = ContainerUtils.getBestWeapon();

        bestItemSlots[1] = ContainerUtils.getMostProjectiles();

        bestItemSlots[2] = ContainerUtils.getMostBlocks();

        bestItemSlots[3] = ContainerUtils.getBiggestStack(Items.ender_pearl);

        bestItemSlots[4] = ContainerUtils.getBiggestStack(Items.golden_apple);

        bestItemSlots[5] = ContainerUtils.getBestBow();

        bestItemSlots[6] = ContainerUtils.getBestTool(ItemPickaxe.class);
        bestItemSlots[8] = ContainerUtils.getBestTool(ItemSpade.class);
        Arsenic.getArsenic().getLogger().info(bestItemSlots[1] + "");
        bestItemSlots[7] = ContainerUtils.getBestTool(ItemAxe.class);

        for(int i = 0; i < 4; i++) {
            bestArmorSlots[i] = ContainerUtils.getBestArmor(i);
        }

        for(int i = 0; i < 4; i++) {
            int curArmorSlot = i + 5;
            int bestArmorSlot = bestArmorSlots[i];

            if(bestArmorSlot != -1 && bestArmorSlot != curArmorSlot) {
                ItemStack currentArmor = ContainerUtils.getItemStack(curArmorSlot);
                if(currentArmor != null) {
                    actions.add(new Action(ActionType.CLICK, curArmorSlot));
                }
                actions.add(new Action(ActionType.CLICK, bestArmorSlot));
            }
        }

        for(int i = 9; i < 45; i++) {
            ItemStack stack = ContainerUtils.getItemStack(i);
            if(stack == null) continue;

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

            if(!isBestItem) {
                actions.add(new Action(ActionType.DROP, i));
            }
        }

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
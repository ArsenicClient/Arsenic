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
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;

@ModuleInfo(name = "AutoEat", category = ModuleCategory.PLAYER, dev = true)
public class AutoEat extends Module {

    public final DoubleProperty healthThreshold = new DoubleProperty("Health Threshold", new DoubleValue(1, 20, 15, 0.5));
    public final DoubleProperty hungerThreshold = new DoubleProperty("Hunger Threshold", new DoubleValue(1, 20, 15, 0.5));
    public final BooleanProperty preferGapples = new BooleanProperty("Prefer Gapples", true);
    public final BooleanProperty preferGolden = new BooleanProperty("Prefer Golden Foods", true);
    public final BooleanProperty checkInventory = new BooleanProperty("Check Inventory", true);

    private boolean isEating;
    private int eatingSlot = -1;

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (shouldEat()) {
            if (!isEating) {
                findAndEatFood();
            }
        } else if (isEating) {
            stopEating();
        }

        if (isEating && !mc.thePlayer.isUsingItem()) {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem());
        }
    };

    private boolean shouldEat() {
        float health = mc.thePlayer.getHealth();
        int foodLevel = mc.thePlayer.getFoodStats().getFoodLevel();
        return health <= healthThreshold.getValue().getInput() || foodLevel <= hungerThreshold.getValue().getInput();
    }

    private void findAndEatFood() {
        int bestSlot = findBestFoodSlot();
        if (bestSlot != -1) {
            eatingSlot = bestSlot;
            isEating = true;
            mc.thePlayer.inventory.currentItem = eatingSlot;
            if (mc.thePlayer.isUsingItem()) mc.thePlayer.stopUsingItem();
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem());
        }
    }

    private int findBestFoodSlot() {
        int bestSlot = -1;
        double bestScore = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemFood) {
                double score = calculateFoodScore(stack);
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = i;
                }
            }
        }

        if (bestSlot == -1 && checkInventory.getValue()) {
            for (int i = 9; i < mc.thePlayer.inventory.mainInventory.length; i++) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                if (stack != null && stack.getItem() instanceof ItemFood) {
                    double score = calculateFoodScore(stack);
                    if (score > bestScore) {
                        bestScore = score;
                        bestSlot = i;
                    }
                }
            }
        }

        return bestSlot;
    }

    private double calculateFoodScore(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemFood)) return -1;
        ItemFood food = (ItemFood) stack.getItem();
        double score = food.getHealAmount(stack) * 2 + food.getSaturationModifier(stack) * 4;

        if (preferGolden.getValue() && stack.getDisplayName().toLowerCase().contains("golden"))
            score += 10;
        if (preferGapples.getValue() && stack.getItem().getUnlocalizedName().contains("apple"))
            score += 15;

        return score;
    }

    private void stopEating() {
        if (isEating && mc.thePlayer.isUsingItem())
            mc.thePlayer.stopUsingItem();
        isEating = false;
        eatingSlot = -1;
    }

    @Override
    protected void onDisable() {
        stopEating();
    }
}

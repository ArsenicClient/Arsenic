package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(name = "AutoArmour", category = ModuleCategory.PLAYER)
public class AutoArmour extends Module {

    public final RangeProperty openDelay = new RangeProperty("Open Delay", new RangeValue(0, 1000, 250, 450, 1));
    public final RangeProperty delay = new RangeProperty("Delay", new RangeValue(0, 1000, 150, 250, 1));
    public final BooleanProperty dropWorse = new BooleanProperty("Drop Worse", true);
    public final BooleanProperty invOpen = new BooleanProperty("Inv Open", true);

    private final MSTimer timer = new MSTimer();

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (invOpen.getValue() && !(mc.currentScreen instanceof GuiInventory)) return;
        if (!timer.hasTimeElapsed(1)) return;

        int[] bestSlots = new int[4];
        float[] bestValues = new float[4];

        for (int i = 0; i < 4; i++) {
            bestSlots[i] = -1;
            ItemStack stack = mc.thePlayer.inventory.armorItemInSlot(i);
            bestValues[i] = (stack != null && stack.getItem() instanceof ItemArmor) ? getArmorValue((ItemArmor) stack.getItem(), stack) : -1;
        }

        for (int i = 9; i < 45; i++) {
            ItemStack stack = mc.thePlayer.openContainer.getSlot(i).getStack();
            if (stack != null && stack.getItem() instanceof ItemArmor) {
                ItemArmor armor = (ItemArmor) stack.getItem();
                int armorType = 3 - armor.armorType;
                float value = getArmorValue(armor, stack);
                if (value > bestValues[armorType]) {
                    bestSlots[armorType] = i;
                    bestValues[armorType] = value;
                }
            }
        }

        boolean acted = false;
        for (int i = 0; i < 4; i++) {
            if (bestSlots[i] != -1) {
                ItemStack equipped = mc.thePlayer.inventory.armorItemInSlot(i);
                if (equipped != null && dropWorse.getValue()) {
                    mc.playerController.windowClick(mc.thePlayer.openContainer.windowId, 8 - i, 1, 4, mc.thePlayer);
                    acted = true;
                    break;
                } else {
                    mc.playerController.windowClick(mc.thePlayer.openContainer.windowId, bestSlots[i], 0, 1, mc.thePlayer);
                    acted = true;
                    break;
                }
            }
        }

        if (!acted && dropWorse.getValue()) {
            for (int i = 9; i < 45; i++) {
                ItemStack stack = mc.thePlayer.openContainer.getSlot(i).getStack();
                if (stack != null && stack.getItem() instanceof ItemArmor) {
                    ItemArmor armor = (ItemArmor) stack.getItem();
                    int armorType = 3 - armor.armorType;
                    float value = getArmorValue(armor, stack);
                    if (value < bestValues[armorType]) {
                        mc.playerController.windowClick(mc.thePlayer.openContainer.windowId, i, 1, 4, mc.thePlayer);
                        acted = true;
                        break;
                    }
                }
            }
        }

        if (acted) {
            timer.reset();
        }
    };

    private float getArmorValue(ItemArmor armor, ItemStack stack) {
        float value = armor.damageReduceAmount;
        int prot = EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack);
        value += prot * 0.04f;
        return value;
    }
}

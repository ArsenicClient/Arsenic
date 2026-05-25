package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMouse;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;

@ModuleInfo(name = "Packet Consume", category = ModuleCategory.PLAYER)
public class PacketConsume extends Module {

    public final BooleanProperty food = new BooleanProperty("Food", true);
    public final BooleanProperty potion = new BooleanProperty("Potion", true);
    public final BooleanProperty instant = new BooleanProperty("Instant", false);

    private boolean consuming;
    private ItemStack consumeStack;

    @Override
    protected void onDisable() {
        if (consuming) {
            releaseKey();
            if (mc.thePlayer.isUsingItem()) {
                mc.thePlayer.stopUsingItem();
            }
            consuming = false;
            consumeStack = null;
        }
    }

    @EventLink
    public final Listener<EventMouse.Down> onRightClick = event -> {
        if (event.button != 1) return;
        if (consuming) return;
        if (mc.thePlayer == null || mc.thePlayer.getHeldItem() == null) return;

        ItemStack stack = mc.thePlayer.getHeldItem();
        EnumAction action = stack.getItem().getItemUseAction(stack);

        boolean isEdible = action == EnumAction.EAT && food.getValue();
        boolean isDrinkable = action == EnumAction.DRINK && potion.getValue();
        if (!isEdible && !isDrinkable) return;

        consuming = true;
        consumeStack = stack;

        if (instant.getValue()) {
            mc.getNetHandler().addToSendQueue(new C08PacketPlayerBlockPlacement(new BlockPos(-1, -1, -1), 255, stack, 0.0F, 0.0F, 0.0F));
            mc.thePlayer.stopUsingItem();
            mc.thePlayer.setItemInUse(stack, 0);
        } else {
            KeyBinding_setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (!consuming) return;

        if (!mc.thePlayer.isUsingItem()) {
            releaseKey();
            if (instant.getValue()) {
                finishConsumption(consumeStack);
            }
            consuming = false;
            consumeStack = null;
        } else if (!instant.getValue() && mc.thePlayer.getItemInUseCount() <= 1) {
            releaseKey();
        }
    };

    private void finishConsumption(ItemStack stack) {
        if (stack == null) return;

        if (stack.getItem() instanceof ItemFood && food.getValue()) {
            ItemFood itemFood = (ItemFood) stack.getItem();
            mc.thePlayer.getFoodStats().addStats(itemFood.getHealAmount(stack), itemFood.getSaturationModifier(stack));
        }

        if (stack.getItem() instanceof ItemPotion && potion.getValue()) {
            ItemPotion itemPotion = (ItemPotion) stack.getItem();
            java.util.Collection<PotionEffect> effects = itemPotion.getEffects(stack);
            if (effects != null) {
                for (PotionEffect effect : effects) {
                    mc.thePlayer.addPotionEffect(effect);
                }
            }
        }
    }

    private void releaseKey() {
        KeyBinding_setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
    }

    private void KeyBinding_setKeyBindState(int key, boolean held) {
        net.minecraft.client.settings.KeyBinding.setKeyBindState(key, held);
    }
}

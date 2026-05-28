package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMouse;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventShader;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;

import java.awt.*;

@ModuleInfo(name = "Packet Consume", category = ModuleCategory.PLAYER)
public class PacketConsume extends Module {

    public final BooleanProperty food = new BooleanProperty("Food", true);
    public final BooleanProperty potion = new BooleanProperty("Potion", true);
    public final BooleanProperty instant = new BooleanProperty("Instant", false);

    private boolean consuming;
    private ItemStack consumeStack;
    private long consumeStartTime;

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
        consumeStartTime = System.currentTimeMillis();

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

    private int getBarX(ScaledResolution sr) {
        return (sr.getScaledWidth() - 120) / 2;
    }

    private int getBarY(ScaledResolution sr) {
        return sr.getScaledHeight() / 2 + 20;
    }

    @EventLink
    public final Listener<EventShader.Blur> blurListener = event -> {
        if (!consuming || instant.getValue()) return;
        ScaledResolution sr = new ScaledResolution(mc);
        int x = getBarX(sr);
        int y = getBarY(sr);
        Gui.drawRect(x, y, x + 120, y + 6, -1);
    };

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        if (!consuming || instant.getValue()) return;

        ScaledResolution sr = event.getSr();
        int x = getBarX(sr);
        int y = getBarY(sr);

        float progress = Math.min(1.0f, (System.currentTimeMillis() - consumeStartTime) / 1600.0f);
        int fillWidth = (int) (120 * progress);

        GlStateManager.enableBlend();
        Gui.drawRect(x, y, x + 120, y + 6, 0x90000000);
        int color = RenderUtils.interpolateColours(new Color(0xFFFF4444), new Color(0xFF44FF44), progress);
        Gui.drawRect(x + 1, y + 1, x + fillWidth - 1, y + 5, color);
        GlStateManager.disableBlend();
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

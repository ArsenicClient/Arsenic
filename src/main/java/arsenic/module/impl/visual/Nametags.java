package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.gui.design.M3;
import arsenic.gui.hud.HudStyle;
import arsenic.injection.accessor.IMixinRenderManager;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.MathUtils;
import arsenic.utils.render.DrawUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.StringUtils;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "Nametags", category = ModuleCategory.RENDER, hidden = true, tier = ModuleTier.VISUAL)
public class Nametags extends Module {

    public final DoubleProperty scaleProperty = new DoubleProperty("Scale", new DoubleValue(0.5, 3.0, 1.0, 0.1));
    public final BooleanProperty showHealth = new BooleanProperty("Show Health", true);
    public final BooleanProperty showDistance = new BooleanProperty("Show Distance", true);
    public final BooleanProperty showArmor = new BooleanProperty("Show Armor", true);
    public final BooleanProperty showEnchants = new BooleanProperty("Show Enchants", true);
    public final BooleanProperty showInvisibles = new BooleanProperty("Show Invisibles", true);

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> renderListener = event -> {
        IMixinRenderManager renderManager = (IMixinRenderManager) mc.getRenderManager();
        double renderPosX = renderManager.getRenderPosX();
        double renderPosY = renderManager.getRenderPosY();
        double renderPosZ = renderManager.getRenderPosZ();

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (!shouldRenderNametag(player)) {
                continue;
            }

            double x = interpolate(player.lastTickPosX, player.posX, event.partialTicks) - renderPosX;
            double y = interpolate(player.lastTickPosY, player.posY, event.partialTicks) - renderPosY;
            double z = interpolate(player.lastTickPosZ, player.posZ, event.partialTicks) - renderPosZ;

            renderNametag(player, x, y, z);
        }
    };

    private boolean shouldRenderNametag(EntityPlayer player) {
        if (player == mc.thePlayer || player.isDead)
            return false;
        if (AntiBot.isBot(player))
            return false;
        if (player.isInvisible() && !showInvisibles.getValue())
            return false;
        return mc.thePlayer.getDistanceToEntity(player) <= 250.0f;
    }

    private double interpolate(double prev, double current, float partialTicks) {
        return prev + (current - prev) * partialTicks;
    }

    private void renderNametag(EntityPlayer player, double x, double y, double z) {
        FontRendererExtension<?> labelFr = HudStyle.label();
        FontRendererExtension<?> bodyFr = HudStyle.body();
        if (labelFr == null || bodyFr == null)
            return;

        float distance = mc.thePlayer.getDistanceToEntity(player);
        float scale = (float) (Math.max(1.25, distance * 0.14) * scaleProperty.getValue().getInput() * 0.024);

        boolean isFriend = Arsenic.getArsenic().getFriendManager().isFriend(player);
        String name = player.getDisplayName().getFormattedText();
        if (name == null || name.trim().isEmpty()) {
            name = StringUtils.stripControlCodes(player.getName());
        }

        float health = Math.max(0f, player.getHealth());
        float maxHealth = Math.max(1f, player.getMaxHealth());
        float healthRatio = MathUtils.clamp01(health / maxHealth);

        String distStr = showDistance.getValue() ? Math.round(distance) + "m" : null;
        String hpStr = showHealth.getValue() ? String.format("%.1f", health) : null;

        float padX = 6f;
        float gap = 5f;
        float cardH = 20f;
        float barH = 2f;

        float distW = distStr == null ? 0f : bodyFr.getWidth(distStr);

        float nameW = labelFr.getWidth(name);
        float hpChipPadX = 4f;
        float hpChipW = hpStr == null ? 0f : (labelFr.getWidth(hpStr) + hpChipPadX * 2f);

        float contentW = (distW > 0f ? distW + gap : 0f) + nameW + (hpChipW > 0f ? gap + hpChipW : 0f);
        float cardW = Math.max(44f, contentW + padX * 2f);

        float x1 = -cardW / 2f;
        float x2 = cardW / 2f;
        float y1 = -cardH;
        float y2 = 0f;
        float rowCy = y1 + (cardH - barH) / 2f;

        GlStateManager.pushMatrix();
        double yOffset = player.isSneaking() ? 0.55 : 0.72;
        GlStateManager.translate(x, y + player.height + yOffset, z);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0f, 1.0f, 0.0f);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0f, 0.0f, 0.0f);
        GlStateManager.scale(-scale, -scale, scale);

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        // Card surface & outline
        int bgCol = M3.alpha(M3.surfaceContainerLow(), 220);
        int borderCol = isFriend
                ? M3.alpha(M3.tertiary(), 210)
                : M3.alpha(M3.outlineVariant(), 145);
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, 4.5f, bgCol);
        DrawUtils.drawRoundedOutline(x1, y1, x2, y2, 4.5f, 1f, borderCol);

        // Bottom M3 health bar
        int hpBarCol = healthBarColor(healthRatio);
        int trackCol = M3.alpha(M3.surfaceContainerHighest(), 190);
        DrawUtils.drawRoundedRect(x1 + 3f, y2 - barH - 1.5f, x2 - 3f, y2 - 1.5f, 1f, trackCol);
        float fillW = (cardW - 6f) * healthRatio;
        if (fillW > 0.5f) {
            DrawUtils.drawRoundedRect(x1 + 3f, y2 - barH - 1.5f, x1 + 3f + fillW, y2 - 1.5f, 1f, hpBarCol);
        }

        // Row elements: Distance -> Name -> Health Chip
        float cursor = x1 + padX;
        if (distStr != null) {
            bodyFr.drawStringWithShadow(distStr, cursor, rowCy, 0xFFAAAAAA, bodyFr.CENTREY);
            cursor += distW + gap;
        }

        int nameColor = isFriend ? 0xFF55FFFF : 0xFFFFFFFF;
        labelFr.drawStringWithShadow(name, cursor, rowCy, nameColor, labelFr.CENTREY);

        if (hpStr != null) {
            int chipBg = healthRatio < 0.3f
                    ? M3.alpha(M3.errorContainer(), 235)
                    : M3.alpha(M3.primaryContainer(), 235);
            int chipFg = healthRatio < 0.3f
                    ? 0xFFFF6B6B
                    : 0xFFFFFFFF;
            float chipX2 = x2 - padX;
            float chipX1 = chipX2 - hpChipW;
            float chipH = 13f;
            DrawUtils.drawRoundedRect(chipX1, rowCy - chipH / 2f, chipX2, rowCy + chipH / 2f, 3.5f, chipBg);
            labelFr.drawStringWithShadow(hpStr, (chipX1 + chipX2) / 2f, rowCy, chipFg, labelFr.CENTREX, labelFr.CENTREY);
        }

        if (showArmor.getValue()) {
            renderEquipment(player, y1 - 3f, bodyFr);
        }

        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.popMatrix();
    }

    private void renderEquipment(EntityPlayer player, float bottomY, FontRendererExtension<?> bodyFr) {
        List<ItemStack> items = new ArrayList<>(5);
        if (player.getCurrentEquippedItem() != null) {
            items.add(player.getCurrentEquippedItem());
        }
        for (int i = 3; i >= 0; i--) {
            ItemStack stack = player.inventory.armorInventory[i];
            if (stack != null) {
                items.add(stack);
            }
        }
        if (items.isEmpty())
            return;

        float slotSize = 18f;
        float slotGap = 2.5f;
        float totalW = items.size() * slotSize + (items.size() - 1) * slotGap;
        float startX = -totalW / 2f;
        float slotY = bottomY - slotSize;

        // Draw tonal M3 item slots first while 2D blend state is clean
        for (int i = 0; i < items.size(); i++) {
            float sx = startX + i * (slotSize + slotGap);
            DrawUtils.drawRoundedRect(sx, slotY, sx + slotSize, slotY + slotSize, 4f,
                    M3.alpha(M3.surfaceContainerLow(), 205));
            DrawUtils.drawRoundedOutline(sx, slotY, sx + slotSize, slotY + slotSize, 4f, 0.8f,
                    M3.alpha(M3.outlineVariant(), 130));
        }

        // Render 16x16 item icons centered inside each 18x18 slot
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            int ix = Math.round(startX + i * (slotSize + slotGap) + 1f);
            int iy = Math.round(slotY + 1f);
            GlStateManager.pushMatrix();
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().zLevel = -150.0f;
            mc.getRenderItem().renderItemAndEffectIntoGUI(stack, ix, iy);
            mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRendererObj, stack, ix, iy, null);
            mc.getRenderItem().zLevel = 0.0f;
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
        }

        // Restore clean 2D billboard state after RenderItem before drawing enchantment tags
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        if (showEnchants.getValue()) {
            for (int i = 0; i < items.size(); i++) {
                float slotCx = startX + i * (slotSize + slotGap) + slotSize / 2f;
                drawEnchantments(items.get(i), slotCx, slotY - 2f, bodyFr);
            }
        }
    }

    private void drawEnchantments(ItemStack stack, float cx, float bottomY, FontRendererExtension<?> bodyFr) {
        List<String> tags = collectEnchantTags(stack);
        if (tags.isEmpty())
            return;

        float lineH = 7.5f;
        float y = bottomY - lineH / 2f;
        bodyFr.setScale(0.68f);
        for (String tag : tags) {
            float tw = bodyFr.getWidth(tag) + 3.5f;
            DrawUtils.drawRoundedRect(cx - tw / 2f, y - 3.5f, cx + tw / 2f, y + 3.5f, 2f,
                    M3.alpha(M3.surfaceContainerLowest(), 200));
            int col = tag.equals("God") ? 0xFFFFD700 : 0xFFFFFFFF;
            bodyFr.drawStringWithShadow(tag, cx, y, col, bodyFr.CENTREX, bodyFr.CENTREY);
            y -= lineH;
        }
        bodyFr.resetScale();
    }

    private List<String> collectEnchantTags(ItemStack stack) {
        List<String> list = new ArrayList<>(4);
        if (stack.getItem() instanceof ItemArmor) {
            addEnchant(list, "P", EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack));
            addEnchant(list, "Th", EnchantmentHelper.getEnchantmentLevel(Enchantment.thorns.effectId, stack));
            addEnchant(list, "U", EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, stack));
        } else if (stack.getItem() instanceof ItemSword) {
            addEnchant(list, "S", EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack));
            addEnchant(list, "KB", EnchantmentHelper.getEnchantmentLevel(Enchantment.knockback.effectId, stack));
            addEnchant(list, "F", EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, stack));
            addEnchant(list, "U", EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, stack));
        } else if (stack.getItem() instanceof ItemBow) {
            addEnchant(list, "Pw", EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, stack));
            addEnchant(list, "Pn", EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, stack));
            addEnchant(list, "Fl", EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, stack));
        } else if (stack.getItem() instanceof ItemTool) {
            addEnchant(list, "E", EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, stack));
            addEnchant(list, "S", EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack));
        } else if (stack.getItem() == Items.golden_apple && stack.hasEffect()) {
            list.add("God");
        }
        return list;
    }

    private static void addEnchant(List<String> list, String prefix, int level) {
        if (level > 0) {
            list.add(prefix + level);
        }
    }

    private static int healthBarColor(float ratio) {
        if (ratio >= 0.5f) {
            return M3.primary();
        } else if (ratio >= 0.25f) {
            return M3.mix(M3.error(), M3.primary(), (ratio - 0.25f) / 0.25f);
        }
        return M3.error();
    }
}

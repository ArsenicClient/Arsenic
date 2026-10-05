package arsenic.module.impl.visual;

import arsenic.utils.java.MathUtils;
import arsenic.gui.themes.ThemeManager;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StringUtils;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "Nametags", category = ModuleCategory.RENDER, hidden = true)
public class Nametags extends Module {

    public final DoubleProperty tagScale = new DoubleProperty("Scale", new DoubleValue(0.5, 3, 1, 0.05));
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(8, 128, 64, 1));

    private static final float BASE_SCALE = 0.02666667F;
    private static final int PAD = 2;
    private static final float ICON_SIZE = 12f;
    private static final float ICON_SPACING = 14f;

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> renderListener = event -> {
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        if (fr == null) return;

        for (EntityPlayer player : Minecraft.getMinecraft().theWorld.playerEntities) {
            if (player == mc.thePlayer) continue;
            if (AntiBot.isBot(player)) continue;
            if (player.isDead) continue;
            if (mc.thePlayer.getDistanceToEntity(player) > range.getValue().getInput()) continue;

            double x = (player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks)
                    - mc.getRenderManager().viewerPosX;
            double y = (player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks)
                    - mc.getRenderManager().viewerPosY;
            double z = (player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks)
                    - mc.getRenderManager().viewerPosZ;

            String name = StringUtils.stripControlCodes(player.getName());
            String healthText = true
                    ? String.format(" §7%.1f", player.getHealth())
                    : "";
            String distText = false
                    ? String.format(" §7[%.0f]", mc.thePlayer.getDistanceToEntity(player))
                    : "";
            String text = name + healthText + distText;

            float scale = BASE_SCALE * (float) tagScale.getValue().getInput();

            int textWidth = (int) Math.ceil(fr.getWidth(text));
            int textHeight = (int) Math.ceil(fr.getHeight(text));
            int left = -(textWidth / 2);
            int right = left + textWidth;

            float healthPercent = MathUtils.clamp01(player.getHealth() / player.getMaxHealth());
            int healthColor = healthPercent > 0.5f ? 0xFF2ECC71
                    : healthPercent > 0.25f ? 0xFFFFFF00
                    : 0xFFFF0000;

            GlStateManager.pushMatrix();
            GL11.glTranslated(x, y + player.height + 0.6, z);
            GL11.glNormal3f(0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate((mc.gameSettings.thirdPersonView == 2 ? -1 : 1)
                    * mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-scale, -scale, scale);
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

            drawGear(fr, collectGear(player));

            Gui.drawRect(left - PAD, -PAD, right + PAD, textHeight + PAD, new Color(0, 0, 0, 110).getRGB());
            int barRight = left - PAD + Math.round((textWidth + PAD * 2) * healthPercent);
            Gui.drawRect(left - PAD, textHeight + PAD, barRight, textHeight + PAD + 1, healthColor);

            GlStateManager.enableTexture2D();
            fr.drawString(text, left, textHeight / 2f, 0xFFFFFFFF, fr.CENTREY);

            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.disableBlend();
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        }
    };

    private List<ItemStack> collectGear(EntityPlayer player) {
        List<ItemStack> gear = new ArrayList<>();
        ItemStack held = player.getHeldItem();
        if (held != null) gear.add(held);
        for (int i = 3; i >= 0; i--) {
            ItemStack armor = player.getCurrentArmor(i);
            if (armor != null) gear.add(armor);
        }
        return gear;
    }

    private void drawGear(FontRendererExtension<?> fr, List<ItemStack> gear) {
        if (gear.isEmpty()) return;

        int count = gear.size();
        float totalW = count * ICON_SPACING;
        float startX = -totalW / 2f;

        float enchScale = 0.55f;
        int enchLineH = (int) (fr.getHeight("A") * enchScale) + 1;
        int maxEnchLines = 0;
        List<List<String>> enchLists = new ArrayList<>();
        for (ItemStack stack : gear) {
            List<String> lines = true ? enchantLines(stack) : new ArrayList<>();
            enchLists.add(lines);
            maxEnchLines = Math.max(maxEnchLines, lines.size());
        }

        float enchBlockH = maxEnchLines * enchLineH;
        float iconBottom = -6 - enchBlockH;
        float iconTop = iconBottom - ICON_SIZE;

        GlStateManager.pushMatrix();
        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.01f);
        GlStateManager.color(1, 1, 1, 1);

        for (int i = 0; i < count; i++) {
            TextureAtlasSprite sprite = resolveSprite(gear.get(i));
            if (sprite == null) continue;
            float left = startX + i * ICON_SPACING + (ICON_SPACING - ICON_SIZE) / 2f;
            drawSprite(sprite, left, iconTop, ICON_SIZE);
        }
        GlStateManager.disableAlpha();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.popMatrix();

        if (maxEnchLines > 0) {
            for (int i = 0; i < count; i++) {
                List<String> lines = enchLists.get(i);
                float colCenter = startX + i * ICON_SPACING + ICON_SPACING / 2f;
                for (int l = 0; l < lines.size(); l++) {
                    String line = lines.get(l);
                    GlStateManager.pushMatrix();
                    float ty = iconBottom + l * enchLineH;
                    GlStateManager.translate(colCenter, ty, 0);
                    GlStateManager.scale(enchScale, enchScale, 1f);
                    fr.drawString(line, (int) (-fr.getWidth(line) / 2f), 0, 0xFFFFFFFF);
                    GlStateManager.popMatrix();
                }
            }
        }
    }

    private List<String> enchantLines(ItemStack stack) {
        List<String> out = new ArrayList<>();
        if (stack == null || !stack.isItemEnchanted()) return out;
        NBTTagList list = stack.getEnchantmentTagList();
        if (list == null) return out;
        for (int i = 0; i < list.tagCount(); i++) {
            int id = list.getCompoundTagAt(i).getShort("id");
            int lvl = list.getCompoundTagAt(i).getShort("lvl");
            out.add(abbreviate(id) + lvl);
        }
        return out;
    }

    private String abbreviate(int id) {
        switch (id) {
            case 0:  return "§bProt";
            case 1:  return "§6FP";
            case 2:  return "§fFF";
            case 3:  return "§8BP";
            case 4:  return "§ePP";
            case 5:  return "§3Resp";
            case 6:  return "§3AA";
            case 7:  return "§2Thn";
            case 8:  return "§3DS";
            case 16: return "§cSharp";
            case 17: return "§cSmite";
            case 18: return "§cBane";
            case 19: return "§7KB";
            case 20: return "§6Fire";
            case 21: return "§aLoot";
            case 32: return "§aEff";
            case 33: return "§7Silk";
            case 34: return "§7Unb";
            case 35: return "§aFort";
            case 48: return "§cPow";
            case 49: return "§7Pun";
            case 50: return "§6Flame";
            case 51: return "§eInf";
            default:
                Enchantment ench = Enchantment.getEnchantmentById(id);
                if (ench != null) {
                    String n = StringUtils.stripControlCodes(net.minecraft.client.resources.I18n.format(ench.getName()));
                    return n.length() > 3 ? n.substring(0, 3) : n;
                }
                return "?";
        }
    }

    private TextureAtlasSprite resolveSprite(ItemStack stack) {
        try {
            IBakedModel model = mc.getRenderItem().getItemModelMesher().getItemModel(stack);
            if (model != null) {
                TextureAtlasSprite sprite = model.getParticleTexture();
                if (sprite != null) return sprite;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void drawSprite(TextureAtlasSprite sprite, float left, float top, float size) {
        float minU = sprite.getMinU();
        float maxU = sprite.getMaxU();
        float minV = sprite.getMinV();
        float maxV = sprite.getMaxV();
        float right = left + size;
        float bottom = top + size;

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(left, bottom, 0).tex(minU, maxV).endVertex();
        wr.pos(right, bottom, 0).tex(maxU, maxV).endVertex();
        wr.pos(right, top, 0).tex(maxU, minV).endVertex();
        wr.pos(left, top, 0).tex(minU, minV).endVertex();
        tess.draw();
    }
}

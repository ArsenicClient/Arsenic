package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.ColourProperty;
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

@ModuleInfo(name = "Nametags", category = ModuleCategory.WORLD, hidden = true)
public class Nametags extends Module {

    public final ColourProperty color = new ColourProperty("Color:", 0xFF2ECC71);
    public final BooleanProperty showHealth = new BooleanProperty("Show Health", true);
    public final BooleanProperty showDistance = new BooleanProperty("Show Distance", false);
    public final BooleanProperty showGear = new BooleanProperty("Show Gear", true);
    public final BooleanProperty showEnchants = new BooleanProperty("Show Enchants", true);
    // On: constant on-screen size at any range. Off: original behaviour (shrinks with distance).
    public final BooleanProperty constantSize = new BooleanProperty("Constant Size", true);

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

            double x = (player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks)
                    - mc.getRenderManager().viewerPosX;
            double y = (player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks)
                    - mc.getRenderManager().viewerPosY;
            double z = (player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks)
                    - mc.getRenderManager().viewerPosZ;

            String name = StringUtils.stripControlCodes(player.getName());
            String healthText = showHealth.getValue()
                    ? String.format(" §7%.1f", player.getHealth())
                    : "";
            String distText = showDistance.getValue()
                    ? String.format(" §7[%.0f]", mc.thePlayer.getDistanceToEntity(player))
                    : "";
            String text = name + healthText + distText;

            // Constant Size on: scale the world-space size linearly with camera distance so the tag
            // keeps a constant on-screen size at any range (perspective shrink of 1/dist cancels the
            // *dist here). REFERENCE_DIST sets that size — it looks like a tag this many blocks away.
            // Off: the plain fixed world scale, which naturally shrinks with distance.
            float scale = 0.02666667F;
            if (constantSize.getValue()) {
                double ax = x, ay = y + player.height + 0.6, az = z;
                double dist = Math.max(1.0, Math.sqrt(ax * ax + ay * ay + az * az));
                final float REFERENCE_DIST = 3.0f;
                scale *= (float) (dist / REFERENCE_DIST);
            }
            int textWidth = (int) fr.getWidth(text);
            int textHeight = (int) fr.getHeight(text);
            float halfWidth = textWidth / 2f;

            float healthPercent = player.getHealth() / player.getMaxHealth();
            int healthColor = healthPercent > 0.5f ? 0xFF2ECC71
                    : healthPercent > 0.25f ? 0xFFFFFF00
                    : 0xFFFF0000;

            GlStateManager.pushMatrix();
            GL11.glTranslated(x, y + player.height + 0.6, z);
            GL11.glNormal3f(0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-scale, -scale, scale);
            GlStateManager.disableDepth();
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            if (showGear.getValue()) {
                drawGear(fr, collectGear(player));
            }

            Gui.drawRect((int) (-halfWidth - 2), -2, (int) (halfWidth + 2), textHeight + 2,
                    new Color(0, 0, 0, 100).getRGB());

            fr.drawString(text, (int) (-halfWidth), 0, 0xFFFFFFFF);

            if (showHealth.getValue()) {
                Gui.drawRect((int) (-halfWidth - 2), textHeight + 2,
                        (int) (-halfWidth - 2 + (textWidth + 4) * healthPercent), textHeight + 3,
                        healthColor);
            }

            GL11.glDisable(GL11.GL_BLEND);
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        }
    };

    /** Held item + the four armour pieces, in a stable left-to-right order, nulls skipped. */
    private List<ItemStack> collectGear(EntityPlayer player) {
        List<ItemStack> gear = new ArrayList<>();
        ItemStack held = player.getHeldItem();
        if (held != null) gear.add(held);
        for (int i = 3; i >= 0; i--) { // helmet -> boots
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

        // enchant text sits between the icon row and the name; leave room above the name for it.
        float enchScale = 0.55f;
        int enchLineH = (int) (fr.getHeight("A") * enchScale) + 1;
        int maxEnchLines = 0;
        List<List<String>> enchLists = new ArrayList<>();
        for (ItemStack stack : gear) {
            List<String> lines = showEnchants.getValue() ? enchantLines(stack) : new ArrayList<>();
            enchLists.add(lines);
            maxEnchLines = Math.max(maxEnchLines, lines.size());
        }

        float enchBlockH = maxEnchLines * enchLineH;
        float iconBottom = -6 - enchBlockH;      // above the name / enchant block
        float iconTop = iconBottom - ICON_SIZE;

        // Icons.
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

        // Enchant abbreviations, centred under each icon column.
        if (showEnchants.getValue() && maxEnchLines > 0) {
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

    /** One "AbbrevLevel" token per enchantment, e.g. "P4", "U3". */
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
            case 0:  return "§bProt";  // Protection
            case 1:  return "§6FP";    // Fire Protection
            case 2:  return "§fFF";    // Feather Falling
            case 3:  return "§8BP";    // Blast Protection
            case 4:  return "§ePP";    // Projectile Protection
            case 5:  return "§3Resp";  // Respiration
            case 6:  return "§3AA";    // Aqua Affinity
            case 7:  return "§2Thn";   // Thorns
            case 8:  return "§3DS";    // Depth Strider
            case 16: return "§cSharp"; // Sharpness
            case 17: return "§cSmite";
            case 18: return "§cBane";
            case 19: return "§7KB";    // Knockback
            case 20: return "§6Fire";  // Fire Aspect
            case 21: return "§aLoot";  // Looting
            case 32: return "§aEff";   // Efficiency
            case 33: return "§7Silk";  // Silk Touch
            case 34: return "§7Unb";   // Unbreaking
            case 35: return "§aFort";  // Fortune
            case 48: return "§cPow";   // Power
            case 49: return "§7Pun";   // Punch
            case 50: return "§6Flame";
            case 51: return "§eInf";   // Infinity
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

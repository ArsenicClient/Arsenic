package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.ColourProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

@ModuleInfo(name = "Nametags", category = ModuleCategory.WORLD, hidden = true)
public class Nametags extends Module {

    public enum SortMode {
        DISTANCE, HEALTH, NAME
    }

    public final BooleanProperty self = new BooleanProperty("Self", false);
    public final BooleanProperty health = new BooleanProperty("Health", true);
    public final BooleanProperty healthBar = new BooleanProperty("HealthBar", true);
    public final BooleanProperty absorption = new BooleanProperty("Absorption", false);
    public final BooleanProperty ping = new BooleanProperty("Ping", false);
    public final BooleanProperty distance = new BooleanProperty("Distance", false);
    public final BooleanProperty armor = new BooleanProperty("Armor", true);
    public final BooleanProperty potionIcons = new BooleanProperty("Potions", true);
    public final BooleanProperty bots = new BooleanProperty("Bots", true);
    public final BooleanProperty clearNames = new BooleanProperty("ClearNames", false);
    public final DoubleProperty scaleFactor = new DoubleProperty("Scale", new DoubleValue(1, 4, 1, 0.5));
    public final BooleanProperty shadow = new BooleanProperty("Shadow", true);
    public final BooleanProperty background = new BooleanProperty("Background", true);
    public final ColourProperty backgroundColor = new ColourProperty("BGColor", 0x46000000);
    public final BooleanProperty border = new BooleanProperty("Border", true);
    public final ColourProperty borderColor = new ColourProperty("BorderColor", 0x64000000);
    public final DoubleProperty maxRenderDistance = new DoubleProperty("MaxDistance", new DoubleValue(1, 200, 50, 1));
    public final EnumProperty<SortMode> sortMode = new EnumProperty<>("Sort", SortMode.DISTANCE);
    public final BooleanProperty onLook = new BooleanProperty("OnLook", false);
    public final DoubleProperty maxAngleDiff = new DoubleProperty("MaxAngle", new DoubleValue(5, 90, 90, 1));
    public final BooleanProperty thruBlocks = new BooleanProperty("ThruBlocks", true);

    private static final ResourceLocation INVENTORY_BACKGROUND = new ResourceLocation("textures/gui/container/inventory.png");

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> renderListener = event -> {
        if (mc.theWorld == null) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glPushMatrix();

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int maxDist = (int) maxRenderDistance.getValue().getInput();
        double maxDistSq = maxDist * maxDist;

        mc.theWorld.playerEntities.stream()
                .filter(e -> e != mc.thePlayer || self.getValue())
                .filter(e -> bots.getValue() || !AntiBot.isBot(e))
                .filter(e -> !e.isDead)
                .filter(e -> mc.thePlayer.getDistanceSqToEntity(e) <= maxDistSq)
                .filter(e -> !onLook.getValue() || isLookingAt(e, maxAngleDiff.getValue().getInput()))
                .filter(e -> thruBlocks.getValue() || mc.thePlayer.canEntityBeSeen(e))
                .sorted((a, b) -> {
                    switch (sortMode.getValue()) {
                        case HEALTH: return Float.compare(a.getHealth(), b.getHealth());
                        case NAME: return a.getName().compareToIgnoreCase(b.getName());
                        default: return Double.compare(
                                mc.thePlayer.getDistanceSqToEntity(a),
                                mc.thePlayer.getDistanceSqToEntity(b)
                        );
                    }
                })
                .forEach(player -> renderNametag(player, event.partialTicks));

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
        GlStateManager.resetColor();
    };

    private boolean isLookingAt(Entity entity, double maxAngle) {
        if (entity == null) return false;
        double dx = entity.posX - mc.thePlayer.posX;
        double dz = entity.posZ - mc.thePlayer.posZ;
        double yaw = Math.toDegrees(Math.atan2(-dz, dx)) - 90.0;
        double angleDiff = MathHelper.wrapAngleTo180_float((float) (yaw - mc.thePlayer.rotationYaw));
        return Math.abs(angleDiff) <= maxAngle;
    }

    private void renderNametag(EntityLivingBase entity, float partialTicks) {
        String rawName = entity.getDisplayName().getUnformattedText();
        if (rawName == null) return;
        String name = clearNames.getValue() ? rawName.replaceAll("§[0-9a-fk-or]", "") : rawName;

        boolean isBot = AntiBot.isBot(entity);
        String nameColor = isBot ? "§3" : entity.isInvisible() ? "§6" : entity.isSneaking() ? "§4" : "§7";

        StringBuilder sb = new StringBuilder();

        if (distance.getValue()) {
            double dist = mc.thePlayer.getDistanceToEntity(entity);
            sb.append("§7").append(Math.round(dist)).append("m ");
        }

        if (ping.getValue() && entity instanceof EntityPlayer) {
            int playerPing = getPing((EntityPlayer) entity);
            String pingColor = playerPing > 200 ? "§c" : playerPing > 100 ? "§e" : "§a";
            sb.append("§7[").append(pingColor).append(playerPing).append("ms§7] ");
        }

        sb.append(nameColor).append(name);

        if (health.getValue()) {
            float rawHealth = getHealth(entity, absorption.getValue());
            float ratio = Math.min(1f, rawHealth / entity.getMaxHealth());
            String hColor = ratio >= 0.75f ? "§a" : ratio >= 0.5f ? "§e" : ratio >= 0.25f ? "§6" : "§c";
            sb.append(" ").append(hColor).append(Math.round(rawHealth)).append(" HP");
        }

        if (isBot) sb.append(" §c§lBot");

        String text = sb.toString();

        double dist = mc.thePlayer.getDistanceToEntity(entity);
        float s = (float) (Math.max(dist / 4.0, 1.0) / 150.0 * scaleFactor.getValue().getInput());

        double ex = (entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks)
                - mc.getRenderManager().viewerPosX;
        double ey = (entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks)
                - mc.getRenderManager().viewerPosY + entity.getEyeHeight() + 0.55;
        double ez = (entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks)
                - mc.getRenderManager().viewerPosZ;

        GL11.glPushMatrix();
        GL11.glTranslated(ex, ey, ez);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-s, -s, s);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int textWidth = mc.fontRendererObj.getStringWidth(text);
        int textHeight = mc.fontRendererObj.FONT_HEIGHT;
        float halfWidth = textWidth / 2f;

        float bgX = -halfWidth - 3;
        float bgY = -2;
        float bgW = textWidth + 6;
        float bgH = textHeight + 4;

        if (healthBar.getValue()) bgH += 3;

        if (background.getValue() || border.getValue()) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            int bgColor = backgroundColor.getValue();
            int brColor = borderColor.getValue();

            if (border.getValue()) {
                drawRect(bgX - 1, bgY - 1, bgX + bgW + 1, bgY + bgH + 1, brColor);
            }
            if (background.getValue()) {
                drawRect(bgX, bgY, bgX + bgW, bgY + bgH, bgColor);
            }
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }

        mc.fontRendererObj.drawString(text, -halfWidth + 1, 1, 0xFFFFFFFF, shadow.getValue());

        if (healthBar.getValue()) {
            float healthPct = Math.min(1f, entity.getHealth() / entity.getMaxHealth());
            int barColor;
            if (healthPct > 0.5f) {
                int r = (int) (255 * (1 - healthPct) * 2);
                barColor = (255 << 24) | (r << 16) | (255 << 8);
            } else {
                int g = (int) (255 * healthPct * 2);
                barColor = (255 << 24) | (255 << 16) | (g << 8);
            }

            float barY = textHeight + 4;
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            drawRect(bgX, barY, bgX + bgW, barY + 2, 0x32000000);
            drawRect(bgX, barY, bgX + bgW * healthPct, barY + 2, barColor);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }

        boolean hasPotions = false;
        if (potionIcons.getValue() && entity instanceof EntityPlayer) {
            java.util.Collection<PotionEffect> effects = entity.getActivePotionEffects();
            int validCount = 0;
            for (PotionEffect effect : effects) {
                Potion potion = Potion.potionTypes[effect.getPotionID()];
                if (potion != null && potion.hasStatusIcon()) validCount++;
            }
            if (validCount > 0) {
                hasPotions = true;
                GL11.glPushMatrix();
                GlStateManager.enableRescaleNormal();
                int potionStartX = (validCount * -20) / 2;
                int idx = 0;
                for (PotionEffect effect : effects) {
                    Potion potion = Potion.potionTypes[effect.getPotionID()];
                    if (potion == null || !potion.hasStatusIcon()) continue;
                    int iconIndex = potion.getStatusIconIndex();
                    int iconU = iconIndex % 8 * 18;
                    int iconV = 198 + iconIndex / 8 * 18;
                    mc.renderEngine.bindTexture(INVENTORY_BACKGROUND);
                    drawTexturedRect(potionStartX + idx * 20, -22, iconU, iconV, 18, 18);
                    idx++;
                }
                GlStateManager.disableRescaleNormal();
                GL11.glPopMatrix();
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            }
        }

        if (armor.getValue() && entity instanceof EntityPlayer) {
            int armorY = hasPotions ? -42 : -22;
            GL11.glPushMatrix();
            RenderHelper.enableGUIStandardItemLighting();
            for (int slot = 0; slot < 5; slot++) {
                net.minecraft.item.ItemStack stack = entity.getEquipmentInSlot(slot);
                if (stack == null) continue;
                mc.getRenderItem().zLevel = -147f;
                mc.getRenderItem().renderItemAndEffectIntoGUI(stack, -50 + slot * 20, armorY);
            }
            RenderHelper.disableStandardItemLighting();
            GL11.glPopMatrix();
            GL11.glEnable(GL11.GL_ALPHA_TEST);
        }

        GL11.glPopMatrix();
        GlStateManager.resetColor();
    }

    private void drawRect(float left, float top, float right, float bottom, int color) {
        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(r, g, b, a);
        GL11.glVertex2f(left, bottom);
        GL11.glVertex2f(right, bottom);
        GL11.glVertex2f(right, top);
        GL11.glVertex2f(left, top);
        GL11.glEnd();
    }

    private void drawTexturedRect(int x, int y, int u, int v, int w, int h) {
        float f = 0.00390625F;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x, y + h, 0).tex(u * f, (v + h) * f).endVertex();
        wr.pos(x + w, y + h, 0).tex((u + w) * f, (v + h) * f).endVertex();
        wr.pos(x + w, y, 0).tex((u + w) * f, v * f).endVertex();
        wr.pos(x, y, 0).tex(u * f, v * f).endVertex();
        tessellator.draw();
    }

    private float getHealth(EntityLivingBase entity, boolean includeAbsorption) {
        float health = entity.getHealth();
        if (includeAbsorption) {
            health += entity.getAbsorptionAmount();
        }
        return health;
    }

    private int getPing(EntityPlayer player) {
        if (mc.getNetHandler() == null) return 0;
        net.minecraft.client.network.NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(player.getUniqueID());
        return info != null ? info.getResponseTime() : 0;
    }
}

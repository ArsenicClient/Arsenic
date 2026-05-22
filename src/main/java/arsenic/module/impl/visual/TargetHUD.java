package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.render.DrawUtils;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StringUtils;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

@ModuleInfo(name = "TargetHUD", category = ModuleCategory.SETTINGS, hidden = true)
public class TargetHUD extends Module {

    public final EnumProperty<TargetHUDMode> mode = new EnumProperty<>("Mode", TargetHUDMode.Face);
    public final DoubleProperty fadeTime = new DoubleProperty("Fade Time (s)", new DoubleValue(1, 10, 3, 0.5));
    public final BooleanProperty editPosition = new BooleanProperty("Edit Position", false);
    public final BooleanProperty stick = new BooleanProperty("Stick", false);

    private AbstractClientPlayer target;
    private long lastTargetTime;
    private final Map<EntityPlayer, Long> recentTargets = new HashMap<>();
    private float animatedHealth;
    private float animatedArmor;
    private float animatedScale;
    private long damageFlashTime;
    private float lastHealth;

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        if (event.getTarget() instanceof EntityPlayer) {
            EntityPlayer targetPlayer = (EntityPlayer) event.getTarget();
            target = (AbstractClientPlayer) targetPlayer;
            lastTargetTime = System.currentTimeMillis();
            recentTargets.put(targetPlayer, lastTargetTime);
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onWorldRender = event -> {
        if (stick.getValue() && target != null && animatedScale > 0.01f) {
            double renderX = (target.lastTickPosX + (target.posX - target.lastTickPosX) * event.partialTicks)
                    - mc.getRenderManager().viewerPosX;
            double renderY = (target.lastTickPosY + (target.posY - target.lastTickPosY) * event.partialTicks)
                    - mc.getRenderManager().viewerPosY;
            double renderZ = (target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * event.partialTicks)
                    - mc.getRenderManager().viewerPosZ;
            renderStickHUD(target, renderX, renderY, renderZ, animatedScale);
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        if (editPosition.getValue()) {
            target = mc.thePlayer;
            drawTargetHUD(target, 0, 10000000, 1.0f);
            return;
        }

        long currentTime = System.currentTimeMillis();
        long fadeMs = (long) (fadeTime.getValue().getInput() * 1000);

        recentTargets.entrySet().removeIf(entry -> currentTime - entry.getValue() > fadeMs);

        boolean draw2D = !stick.getValue();
        AbstractClientPlayer renderTarget = null;
        boolean fadingOut = false;

        if (target != null && currentTime - lastTargetTime < fadeMs) {
            animatedScale = interpolate(animatedScale, 1.0f, 0.1f);
            renderTarget = target;
        } else if (!recentTargets.isEmpty()) {
            EntityPlayer mostRecent = null;
            long mostRecentTime = 0;
            for (Map.Entry<EntityPlayer, Long> entry : recentTargets.entrySet()) {
                if (entry.getValue() > mostRecentTime) {
                    mostRecentTime = entry.getValue();
                    mostRecent = entry.getKey();
                }
            }
            if (mostRecent != null && currentTime - mostRecentTime < fadeMs) {
                animatedScale = interpolate(animatedScale, 1.0f, 0.1f);
                renderTarget = (AbstractClientPlayer) mostRecent;
            }
        } else {
            if (animatedScale > 0.01f) {
                animatedScale = interpolate(animatedScale, 0.0f, 0.15f);
                renderTarget = target;
                fadingOut = true;
            } else {
                target = null;
            }
        }

        if (renderTarget != null && draw2D) {
            drawTargetHUD(renderTarget, currentTime - lastTargetTime, fadeMs, animatedScale);
        }
        if (renderTarget == null && !fadingOut) {
            target = null;
        }
    };

    private void drawTargetHUD(AbstractClientPlayer target, long timeSinceTarget, long fadeTime, float scale) {
        if (scale <= 0.01f) return;
        switch (mode.getValue()) {
            case Simple:
                drawSimpleMode(target, scale);
                break;
            default:
                drawFaceMode(target, scale);
                break;
        }
    }

    private void drawFaceMode(AbstractClientPlayer target, float scale) {
        float alpha = Math.min(1f, scale);
        int x = HUD.targetHUDX;
        int y = HUD.targetHUDY;
        int hudWidth = 150;
        int hudHeight = 50;

        GL11.glPushMatrix();
        GL11.glTranslated(x + hudWidth / 2.0, y + hudHeight / 2.0, 0);
        GL11.glScalef(scale, scale, 1.0f);
        GL11.glTranslated(-(x + hudWidth / 2.0), -(y + hudHeight / 2.0), 0);

        long timeSinceDamage = System.currentTimeMillis() - damageFlashTime;
        float flashAlpha = timeSinceDamage < 300 ? 1f - (timeSinceDamage / 300f) : 0;

        int bgColor = new Color(26, 26, 26, (int)(alpha * 128)).getRGB();
        if (flashAlpha > 0) {
            int r = (int) (26 + (255 - 26) * flashAlpha);
            int g = (int) (26 * (1 - flashAlpha));
            int b = (int) (26 * (1 - flashAlpha));
            bgColor = new Color(r, g, b, (int)(alpha * 128)).getRGB();
        }
        DrawUtils.drawRoundedRect(x, y, x + hudWidth, y + hudHeight, 8, bgColor);

        int borderColor = flashAlpha > 0
                ? (int) (alpha * 0xFF) << 24 | 0xFF0000
                : (int) (alpha * 0xFF) << 24 | getThemeColor();
        DrawUtils.drawBorderedRoundedRect(x, y, x + hudWidth, y + hudHeight, 8, 2, borderColor, 0x00000000);

        GL11.glColor4f(1, 1, 1, alpha);
        mc.getTextureManager().bindTexture(target.getLocationSkin());
        Gui.drawScaledCustomSizeModalRect(x + 5, y + 5, 8.0F, 8.0F, 8, 8, 30, 30, 64.0F, 64.0F);

        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        if (fr == null) { GL11.glPopMatrix(); return; }

        String name = StringUtils.stripControlCodes(target.getName());
        fr.drawStringWithShadow(name, x + 40, y + 8, (int) (alpha * 0xFF) << 24 | 0xFFFFFF);

        float health = target.getHealth();
        float maxHealth = target.getMaxHealth();
        if (animatedHealth == 0 || target == mc.thePlayer) animatedHealth = health;
        animatedHealth = interpolate(animatedHealth, health, 0.1f);
        float healthPercent = animatedHealth / maxHealth;

        int healthBarY = y + 25;
        int healthBarWidth = hudWidth - 45;

        DrawUtils.drawRoundedRect(x + 40, healthBarY, x + 40 + healthBarWidth, healthBarY + 8, 4,
                (int) (alpha * 0x40) << 24 | 0x404040);

        int healthColor = healthPercent > 0.5f ? getThemeColor() : (healthPercent > 0.25f ? 0xFFFFFF00 : 0xFFFF0000);
        DrawUtils.drawRoundedRect(x + 40, healthBarY, x + 40 + (int) (healthBarWidth * healthPercent), healthBarY + 8, 4,
                (int) (alpha * 0xFF) << 24 | healthColor);

        int armor = target.getTotalArmorValue();
        if (animatedArmor == 0 || target == mc.thePlayer) animatedArmor = armor;
        animatedArmor = interpolate(animatedArmor, armor, 0.1f);

        int armorBarY = healthBarY + 10;
        DrawUtils.drawRoundedRect(x + 40, armorBarY, x + 40 + healthBarWidth, armorBarY + 4, 2,
                (int) (alpha * 0x40) << 24 | 0x404040);

        DrawUtils.drawRoundedRect(x + 40, armorBarY, x + 40 + (int) (healthBarWidth * Math.min(1f, animatedArmor / 20f)), armorBarY + 4, 2,
                (int) (alpha * 0xFF) << 24 | getThemeColor());

        String healthText = String.format("%.1f/%.1f", animatedHealth, maxHealth);
        fr.drawString(healthText, x + 40, y + 15, (int) (alpha * 0xFF) << 24 | 0xCCCCCC);

        GL11.glPopMatrix();
    }

    private void drawSimpleMode(AbstractClientPlayer target, float scale) {
        float alpha = Math.min(1f, scale);
        int x = HUD.targetHUDX;
        int y = HUD.targetHUDY;
        int hudWidth = 130;
        int hudHeight = 32;

        GL11.glPushMatrix();
        GL11.glTranslated(x + hudWidth / 2.0, y + hudHeight / 2.0, 0);
        GL11.glScalef(scale, scale, 1.0f);
        GL11.glTranslated(-(x + hudWidth / 2.0), -(y + hudHeight / 2.0), 0);

        long timeSinceDamage = System.currentTimeMillis() - damageFlashTime;
        float flashAlpha = timeSinceDamage < 300 ? 1f - (timeSinceDamage / 300f) : 0;

        int bgColor = new Color(26, 26, 26, (int)(alpha * 128)).getRGB();
        if (flashAlpha > 0) {
            int r = (int) (26 + (255 - 26) * flashAlpha);
            int g = (int) (26 * (1 - flashAlpha));
            int b = (int) (26 * (1 - flashAlpha));
            bgColor = new Color(r, g, b, (int)(alpha * 128)).getRGB();
        }
        DrawUtils.drawRoundedRect(x, y, x + hudWidth, y + hudHeight, 8, bgColor);

        int borderColor = flashAlpha > 0
                ? (int) (alpha * 0xFF) << 24 | 0xFF0000
                : (int) (alpha * 0xFF) << 24 | getThemeColor();
        DrawUtils.drawBorderedRoundedRect(x, y, x + hudWidth, y + hudHeight, 8, 2, borderColor, 0x00000000);

        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        if (fr == null) { GL11.glPopMatrix(); return; }

        String name = StringUtils.stripControlCodes(target.getName());
        fr.drawStringWithShadow(name, x + 5, y + 5, (int) (alpha * 0xFF) << 24 | 0xFFFFFF);

        float health = target.getHealth();
        float maxHealth = target.getMaxHealth();
        if (animatedHealth == 0 || target == mc.thePlayer) animatedHealth = health;
        animatedHealth = interpolate(animatedHealth, health, 0.1f);
        float healthPercent = animatedHealth / maxHealth;

        int healthBarY = y + 19;
        int healthBarWidth = hudWidth - 10;

        DrawUtils.drawRoundedRect(x + 5, healthBarY, x + 5 + healthBarWidth, healthBarY + 8, 4,
                (int) (alpha * 0x40) << 24 | 0x404040);

        int healthColor = healthPercent > 0.5f ? getThemeColor() : (healthPercent > 0.25f ? 0xFFFFFF00 : 0xFFFF0000);
        DrawUtils.drawRoundedRect(x + 5, healthBarY, x + 5 + (int) (healthBarWidth * healthPercent), healthBarY + 8, 4,
                (int) (alpha * 0xFF) << 24 | healthColor);

        String healthText = String.format("%d/%d", Math.round(animatedHealth), (int) maxHealth);
        float textWidth = fr.getWidth(healthText);
        fr.drawString(healthText, (int) (x + hudWidth - 5 - textWidth), y + 5, (int) (alpha * 0xFF) << 24 | 0xCCCCCC);

        GL11.glPopMatrix();
    }

    private int getThemeColor() {
        return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
    }

    private void renderStickHUD(AbstractClientPlayer en, double renderX, double renderY, double renderZ, float scale) {
        GlStateManager.pushMatrix();
        GL11.glTranslated(renderX, renderY + en.height + 0.5, renderZ);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.disableDepth();
        float s = 0.02666667F;
        GlStateManager.scale(-s, -s, s);
        GlStateManager.translate(35, -15, 0);

        int origX = HUD.targetHUDX;
        int origY = HUD.targetHUDY;
        HUD.targetHUDX = 0;
        HUD.targetHUDY = 0;

        switch (mode.getValue()) {
            case Simple:
                drawSimpleMode(en, scale);
                break;
            default:
                drawFaceMode(en, scale);
                break;
        }

        HUD.targetHUDX = origX;
        HUD.targetHUDY = origY;
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    @Override
    protected void onEnable() {
        target = null;
        animatedScale = 0f;
        recentTargets.clear();
    }

    @Override
    protected void onDisable() {
        target = null;
        animatedScale = 0f;
        recentTargets.clear();
    }

    private float interpolate(float current, float target, float speed) {
        return current + (target - current) * speed;
    }

    public enum TargetHUDMode {
        Face, Simple
    }
}

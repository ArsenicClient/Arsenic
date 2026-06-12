package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventShader;
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
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StringUtils;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

@ModuleInfo(name = "TargetHUD", category = ModuleCategory.SETTINGS, hidden = true)
public class TargetHUD extends Module {

    public final EnumProperty<TargetHUDMode> mode = new EnumProperty<>("Mode", TargetHUDMode.Face);
    public final EnumProperty<BackgroundMode> backgroundMode = new EnumProperty<>("Background", BackgroundMode.Glassy);
    public final DoubleProperty fadeTime = new DoubleProperty("Fade Time (s)", new DoubleValue(1, 10, 3, 0.5));
    public final BooleanProperty editPosition = new BooleanProperty("Edit Position", false);
    public final BooleanProperty stick = new BooleanProperty("Stick", false);
    public final BooleanProperty showArmor = new BooleanProperty("Show Armor", true);
    public final BooleanProperty showPing = new BooleanProperty("Show Ping", false);

    private AbstractClientPlayer target;
    private long lastTargetTime;
    private final Map<EntityPlayer, Long> recentTargets = new HashMap<>();
    private float animatedHealth;
    private float animatedArmor;
    private float animatedScale;
    private long lastDamageTime;
    private float damageFlashIntensity;

    // ── Blur pass (glassy background) ─────────────────────────────────────

    @EventLink
    public final Listener<EventShader.Blur> blurListener = event -> {
        if (animatedScale <= 0.01f || backgroundMode.getValue() != BackgroundMode.Glassy) return;
        HudBounds b = computeHudBounds();
        if (b == null) return;
        float radius = mode.getValue() == TargetHUDMode.Face ? 10f : 6f;
        GL11.glPushMatrix();
        applyScaleTransform(b);
        DrawUtils.drawRoundedRect(b.x, b.y, b.x + b.w, b.y + b.h, radius, -1);
        GL11.glPopMatrix();
    };

    // ── Event handlers ────────────────────────────────────────────────────

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        if (event.getTarget() instanceof EntityPlayer) {
            EntityPlayer targetPlayer = (EntityPlayer) event.getTarget();
            target = (AbstractClientPlayer) targetPlayer;
            lastTargetTime = System.currentTimeMillis();
            lastDamageTime = System.currentTimeMillis();
            damageFlashIntensity = 1.0f;
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
            renderStickHUD(target, renderX, renderY, renderZ);
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        if (editPosition.getValue()) {
            target = mc.thePlayer;
            drawTargetHUD(target, 1.0f);
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
            drawTargetHUD(renderTarget, animatedScale);
        }
        if (renderTarget == null && !fadingOut) {
            target = null;
        }
    };

    // ── HUD drawing ───────────────────────────────────────────────────────

    private void drawTargetHUD(AbstractClientPlayer target, float scale) {
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
        float alpha = Math.max(0, Math.min(1f, scale));
        HudBounds b = computeHudBounds();
        if (b == null) return;

        GL11.glPushMatrix();
        applyScaleTransform(b);

        long timeSinceDamage = System.currentTimeMillis() - lastDamageTime;
        damageFlashIntensity = Math.max(0, 1.0f - (timeSinceDamage / 300f));

        float radius = 10f;

        int baseBgColor = new Color(20, 20, 20, clampAlpha(alpha * 165)).getRGB();
        int borderColor = new Color(255, 255, 255, clampAlpha(alpha * 25)).getRGB();

        if (damageFlashIntensity > 0) {
            int flashAlpha = clampAlpha(damageFlashIntensity * 150);
            baseBgColor = new Color(40, 20, 20, clampAlpha(alpha * 165 + flashAlpha)).getRGB();
            borderColor = new Color(255, 50, 50, clampAlpha(alpha * 255)).getRGB();
        }

        DrawUtils.drawRoundedRect(b.x, b.y, b.x + b.w, b.y + b.h, radius, baseBgColor);
        DrawUtils.drawRoundedOutline(b.x, b.y, b.x + b.w, b.y + b.h, radius, 1.5f, borderColor);

        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        if (fr == null) { GL11.glPopMatrix(); return; }

        GL11.glColor4f(1, 1, 1, alpha);
        mc.getTextureManager().bindTexture(target.getLocationSkin());
        Gui.drawScaledCustomSizeModalRect(b.x + 8, b.y + 8, 8.0F, 8.0F, 8, 8, 35, 35, 64.0F, 64.0F);

        int whiteCol = clampAlpha(alpha * 0xFF) << 24 | 0xFFFFFF;
        int grayCol = clampAlpha(alpha * 0xFF) << 24 | 0xAAAAAA;

        String name = StringUtils.stripControlCodes(target.getName());
        fr.drawString(name, b.x + 50, b.y + 9, whiteCol);

        if (showPing.getValue()) {
            String pingStr;
            try {
                int ping = mc.getNetHandler().getPlayerInfo(target.getUniqueID()).getResponseTime();
                pingStr = ping + "ms";
            } catch (Exception e) {
                pingStr = "?ms";
            }
            float pingX = (b.x + b.w) - 5 - fr.getWidth(pingStr);
            fr.drawString(pingStr, pingX, b.y + 9, grayCol);
        }

        int armor = target.getTotalArmorValue();
        animatedArmor = interpolate(animatedArmor, armor, 0.1f);

        float health = target.getHealth();
        float maxHealth = target.getMaxHealth();
        animatedHealth = interpolate(animatedHealth, health, 0.1f);
        float healthPercent = Math.max(0, Math.min(1, animatedHealth / maxHealth));

        int barX = b.x + 50;
        int barW = b.w - 60;
        int barY = b.y + 26;
        int barH = 4;

        int hpColor = getHealthColor(healthPercent);

        DrawUtils.drawRoundedRect(barX, barY, barX + barW, barY + barH, barH/2f,
            new Color(0, 0, 0, clampAlpha(alpha * 100)).getRGB());

        if (animatedHealth > 1) {
            int glowColor = (hpColor & 0x00FFFFFF) | (clampAlpha(alpha * 60) << 24);
            DrawUtils.drawRoundedRect(barX - 2, barY - 2, barX + barW + 2, barY + barH + 2, barH/2f + 1, glowColor);
        }

        int fillW = (int) (barW * healthPercent);
        if (fillW > 0) {
            DrawUtils.drawRoundedRect(barX, barY, barX + fillW, barY + barH, barH/2f, hpColor);
        }

        String hpText = String.format("%.0f/%.0f", animatedHealth, maxHealth);
        float hpTextW = fr.getWidth(hpText);
        fr.drawStringWithShadow(hpText, barX + (barW - hpTextW) / 2f, barY - 1, whiteCol);

        if (showArmor.getValue()) {
            int armorY = barY + barH + 3;
            int armorH = 2;
            float armorPercent = Math.min(1f, animatedArmor / 20f);
            int armorColor = new Color(170, 221, 255, clampAlpha(alpha * 255)).getRGB();

            DrawUtils.drawRoundedRect(barX, armorY, barX + barW, armorY + armorH, armorH/2f,
                new Color(0, 0, 0, clampAlpha(alpha * 100)).getRGB());

            int armorFillW = (int) (barW * armorPercent);
            if (armorFillW > 0) {
                DrawUtils.drawRoundedRect(barX, armorY, barX + armorFillW, armorY + armorH, armorH/2f, armorColor);
            }
        }

        GL11.glPopMatrix();
    }

    private void drawSimpleMode(AbstractClientPlayer target, float scale) {
        float alpha = Math.max(0, Math.min(1f, scale));
        HudBounds b = computeHudBounds();
        if (b == null) return;

        GL11.glPushMatrix();
        applyScaleTransform(b);

        long timeSinceDamage = System.currentTimeMillis() - lastDamageTime;
        damageFlashIntensity = Math.max(0, 1.0f - (timeSinceDamage / 300f));

        float radius = 6f;

        int baseBgColor = new Color(20, 20, 20, clampAlpha(alpha * 165)).getRGB();
        int borderColor = new Color(255, 255, 255, clampAlpha(alpha * 25)).getRGB();

        if (damageFlashIntensity > 0) {
            int flashAlpha = clampAlpha(damageFlashIntensity * 150);
            baseBgColor = new Color(40, 20, 20, clampAlpha(alpha * 165 + flashAlpha)).getRGB();
            borderColor = new Color(255, 50, 50, clampAlpha(alpha * 255)).getRGB();
        }

        DrawUtils.drawRoundedRect(b.x, b.y, b.x + b.w, b.y + b.h, radius, baseBgColor);
        DrawUtils.drawRoundedOutline(b.x, b.y, b.x + b.w, b.y + b.h, radius, 1.5f, borderColor);

        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        if (fr == null) { GL11.glPopMatrix(); return; }

        int whiteCol = clampAlpha(alpha * 0xFF) << 24 | 0xFFFFFF;

        String name = StringUtils.stripControlCodes(target.getName());
        fr.drawString(name, b.x + 6, b.y + 6, whiteCol);

        float health = target.getHealth();
        float maxHealth = target.getMaxHealth();
        animatedHealth = interpolate(animatedHealth, health, 0.1f);
        float healthPercent = Math.max(0, Math.min(1, animatedHealth / maxHealth));

        int barX = b.x + 6;
        int barW = b.w - 12;
        int barY = b.y + 18;
        int barH = 6;

        int hpColor = getHealthColor(healthPercent);

        DrawUtils.drawRoundedRect(barX, barY, barX + barW, barY + barH, barH/2f,
            new Color(0, 0, 0, clampAlpha(alpha * 100)).getRGB());

        if (animatedHealth > 1) {
            int glowColor = (hpColor & 0x00FFFFFF) | (clampAlpha(alpha * 60) << 24);
            DrawUtils.drawRoundedRect(barX - 2, barY - 2, barX + barW + 2, barY + barH + 2, barH/2f + 1, glowColor);
        }

        int fillW = (int) (barW * healthPercent);
        if (fillW > 0) {
            DrawUtils.drawRoundedRect(barX, barY, barX + fillW, barY + barH, barH/2f, hpColor);
        }

        String hpText = String.format("%.0f/%.0f", animatedHealth, maxHealth);
        float hpTextW = fr.getWidth(hpText);
        fr.drawStringWithShadow(hpText, barX + (barW - hpTextW) / 2f, barY - 1, whiteCol);

        GL11.glPopMatrix();
    }

    // ── Stick HUD ────────────────────────────────────────────────────────

    private void renderStickHUD(AbstractClientPlayer en, double renderX, double renderY, double renderZ) {
        GlStateManager.pushMatrix();
        try {
            GL11.glTranslated(renderX, renderY + en.height + 0.5, renderZ);
            GL11.glNormal3f(0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
            GlStateManager.disableDepth();
            float s = 0.02666667F;
            GlStateManager.scale(-s, -s, s);
            GlStateManager.translate(100, -35, 0);

            int origX = HUD.targetHUDX;
            int origY = HUD.targetHUDY;
            HUD.targetHUDX = 0;
            HUD.targetHUDY = 0;

            try {
                switch (mode.getValue()) {
                    case Simple:
                        drawSimpleMode(en, animatedScale);
                        break;
                    default:
                        drawFaceMode(en, animatedScale);
                        break;
                }
            } finally {
                HUD.targetHUDX = origX;
                HUD.targetHUDY = origY;
            }
        } finally {
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        }
    }

    // ── Geometry helpers ─────────────────────────────────────────────────

    private static class HudBounds {
        int x, y, w, h;
    }

    private HudBounds computeHudBounds() {
        int w, h;
        switch (mode.getValue()) {
            case Simple:
                w = 130; h = 38;
                break;
            default:
                w = 160; h = 55;
                break;
        }
        if (showArmor.getValue() && mode.getValue() == TargetHUDMode.Face) {
            h += 7;
        }

        HudBounds b = new HudBounds();
        b.x = HUD.targetHUDX;
        b.y = HUD.targetHUDY;
        b.w = w;
        b.h = h;
        return b;
    }

    private void applyScaleTransform(HudBounds b) {
        float cx = b.x + b.w / 2f;
        float cy = b.y + b.h / 2f;
        GL11.glTranslated(cx, cy, 0);
        GL11.glScalef(animatedScale, animatedScale, 1.0f);
        GL11.glTranslated(-cx, -cy, 0);
    }

    // ── Color helpers ────────────────────────────────────────────────────

    private int getThemeColor() {
        return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
    }

    private int getHealthColor(float percent) {
        if (percent > 0.5f) return 0xFF2ecc71; // Neon Green
        if (percent > 0.25f) return 0xFFf1c40f; // Yellow
        return 0xFFe74c3c; // Red
    }

    private int interpolateColor(int color1, int color2, float t) {
        int r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    private float interpolate(float current, float target, float speed) {
        return current + (target - current) * speed;
    }

    private static int clampAlpha(float value) {
        if (value < 0) return 0;
        if (value > 255) return 255;
        return (int) value;
    }

    // ── Enums / lifecycle ────────────────────────────────────────────────

    public enum TargetHUDMode {
        Face, Simple
    }

    public enum BackgroundMode {
        Glassy, Solid
    }

    @Override
    protected void onEnable() {
        target = null;
        animatedScale = 0f;
        damageFlashIntensity = 0f;
        recentTargets.clear();
    }

    @Override
    protected void onDisable() {
        target = null;
        animatedScale = 0f;
        damageFlashIntensity = 0f;
        recentTargets.clear();
    }
}
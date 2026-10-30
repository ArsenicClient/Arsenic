package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventShader;
import arsenic.gui.design.M3;
import arsenic.gui.hud.HudEditorScreen;
import arsenic.gui.hud.HudElement;
import arsenic.gui.hud.HudStyle;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.MathUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.render.shader.ShaderUtil;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StringUtils;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

@ModuleInfo(name = "TargetHUD", category = ModuleCategory.RENDER, hidden = true, tier = ModuleTier.VISUAL)
public class TargetHUD extends Module {

    private static final int FACE_W = 156, FACE_H = 46;
    private static final int SIMPLE_W = 136, SIMPLE_H = 34;

    private final HudElement hud = hudElement("TargetHUD", 100, 100, FACE_W, FACE_H);
    private static final ShaderUtil roundedFaceShader = new ShaderUtil("roundedFace");

    public final EnumProperty<TargetHUDMode> mode = new EnumProperty<>("Mode", TargetHUDMode.Face);
    public final DoubleProperty fadeTime = new DoubleProperty("Fade Time (s)", new DoubleValue(1, 10, 3, 0.5));
    public final BooleanProperty editPosition = new BooleanProperty("Edit Position", false);
    public final BooleanProperty stick = new BooleanProperty("Stick", false);

    private AbstractClientPlayer target;
    private long lastTargetTime;
    private final Map<EntityPlayer, Long> recentTargets = new HashMap<>();
    private float animatedHealth;
    private float trailHealth;
    private float animatedArmor;
    private float animatedScale;
    private long damageFlashTime;
    private float lastHealth = -1f;
    private AbstractClientPlayer renderedTarget;

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
        updateSize();
        if (editPosition.getValue() || mc.currentScreen instanceof HudEditorScreen || mc.currentScreen instanceof GuiChat) {
            if (target == null || System.currentTimeMillis() - lastTargetTime >= (long) (fadeTime.getValue().getInput() * 1000)) {
                if (editPosition.getValue() || mc.currentScreen instanceof HudEditorScreen) {
                    target = mc.thePlayer;
                    animatedScale = 1.0f;
                    drawTargetHUD(target, 0, 10000000, 1.0f);
                    return;
                }
            }
        }

        long currentTime = System.currentTimeMillis();
        long fadeMs = (long) (fadeTime.getValue().getInput() * 1000);

        recentTargets.entrySet().removeIf(entry -> currentTime - entry.getValue() > fadeMs);

        boolean draw2D = !stick.getValue();
        AbstractClientPlayer renderTarget = null;
        boolean fadingOut = false;

        if (target != null && currentTime - lastTargetTime < fadeMs) {
            animatedScale = interpolate(animatedScale, 1.0f, 0.14f);
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
                animatedScale = interpolate(animatedScale, 1.0f, 0.14f);
                renderTarget = (AbstractClientPlayer) mostRecent;
            }
        } else {
            if (animatedScale > 0.01f) {
                animatedScale = interpolate(animatedScale, 0.0f, 0.16f);
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

    @RequiresPlayer
    @EventLink
    public final Listener<EventShader.Blur> blurListener = event -> {
        if (stick.getValue() || target == null || animatedScale <= 0.05f)
            return;
        float x = HudStyle.snap(hud.x);
        float y = HudStyle.snap(hud.y);
        float w = mode.getValue() == TargetHUDMode.Simple ? SIMPLE_W : FACE_W;
        float h = mode.getValue() == TargetHUDMode.Simple ? SIMPLE_H : FACE_H;
        GL11.glPushMatrix();
        GL11.glTranslated(x + w / 2.0, y + h / 2.0, 0);
        GL11.glScalef(animatedScale, animatedScale, 1.0f);
        GL11.glTranslated(-(x + w / 2.0), -(y + h / 2.0), 0);
        DrawUtils.drawRoundedRect(x, y, x + w, y + h, HudStyle.RADIUS_CARD, 0xFFFFFFFF);
        GL11.glPopMatrix();
    };

    private void updateSize() {
        if (mode.getValue() == TargetHUDMode.Simple)
            hud.setSize(SIMPLE_W, SIMPLE_H);
        else
            hud.setSize(FACE_W, FACE_H);
    }

    private void updateHealthState(AbstractClientPlayer target) {
        float health = Math.max(0f, target.getHealth());
        int armor = target.getTotalArmorValue();
        if (renderedTarget != target || lastHealth < 0f) {
            renderedTarget = target;
            animatedHealth = health;
            trailHealth = health;
            animatedArmor = armor;
            lastHealth = health;
            damageFlashTime = 0L;
        }
        if (health < lastHealth - 0.1f || target.hurtTime >= 8) {
            damageFlashTime = System.currentTimeMillis();
        }
        lastHealth = health;
        animatedHealth = interpolate(animatedHealth, health, 0.16f);
        if (trailHealth < animatedHealth)
            trailHealth = animatedHealth;
        else
            trailHealth = interpolate(trailHealth, animatedHealth, 0.055f);

        animatedArmor = interpolate(animatedArmor, armor, 0.15f);
    }

    private void drawTargetHUD(AbstractClientPlayer target, long timeSinceTarget, long fadeTime, float scale) {
        if (scale <= 0.01f || target == null)
            return;
        updateHealthState(target);
        switch (mode.getValue()) {
            case Simple:
                drawSimpleMode(target, scale);
                break;
            default:
                drawFaceMode(target, scale);
                break;
        }
        HudStyle.resetGl();
    }

    private void drawFaceMode(AbstractClientPlayer target, float scale) {
        float alpha = MathUtils.clamp01(scale);
        int cardAlpha = (int) (205 * alpha);
        float x = HudStyle.snap(hud.x);
        float y = HudStyle.snap(hud.y);
        float hudWidth = FACE_W;
        float hudHeight = FACE_H;
        float pad = 7f;
        float faceSize = 32f;

        GL11.glPushMatrix();
        GL11.glTranslated(x + hudWidth / 2.0, y + hudHeight / 2.0, 0);
        GL11.glScalef(scale, scale, 1.0f);
        GL11.glTranslated(-(x + hudWidth / 2.0), -(y + hudHeight / 2.0), 0);

        long timeSinceDamage = System.currentTimeMillis() - damageFlashTime;
        float flashPct = timeSinceDamage < 280L ? 1f - (timeSinceDamage / 280f) : 0f;

        HudStyle.card(x, y, x + hudWidth, y + hudHeight, HudStyle.RADIUS_CARD, cardAlpha);
        if (flashPct > 0.01f) {
            DrawUtils.drawRoundedOutline(x, y, x + hudWidth, y + hudHeight, HudStyle.RADIUS_CARD, 1.2f,
                    M3.alpha(M3.error(), (int) (210 * flashPct * alpha)));
        }

        float faceX = HudStyle.snap(x + pad);
        float faceY = HudStyle.snap(y + (hudHeight - faceSize) / 2f);
        drawPlayerFace(target, faceX, faceY, faceSize, 6f, alpha, flashPct);

        FontRendererExtension<?> bodyFr = HudStyle.body();
        FontRendererExtension<?> labelFr = HudStyle.label();

        float colX = HudStyle.snap(faceX + faceSize + pad);
        float rightX = HudStyle.snap(x + hudWidth - pad);
        float colW = rightX - colX;

        float maxHealth = Math.max(1f, target.getMaxHealth());
        float healthPct = MathUtils.clamp01(animatedHealth / maxHealth);
        float trailPct = MathUtils.clamp01(trailHealth / maxHealth);

        // Row 1: Name on left, HP badge pill on right
        float row1Cy = faceY + 5.5f;
        String hpText = String.format("%.1f HP", animatedHealth);
        int hpChipBg = flashPct > 0.2f
                ? M3.alpha(M3.errorContainer(), (int) (230 * alpha))
                : M3.alpha(M3.primaryContainer(), (int) (230 * alpha));
        int hpChipFg = flashPct > 0.2f
                ? M3.alpha(M3.onErrorContainer(), (int) (255 * alpha))
                : M3.alpha(M3.onPrimaryContainer(), (int) (255 * alpha));
        float chipW = HudStyle.rightChip(labelFr, hpText, rightX, row1Cy, 11.5f, 4.5f, hpChipBg, hpChipFg);

        String rawName = StringUtils.stripControlCodes(target.getName());
        String name = trimToWidth(labelFr, rawName, colW - chipW - 4f);
        labelFr.drawString(name, colX, row1Cy, M3.alpha(M3.onSurface(), (int) (255 * alpha)), labelFr.CENTREY);

        // Row 2: Distance on left, Armor / Absorption on right
        float row2Cy = faceY + 16.5f;
        float dist = mc.thePlayer != null ? mc.thePlayer.getDistanceToEntity(target) : 0f;
        String distText = String.format("%.1f m", dist);
        int mutedCol = M3.alpha(M3.onSurfaceVariant(), (int) (230 * alpha));
        bodyFr.setScale(0.86f);
        bodyFr.drawString(distText, colX, row2Cy, mutedCol, bodyFr.CENTREY);

        float absorption = target.getAbsorptionAmount();
        String statRight = absorption > 0.1f
                ? String.format("+%.1f ABS", absorption)
                : String.format("%d%% ARM", Math.round(MathUtils.clamp01(animatedArmor / 20f) * 100f));
        int statRightCol = absorption > 0.1f
                ? M3.alpha(M3.tertiary(), (int) (245 * alpha))
                : mutedCol;
        bodyFr.drawString(statRight, rightX - bodyFr.getWidth(statRight), row2Cy, statRightCol, bodyFr.CENTREY);
        bodyFr.resetScale();

        // Row 3: Health bar & Armor bar aligned cleanly with bottom of face
        float hpBarY1 = faceY + 24f;
        float hpBarY2 = faceY + 28.5f;
        int trackCol = M3.alpha(M3.surfaceContainerHighest(), (int) (210 * alpha));
        int activeHpCol = healthColor(healthPct, alpha);
        int trailHpCol = M3.alpha(M3.error(), (int) (155 * alpha));
        HudStyle.progressBar(colX, hpBarY1, rightX, hpBarY2, healthPct, trailPct, activeHpCol, trailHpCol, trackCol);

        float armBarY1 = faceY + 30f;
        float armBarY2 = faceY + 32f;
        float armorPct = MathUtils.clamp01(animatedArmor / 20f);
        int armCol = M3.alpha(M3.secondary(), (int) (235 * alpha));
        HudStyle.progressBar(colX, armBarY1, rightX, armBarY2, armorPct, 0f, armCol, 0, trackCol);

        GL11.glPopMatrix();
    }

    private void drawSimpleMode(AbstractClientPlayer target, float scale) {
        float alpha = MathUtils.clamp01(scale);
        int cardAlpha = (int) (205 * alpha);
        float x = HudStyle.snap(hud.x);
        float y = HudStyle.snap(hud.y);
        float hudWidth = SIMPLE_W;
        float hudHeight = SIMPLE_H;
        float pad = 7f;

        GL11.glPushMatrix();
        GL11.glTranslated(x + hudWidth / 2.0, y + hudHeight / 2.0, 0);
        GL11.glScalef(scale, scale, 1.0f);
        GL11.glTranslated(-(x + hudWidth / 2.0), -(y + hudHeight / 2.0), 0);

        long timeSinceDamage = System.currentTimeMillis() - damageFlashTime;
        float flashPct = timeSinceDamage < 280L ? 1f - (timeSinceDamage / 280f) : 0f;

        HudStyle.card(x, y, x + hudWidth, y + hudHeight, HudStyle.RADIUS_CARD, cardAlpha);
        if (flashPct > 0.01f) {
            DrawUtils.drawRoundedOutline(x, y, x + hudWidth, y + hudHeight, HudStyle.RADIUS_CARD, 1.2f,
                    M3.alpha(M3.error(), (int) (210 * flashPct * alpha)));
        }

        FontRendererExtension<?> labelFr = HudStyle.label();
        float maxHealth = Math.max(1f, target.getMaxHealth());
        float healthPct = MathUtils.clamp01(animatedHealth / maxHealth);
        float trailPct = MathUtils.clamp01(trailHealth / maxHealth);

        float rowCy = y + pad + 5.5f;
        float rightX = x + hudWidth - pad;
        String hpText = String.format("%.1f / %.0f", animatedHealth, maxHealth);
        float chipW = HudStyle.rightChip(labelFr, hpText, rightX, rowCy, 11.5f, 4.5f,
                M3.alpha(M3.primaryContainer(), (int) (225 * alpha)),
                M3.alpha(M3.onPrimaryContainer(), (int) (255 * alpha)));

        String rawName = StringUtils.stripControlCodes(target.getName());
        String name = trimToWidth(labelFr, rawName, (rightX - (x + pad)) - chipW - 4f);
        labelFr.drawString(name, x + pad, rowCy, M3.alpha(M3.onSurface(), (int) (255 * alpha)), labelFr.CENTREY);

        float barY1 = y + hudHeight - pad - 5f;
        float barY2 = y + hudHeight - pad;
        int trackCol = M3.alpha(M3.surfaceContainerHighest(), (int) (210 * alpha));
        int activeHpCol = healthColor(healthPct, alpha);
        int trailHpCol = M3.alpha(M3.error(), (int) (155 * alpha));
        HudStyle.progressBar(x + pad, barY1, rightX, barY2, healthPct, trailPct, activeHpCol, trailHpCol, trackCol);

        GL11.glPopMatrix();
    }

    private int healthColor(float healthPct, float alpha) {
        int base;
        if (healthPct >= 0.5f) {
            base = M3.primary();
        } else if (healthPct >= 0.25f) {
            base = M3.mix(M3.error(), M3.primary(), (healthPct - 0.25f) / 0.25f);
        } else {
            base = M3.error();
        }
        return M3.alpha(base, (int) (255 * alpha));
    }

    private void drawPlayerFace(AbstractClientPlayer target, float x, float y, float size, float radius,
                                float alpha, float hurtPct) {
        DrawUtils.drawRoundedRect(x, y, x + size, y + size, radius,
                M3.alpha(M3.surfaceContainerHighest(), (int) (220 * alpha)));
        float sf = Math.max(1f, HudStyle.scaleFactor());
        try {
            mc.getTextureManager().bindTexture(target.getLocationSkin());
            RenderUtils.resetColor();
            RenderUtils.startBlend();
            RenderUtils.applyGuiBlend();
            roundedFaceShader.init();
            roundedFaceShader.setUniformi("textureIn", 0);
            roundedFaceShader.setUniformf("rectSize", size * sf, size * sf);
            roundedFaceShader.setUniformf("radius", radius * sf);
            roundedFaceShader.setUniformf("alpha", alpha);
            roundedFaceShader.setUniformf("uvMin", 8f / 64f, 8f / 64f);
            roundedFaceShader.setUniformf("uvMax", 16f / 64f, 16f / 64f);
            ShaderUtil.drawQuads(x, y, size, size);
            roundedFaceShader.setUniformf("uvMin", 40f / 64f, 8f / 64f);
            roundedFaceShader.setUniformf("uvMax", 48f / 64f, 16f / 64f);
            ShaderUtil.drawQuads(x, y, size, size);
            roundedFaceShader.unload();
            RenderUtils.endBlend();
            if (hurtPct > 0.01f) {
                DrawUtils.drawRoundedRect(x, y, x + size, y + size, radius,
                        M3.alpha(M3.error(), (int) (115 * hurtPct * alpha)));
            }
        } catch (Throwable t) {
            GlStateManager.enableBlend();
            GlStateManager.color(1f, 1f - hurtPct * 0.45f, 1f - hurtPct * 0.45f, alpha);
            mc.getTextureManager().bindTexture(target.getLocationSkin());
            Gui.drawScaledCustomSizeModalRect(Math.round(x), Math.round(y), 8.0F, 8.0F, 8, 8,
                    Math.round(size), Math.round(size), 64.0F, 64.0F);
        }
        int outlineCol = hurtPct > 0.1f
                ? M3.alpha(M3.error(), (int) (200 * alpha))
                : M3.alpha(M3.outlineVariant(), (int) (140 * alpha));
        DrawUtils.drawRoundedOutline(x, y, x + size, y + size, radius, 1f, outlineCol);
        HudStyle.resetGl();
    }

    private static String trimToWidth(FontRendererExtension<?> fr, String text, float maxWidth) {
        if (text == null || fr.getWidth(text) <= maxWidth)
            return text == null ? "" : text;
        String ellipsis = "..";
        float ellW = fr.getWidth(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (fr.getWidth(sb.toString() + c) + ellW > maxWidth)
                break;
            sb.append(c);
        }
        return sb.append(ellipsis).toString();
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

        int origX = hud.x;
        int origY = hud.y;
        hud.x = 0;
        hud.y = 0;

        updateHealthState(en);
        switch (mode.getValue()) {
            case Simple:
                drawSimpleMode(en, scale);
                break;
            default:
                drawFaceMode(en, scale);
                break;
        }

        hud.x = origX;
        hud.y = origY;
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    @Override
    protected void onEnable() {
        target = null;
        renderedTarget = null;
        animatedScale = 0f;
        lastHealth = -1f;
        recentTargets.clear();
    }

    @Override
    protected void onDisable() {
        target = null;
        renderedTarget = null;
        animatedScale = 0f;
        lastHealth = -1f;
        recentTargets.clear();
    }

    private float interpolate(float current, float target, float speed) {
        return current + (target - current) * speed;
    }

    public enum TargetHUDMode {
        Face, Simple
    }
}

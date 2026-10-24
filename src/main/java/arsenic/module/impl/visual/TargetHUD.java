package arsenic.module.impl.visual;

import arsenic.module.ModuleTier;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.gui.design.M3;
import arsenic.gui.hud.HudElement;
import arsenic.gui.hud.HudStyle;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.render.shader.ShaderUtil;
import arsenic.utils.timer.FrameClock;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StringUtils;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

@ModuleInfo(name = "TargetHUD", category = ModuleCategory.RENDER, hidden = true, tier = ModuleTier.VISUAL)
public class TargetHUD extends Module {

    private static final ShaderUtil ROUNDED_FACE_SHADER = new ShaderUtil("roundedFace");

    private final HudElement hud = hudElement("TargetHUD", 100, 100, 218, 64);

    public final EnumProperty<TargetHUDMode> mode = new EnumProperty<>("Mode", TargetHUDMode.Face);
    public final DoubleProperty fadeTime = new DoubleProperty("Fade Time (s)", new DoubleValue(1, 10, 3, 0.5));
    public final BooleanProperty editPosition = new BooleanProperty("Edit Position", false);
    public final BooleanProperty stick = new BooleanProperty("Stick", false);
    @ShowWhen(property = "Stick", is = "true")
    public final DoubleProperty stickScale = new DoubleProperty("Stick Scale", new DoubleValue(0.4, 3, 1, 0.05));
    @ShowWhen(property = "Stick", is = "true")
    public final BooleanProperty adaptiveStickScale = new BooleanProperty("Adaptive Stick Scale", false);

    private AbstractClientPlayer target;
    private EntityPlayer animatedEntity;
    private long lastTargetTime;
    private final Map<EntityPlayer, Long> recentTargets = new HashMap<>();
    private final FrameClock clock = new FrameClock();
    private float animatedHealth;
    private float animatedArmor;
    private float animatedAbsorption;
    private float animatedScale;
    private long damageFlashTime;
    private float lastHealth;
    private float lastDt = 1f / 60f;

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        if (event.getTarget() instanceof EntityPlayer) {
            EntityPlayer targetPlayer = (EntityPlayer) event.getTarget();
            if (target == targetPlayer && targetPlayer.getHealth() < lastHealth - 0.01f)
                damageFlashTime = System.currentTimeMillis();
            lastHealth = targetPlayer.getHealth();
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
        long now = System.currentTimeMillis();
        long fadeMs = (long) (fadeTime.getValue().getInput() * 1000);
        float dt = clock.tick();
        lastDt = dt;

        if (editPosition.getValue()) {
            target = mc.thePlayer;
            animate(mc.thePlayer, dt);
            drawTargetHUD(mc.thePlayer, 1f, dt);
            return;
        }

        recentTargets.entrySet().removeIf(entry -> now - entry.getValue() > fadeMs);

        boolean draw2D = !stick.getValue();
        AbstractClientPlayer renderTarget = null;

        if (target != null && now - lastTargetTime < fadeMs) {
            animatedScale = FrameClock.approach(animatedScale, 1f, 16f, dt);
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
            if (mostRecent != null && now - mostRecentTime < fadeMs) {
                animatedScale = FrameClock.approach(animatedScale, 1f, 16f, dt);
                renderTarget = (AbstractClientPlayer) mostRecent;
            }
        }

        if (renderTarget == null) {
            if (animatedScale > 0.01f && target != null) {
                animatedScale = FrameClock.approach(animatedScale, 0f, 14f, dt);
                renderTarget = target;
            } else {
                animatedScale = 0f;
                target = null;
                animatedEntity = null;
            }
        }

        if (renderTarget != null && draw2D)
            drawTargetHUD(renderTarget, animatedScale, dt);
    };

    private void drawTargetHUD(AbstractClientPlayer target, float scale, float dt) {
        if (scale <= 0.01f)
            return;
        if (mode.getValue() == TargetHUDMode.Simple)
            drawSimpleMode(target, scale, dt);
        else
            drawFaceMode(target, scale, dt);
    }

    private void animate(EntityPlayer target, float dt) {
        long now = System.currentTimeMillis();
        if (animatedEntity != target) {
            animatedEntity = target;
            animatedHealth = target.getHealth();
            animatedArmor = target.getTotalArmorValue();
            animatedAbsorption = target.getAbsorptionAmount();
            lastHealth = target.getHealth();
            animatedScale = 1f;
        }

        if (target.getHealth() < lastHealth - 0.01f)
            damageFlashTime = now;
        lastHealth = target.getHealth();

        // animatedHealth is the lazy "recent damage" trail: it snaps up when health recovers,
        // but falls slowly so the grey line lags behind the instant main bar
        animatedHealth = target.getHealth() > animatedHealth
                ? target.getHealth()
                : FrameClock.approach(animatedHealth, target.getHealth(), 3.5f, dt);
        animatedArmor = FrameClock.approach(animatedArmor, target.getTotalArmorValue(), 11f, dt);
        animatedAbsorption = FrameClock.approach(animatedAbsorption, target.getAbsorptionAmount(), 11f, dt);
    }

    private void drawFaceMode(AbstractClientPlayer target, float scale, float dt) {
        animate(target, dt);
        float alpha = Math.min(1f, scale);
        float w = 218f, h = 64f;
        long now = System.currentTimeMillis();
        long sinceDamage = now - damageFlashTime;
        float flash = sinceDamage < 360 ? 1f - sinceDamage / 360f : 0f;
        float shake = flash > 0 ? (float) Math.sin(now / 22.0) * 1.6f * flash : 0f;
        float x = hud.x + shake;
        float y = hud.y;
        hud.setSize((int) w, (int) h);

        GL11.glPushMatrix();
        GL11.glTranslated(x + w / 2.0, y + h / 2.0, 0);
        GL11.glScalef(scale, scale, 1f);
        GL11.glTranslated(-(x + w / 2.0), -(y + h / 2.0), 0);
        HudStyle.pushScale(x, y, hud.scale);

        int fill = M3.alpha(M3.surfaceContainerHigh(), (int) (alpha * 246));
        if (flash > 0)
            fill = M3.mix(fill, M3.alpha(M3.errorContainer(), (int) (alpha * 246)), flash * 0.55f);
        HudStyle.card(x, y, x + w, y + h, 16f, fill,
                M3.alpha(flash > 0 ? M3.error() : M3.outlineVariant(), (int) (alpha * (flash > 0 ? 160 : 105))));

        float av = 42f;
        float ax = x + 9f, ay = y + 9f;
        // no backdrop: the face is already drawn corner-rounded through its mask

        drawRoundedFace(target, ax, ay + 3f, 42f, 11f, alpha);

        FontRendererExtension<?> nameFont = HudStyle.headline();
        FontRendererExtension<?> body = HudStyle.body();
        FontRendererExtension<?> label = HudStyle.label();

        float cx = x + 60f;
        // the main bar follows the real health instantly; animatedHealth is the slow grey trail
        float health = Math.max(0f, target.getHealth());
        float maxHealth = Math.max(1f, target.getMaxHealth());
        float pct = Math.max(0f, Math.min(1f, health / maxHealth));
        float trailPct = Math.max(0f, Math.min(1f, animatedHealth / maxHealth));
        int healthColor = pct > 0.5f ? M3.primary() : pct > 0.25f ? M3.tertiary() : M3.error();

        String name = StringUtils.stripControlCodes(target.getName());
        String hpText = String.format("%.1f", health);
        float hpW = nameFont.getWidth(hpText);
        nameFont.drawString(ellipsize(nameFont, name, x + w - 12f - hpW - 12f - cx), cx, y + 18f,
                M3.alpha(M3.onSurface(), (int) (alpha * 255)), nameFont.CENTREY);
        nameFont.drawString(hpText, x + w - 12f - hpW, y + 18f,
                M3.alpha(M3.primary(), (int) (alpha * 255)), nameFont.CENTREY);

        String distText = String.format("%.0fm", mc.thePlayer.getDistanceToEntity(target));
        // The status is our position in this fight, not the target's remaining-health percentage.
        float enemyEffectiveHealth = health + Math.max(0f, animatedAbsorption);
        float playerEffectiveHealth = Math.max(0f, mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount());
        float playerShare = playerEffectiveHealth + enemyEffectiveHealth > 0f
                ? playerEffectiveHealth / (playerEffectiveHealth + enemyEffectiveHealth) : 0.5f;
        String status = playerShare > 0.6f ? "Winning" : playerShare < 0.4f ? "Low" : "Holding";
        int statusColor = playerShare > 0.6f ? M3.primary()
                : playerShare < 0.4f ? M3.error() : M3.tertiary();
        float distW = body.getWidth(distText);
        body.drawString(distText, cx, y + 33f,
                M3.alpha(M3.onSurfaceVariant(), (int) (alpha * 235)), body.CENTREY);
        label.drawString("·", cx + distW + 5f, y + 33f,
                M3.alpha(M3.outline(), (int) (alpha * 220)), label.CENTREY);
        body.drawString(status, cx + distW + 12f, y + 33f,
                M3.alpha(statusColor, (int) (alpha * 245)), body.CENTREY);

        float barX1 = x + 10f;
        float barX2 = x + w - 10f;
        float barY = y + h - 12f;
        int track = M3.alpha(M3.surfaceContainerHighest(), (int) (alpha * 235));
        int trail = M3.alpha(M3.outline(), (int) (alpha * 185));
        HudStyle.md3Progress(barX1, barY, barX2, pct, trailPct, track, trail,
                M3.alpha(healthColor, (int) (alpha * 255)));

        if (flash > 0)
            DrawUtils.drawCircle(x + w - 13f, y + 13f, 2.4f,
                    M3.alpha(M3.error(), (int) (alpha * 220 * flash)));

        HudStyle.resetColor();
        HudStyle.popScale();
        GL11.glPopMatrix();
    }

    private void drawSimpleMode(AbstractClientPlayer target, float scale, float dt) {
        animate(target, dt);
        float alpha = Math.min(1f, scale);
        float w = 168f, h = 44f;
        long now = System.currentTimeMillis();
        long sinceDamage = now - damageFlashTime;
        float flash = sinceDamage < 320 ? 1f - sinceDamage / 320f : 0f;
        float x = hud.x + (flash > 0 ? (float) Math.sin(now / 20.0) * 1.2f * flash : 0f);
        float y = hud.y;
        hud.setSize((int) w, (int) h);

        GL11.glPushMatrix();
        GL11.glTranslated(x + w / 2.0, y + h / 2.0, 0);
        GL11.glScalef(scale, scale, 1f);
        GL11.glTranslated(-(x + w / 2.0), -(y + h / 2.0), 0);
        HudStyle.pushScale(x, y, hud.scale);

        int fill = M3.alpha(M3.surfaceContainerHigh(), (int) (alpha * 246));
        if (flash > 0)
            fill = M3.mix(fill, M3.alpha(M3.errorContainer(), (int) (alpha * 246)), flash * 0.55f);
        HudStyle.card(x, y, x + w, y + h, 14f, fill,
                M3.alpha(flash > 0 ? M3.error() : M3.outlineVariant(), (int) (alpha * (flash > 0 ? 160 : 105))));

        float av = 30f;
        float ax = x + 7f, ay = y + 7f;
        drawRoundedFace(target, ax, ay + 2f, 30f, 8f, alpha);

        FontRendererExtension<?> nameFont = HudStyle.title();
        FontRendererExtension<?> label = HudStyle.label();
        float cx = x + 44f;
        float health = Math.max(0f, target.getHealth());
        float maxHealth = Math.max(1f, target.getMaxHealth());
        float pct = Math.max(0f, Math.min(1f, health / maxHealth));
        float trailPct = Math.max(0f, Math.min(1f, animatedHealth / maxHealth));
        int healthColor = pct > 0.5f ? M3.primary() : pct > 0.25f ? M3.tertiary() : M3.error();

        String name = StringUtils.stripControlCodes(target.getName());
        String hpText = String.format("%.1f", health);
        float hpW = nameFont.getWidth(hpText);
        nameFont.drawString(ellipsize(nameFont, name, x + w - 8f - hpW - 10f - cx), cx, y + 14f,
                M3.alpha(M3.onSurface(), (int) (alpha * 255)), nameFont.CENTREY);
        nameFont.drawString(hpText, x + w - 8f - hpW, y + 14f,
                M3.alpha(M3.primary(), (int) (alpha * 255)), nameFont.CENTREY);
        label.drawString(String.format("%.0fm", mc.thePlayer.getDistanceToEntity(target)),
                cx, y + 26f, M3.alpha(M3.onSurfaceVariant(), (int) (alpha * 225)), label.CENTREY);

        float barX1 = x + 7f;
        float barX2 = x + w - 7f;
        float barY = y + h - 9f;
        int track = M3.alpha(M3.surfaceContainerHighest(), (int) (alpha * 235));
        int trail = M3.alpha(M3.outline(), (int) (alpha * 185));
        HudStyle.md3Progress(barX1, barY, barX2, pct, trailPct, track, trail,
                M3.alpha(healthColor, (int) (alpha * 255)));

        HudStyle.resetColor();
        HudStyle.popScale();
        GL11.glPopMatrix();
    }

    private void renderStickHUD(AbstractClientPlayer en, double renderX, double renderY, double renderZ, float scale) {
        GlStateManager.pushMatrix();
        GL11.glTranslated(renderX, renderY + en.height + 0.5, renderZ);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GlStateManager.disableDepth();
        float s = 0.02666667F * (float) stickScale.getValue().getInput();
        if (adaptiveStickScale.getValue()) {
            // A billboard otherwise gets very small at range. Eight blocks is the neutral/default distance.
            float distanceScale = mc.thePlayer.getDistanceToEntity(en) / 8f;
            s *= Math.max(0.6f, Math.min(3f, distanceScale));
        }
        GlStateManager.scale(-s, -s, s);
        GlStateManager.translate(35, -15, 0);

        int origX = hud.x;
        int origY = hud.y;
        hud.x = 0;
        hud.y = 0;

        if (mode.getValue() == TargetHUDMode.Simple)
            drawSimpleMode(en, scale, lastDt);
        else
            drawFaceMode(en, scale, lastDt);

        hud.x = origX;
        hud.y = origY;
        if (depthWasEnabled)
            GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    @Override
    protected void onEnable() {
        target = null;
        animatedEntity = null;
        animatedScale = 0f;
        recentTargets.clear();
    }

    @Override
    protected void onDisable() {
        target = null;
        animatedEntity = null;
        animatedScale = 0f;
        recentTargets.clear();
    }

    private static String ellipsize(FontRendererExtension<?> fr, String text, float maxWidth) {
        if (fr.getWidth(text) <= maxWidth)
            return text;
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && fr.getWidth(text.substring(0, end) + ellipsis) > maxWidth)
            end--;
        return text.substring(0, end) + ellipsis;
    }

    /** Draws the front eight-by-eight pixels of a skin through an antialiased rounded mask. */
    private static void drawRoundedFace(AbstractClientPlayer player, float x, float y, float size,
                                        float radius, float alpha) {
        float scaleFactor = new ScaledResolution(mc).getScaleFactor();
        RenderUtils.resetColor();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        RenderUtils.applyGuiBlend();
        RenderUtils.setAlphaLimit(0);
        mc.getTextureManager().bindTexture(player.getLocationSkin());

        ROUNDED_FACE_SHADER.init();
        ROUNDED_FACE_SHADER.setUniformi("textureIn", 0);
        ROUNDED_FACE_SHADER.setUniformf("rectSize", size * scaleFactor, size * scaleFactor);
        ROUNDED_FACE_SHADER.setUniformf("radius", radius * scaleFactor);
        ROUNDED_FACE_SHADER.setUniformf("alpha", alpha);
        ROUNDED_FACE_SHADER.setUniformf("uvMin", 8f / 64f, 8f / 64f);
        ROUNDED_FACE_SHADER.setUniformf("uvMax", 16f / 64f, 16f / 64f);
        ShaderUtil.drawQuads(x, y, size, size);
        ROUNDED_FACE_SHADER.unload();
        RenderUtils.resetColor();
    }

    public enum TargetHUDMode {
        Face, Simple
    }
}

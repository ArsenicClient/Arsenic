package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventRender2D;
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
import net.minecraft.client.player.AbstractClientPlayer;
import arsenic.utils.render.RenderContext;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.render.WorldToScreen;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

@ModuleInfo(name = "TargetHUD", category = ModuleCategory.RENDER, hidden = true)
public class TargetHUD extends Module {

    public final EnumProperty<TargetHUDMode> mode = new EnumProperty<>("Mode", TargetHUDMode.Face);
    public final DoubleProperty fadeTime = new DoubleProperty("Fade Time (s)", new DoubleValue(1, 10, 3, 0.5));
    public final BooleanProperty editPosition = new BooleanProperty("Edit Position", false);
    public final BooleanProperty stick = new BooleanProperty("Stick", false);

    private AbstractClientPlayer target;
    private long lastTargetTime;
    private final Map<Player, Long> recentTargets = new HashMap<>();
    private float animatedHealth;
    private float animatedArmor;
    private float animatedScale;
    private long damageFlashTime;
    private float lastHealth;

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        if (event.getTarget() instanceof Player) {
            Player targetPlayer = (Player) event.getTarget();
            target = (AbstractClientPlayer) targetPlayer;
            lastTargetTime = System.currentTimeMillis();
            recentTargets.put(targetPlayer, lastTargetTime);
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        if (editPosition.getValue()) {
            target = mc.player;
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
            Player mostRecent = null;
            long mostRecentTime = 0;
            for (Map.Entry<Player, Long> entry : recentTargets.entrySet()) {
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
        } else if (renderTarget != null && stick.getValue()) {
            renderStickHUD(renderTarget, animatedScale);
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

        Matrix3x2fStack pose = RenderContext.graphics().pose();
        pose.pushMatrix();
        pose.translate(x + hudWidth / 2f, y + hudHeight / 2f);
        pose.scale(scale, scale);
        pose.translate(-(x + hudWidth / 2f), -(y + hudHeight / 2f));
        try {

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

        PlayerFaceExtractor.extractRenderState(RenderContext.graphics(), target.getSkin(), x + 5, y + 5, 30, RenderContext.applyAlpha(0xFFFFFFFF));

        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        if (fr == null) return;

        String name = net.minecraft.ChatFormatting.stripFormatting(target.getName().getString());
        fr.drawStringWithShadow(name, x + 40, y + 8, (int) (alpha * 0xFF) << 24 | 0xFFFFFF);

        float health = target.getHealth();
        float maxHealth = target.getMaxHealth();
        if (animatedHealth == 0 || target == mc.player) animatedHealth = health;
        animatedHealth = interpolate(animatedHealth, health, 0.1f);
        float healthPercent = animatedHealth / maxHealth;

        int healthBarY = y + 25;
        int healthBarWidth = hudWidth - 45;

        DrawUtils.drawRoundedRect(x + 40, healthBarY, x + 40 + healthBarWidth, healthBarY + 8, 4,
                (int) (alpha * 0x40) << 24 | 0x404040);

        int healthColor = healthPercent > 0.5f ? getThemeColor() : (healthPercent > 0.25f ? 0xFFFFFF00 : 0xFFFF0000);
        DrawUtils.drawRoundedRect(x + 40, healthBarY, x + 40 + (int) (healthBarWidth * healthPercent), healthBarY + 8, 4,
                (int) (alpha * 0xFF) << 24 | healthColor);

        int armor = target.getArmorValue();
        if (animatedArmor == 0 || target == mc.player) animatedArmor = armor;
        animatedArmor = interpolate(animatedArmor, armor, 0.1f);

        int armorBarY = healthBarY + 10;
        DrawUtils.drawRoundedRect(x + 40, armorBarY, x + 40 + healthBarWidth, armorBarY + 4, 2,
                (int) (alpha * 0x40) << 24 | 0x404040);

        DrawUtils.drawRoundedRect(x + 40, armorBarY, x + 40 + (int) (healthBarWidth * Math.min(1f, animatedArmor / 20f)), armorBarY + 4, 2,
                (int) (alpha * 0xFF) << 24 | getThemeColor());

        String healthText = String.format("%.1f/%.1f", animatedHealth, maxHealth);
        fr.drawString(healthText, x + 40, y + 15, (int) (alpha * 0xFF) << 24 | 0xCCCCCC);
        } finally {
            pose.popMatrix();
        }

    }

    private void drawSimpleMode(AbstractClientPlayer target, float scale) {
        float alpha = Math.min(1f, scale);
        int x = HUD.targetHUDX;
        int y = HUD.targetHUDY;
        int hudWidth = 130;
        int hudHeight = 32;

        Matrix3x2fStack pose = RenderContext.graphics().pose();
        pose.pushMatrix();
        pose.translate(x + hudWidth / 2f, y + hudHeight / 2f);
        pose.scale(scale, scale);
        pose.translate(-(x + hudWidth / 2f), -(y + hudHeight / 2f));
        try {

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
        if (fr == null) return;

        String name = net.minecraft.ChatFormatting.stripFormatting(target.getName().getString());
        fr.drawStringWithShadow(name, x + 5, y + 5, (int) (alpha * 0xFF) << 24 | 0xFFFFFF);

        float health = target.getHealth();
        float maxHealth = target.getMaxHealth();
        if (animatedHealth == 0 || target == mc.player) animatedHealth = health;
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
        } finally {
            pose.popMatrix();
        }

    }

    private int getThemeColor() {
        return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
    }

    /**
     * "Stick" mode: the HUD floats above the target. 1.8 drew it as a billboard in the world; the
     * same effect here comes from projecting the head position onto the screen, sizing the panel by
     * distance, and drawing the normal 2D HUD there.
     */
    private void renderStickHUD(AbstractClientPlayer en, float scale) {
        Vec3 head = RenderUtils.interpolatedPosition(en).add(0, en.getBbHeight() + 0.5, 0);
        WorldToScreen.Point point = WorldToScreen.project(head);
        if (point == null)
            return;
        float distanceScale = Math.max(0.35f, Math.min(1.5f, 6f / point.depth()));
        int origX = HUD.targetHUDX;
        int origY = HUD.targetHUDY;
        int hudWidth = mode.getValue() == TargetHUDMode.Simple ? 130 : 150;
        HUD.targetHUDX = Math.round(point.x() - hudWidth / 2f);
        HUD.targetHUDY = Math.round(point.y() - 50);

        switch (mode.getValue()) {
            case Simple:
                drawSimpleMode(en, scale * distanceScale);
                break;
            default:
                drawFaceMode(en, scale * distanceScale);
                break;
        }

        HUD.targetHUDX = origX;
        HUD.targetHUDY = origY;
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

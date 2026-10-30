import arsenic.module.ModuleTier;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.gui.hud.HudElement;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.java.MathUtils;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.timer.FrameClock;
import arsenic.utils.render.RenderContext;
import net.minecraft.world.entity.LivingEntity;

/**
 * Counts your confirmed hits in a row on an animated HUD panel. A hit is confirmed when the attacked entity's hurt time
 * rises within a few ticks of the attack. The combo ends after a timeout, when you take a hit, or when you switch
 * target. Display only; it never acts.
 */
@ModuleInfo(name = "ComboCounter", description = "Counts your hits in a row on an animated panel", category = ModuleCategory.RENDER, tier = ModuleTier.QOL)
public class ComboCounter extends Module {

    public final DoubleProperty timeout = new DoubleProperty("Timeout (s)", new DoubleValue(1, 5, 2.5, 0.1));
    public final BooleanProperty resetOnHit = new BooleanProperty("Reset On Hit", true);
    public final BooleanProperty resetOnTarget = new BooleanProperty("Reset On Target Change", true);
    public final BooleanProperty sound = new BooleanProperty("Sound", false);
    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("ComboCounter", 4, 200, 130, 44);
    private final FrameClock clock = new FrameClock();

    private int combo, lastCombo, best;
    private int tickCounter, lastHitTick, attackTick;
    private int targetPrevHurt, myPrevHurt;
    private LivingEntity lastTarget;      // last attacked entity, watched to confirm the hit
    private LivingEntity comboTarget;     // target of the running combo
    private float pop, vis;                   // animations: number bounce, panel fade

    @Override
    protected void onDisable() {
        combo = 0; lastCombo = 0; lastTarget = null; comboTarget = null; pop = 0; vis = 0;
    }

    @Override
    public String getHudInfo() { return combo > 0 ? String.valueOf(combo) : null; }

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        if (!(event.getTarget() instanceof LivingEntity)) return;
        lastTarget = (LivingEntity) event.getTarget();
        attackTick = tickCounter;
        targetPrevHurt = lastTarget.hurtTime;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        tickCounter++;

        int myHurt = mc.player.hurtTime;                            // rising edge: we just got hit
        if (resetOnHit.getValue() && myHurt > myPrevHurt && combo > 0) endCombo();
        myPrevHurt = myHurt;

        if (lastTarget != null) {
            if (lastTarget.isRemoved() || tickCounter - attackTick > 15) {  // 15 ticks of slack for ping
                lastTarget = null;
            } else {
                int h = lastTarget.hurtTime;
                if (h > targetPrevHurt) registerHit(lastTarget);       // target took damage: hit confirmed
                targetPrevHurt = h;
            }
        }

        if (combo > 0 && tickCounter - lastHitTick > timeoutTicks()) endCombo();
    };

    private int timeoutTicks() { return (int) (timeout.getValue().getInput() * 20); }

    private void registerHit(LivingEntity t) {
        if (combo > 0 && comboTarget != t && resetOnTarget.getValue()) combo = 0;
        combo++;
        comboTarget = t;
        lastHitTick = tickCounter;
        if (combo > best) best = combo;
        pop = 1f;
        if (sound.getValue()) SoundUtils.hitConfirm();
    }

    private void endCombo() {
        if (combo > 0) lastCombo = combo;                              // number stays visible while the panel fades
        combo = 0;
        comboTarget = null;
    }

    // ring around the combo target, in the combo colour
    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onWorld = event -> {
        if (!render.getValue() || combo == 0 || comboTarget == null || comboTarget.isRemoved() || mc.level == null) return;
        RenderUtils.drawCircle(comboTarget, event.partialTicks, 0.8, accent(combo) & 0xFFFFFF, 0.85f);
    };

    @EventLink
    public final Listener<EventRender2D> onHud = event -> {
        float dt = clock.tick();
        pop = FrameClock.approach(pop, 0f, 9f, dt);
        vis = FrameClock.approach(vis, combo > 0 ? 1f : 0f, 8f, dt);
        if (!hud.getValue() || vis < 0.02f || mc.font == null) return;

        int shown = combo > 0 ? combo : lastCombo;
        int accent = accent(shown) & 0xFFFFFF;
        float x = panel.x, y = panel.y, w = 130, h = 44;
        panel.setSize(130, 44);

        // background and accent strip
        DrawUtils.drawRoundedRect(x, y, x + w, y + h, 6, ((int) (150 * vis) << 24) | 0x121212);
        DrawUtils.drawRoundedRect(x + 4, y + 6, x + 6, y + h - 6, 1, alpha(accent, vis));

        // big number with a bounce
        String num = String.valueOf(shown);
        float scale = 2.2f * (1f + pop * 0.35f);
        float cx = x + 30, cy = y + h / 2f;
        var pose = RenderContext.graphics().pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.scale(scale, scale);
        DrawUtils.drawStringWithShadow(num, -mc.font.width(num) / 2f, -4f, alpha(accent, vis));
        pose.popMatrix();

        // tier label and best combo
        String tier = shown >= 15 ? "UNSTOPPABLE" : shown >= 10 ? "ON FIRE" : shown >= 5 ? "NICE" : "COMBO";
        DrawUtils.drawStringWithShadow(tier, x + 56, y + 8, alpha(accent, vis));
        DrawUtils.drawStringWithShadow("BEST " + best, x + 56, y + 20, alpha(0xAAAAAA, vis));

        // timeout bar that drains
        float progress = combo > 0 ? MathUtils.clamp01(1f - (tickCounter - lastHitTick) / (float) timeoutTicks()) : 0f;
        float bx0 = x + 56, bx1 = x + w - 8, by = y + h - 11, bh = 3f;
        DrawUtils.drawRoundedRect(bx0, by, bx1, by + bh, bh / 2f, ((int) (110 * vis) << 24) | 0x000000);
        if (progress > 0.02f)
            DrawUtils.drawRoundedRect(bx0, by, bx0 + Math.max(bh, (bx1 - bx0) * progress), by + bh, bh / 2f, alpha(accent, vis));
    };

    // theme colour shifting to red as the combo grows
    private int accent(int c) {
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        return ColorUtils.mixRgb(main, 0xFF4444, MathUtils.clamp01(c / 15f)) | 0xFF000000;
    }

    private static int alpha(int rgb, float a) {
        return ColorUtils.withAlpha(rgb & 0xFFFFFF, Math.max(5, (int) (255 * a)));   // the font ignores alpha <= 3
    }
}

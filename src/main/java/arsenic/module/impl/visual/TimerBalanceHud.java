package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.gui.hud.HudElement;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.Group;
import arsenic.module.property.PropertyGroup;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.lag.PingTracker;
import arsenic.utils.lag.TimerBalance;
import arsenic.utils.render.DrawUtils;

/**
 * Shows the timer balance {@link TimerBalance} estimates Grim has for us: how far movement packets can run ahead of
 * real time, out of the most that can be banked at the current transaction ping. The numbers are sampled every Update
 * Rate so they stay readable; the bar eases toward each new sample. Drawn like TargetHUD's simple mode.
 */
@ModuleInfo(name = "TimerBalance", category = ModuleCategory.RENDER, description = "Shows how many ticks Grim's timer would let you send ahead", tier = ModuleTier.QOL)
public class TimerBalanceHud extends Module {

    @PropertyGroup(Group.ADVANCED)
    public final DoubleProperty updateRate = new DoubleProperty("Update Rate (ms)", new DoubleValue(50, 2000, 250, 50));
    @PropertyGroup(Group.ADVANCED)
    public final BooleanProperty showFlags = new BooleanProperty("Show Predicted Flags", false);

    private static final int WIDTH = 130, HEIGHT = 40, PAD = 5, RADIUS = 8, BAR_HEIGHT = 6;
    /** Fraction of the remaining distance the bar closes per 1/60 s. */
    private static final float BAR_EASE = 0.15f;

    private final HudElement hud = hudElement("Timer Balance", 4, 240, WIDTH, HEIGHT);

    // last sample
    private long sampledAt;
    private int balanceMs, maxMs, ticks, ping, flags;
    // bar fill shown, eased toward the sample
    private float shownFill;
    private long lastFrame;

    @Override
    protected void onEnable() {
        sampledAt = 0;
        lastFrame = 0;
    }

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        if (mc.thePlayer == null)
            return;
        sample();
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        if (fr == null)
            return;

        int x = hud.x, y = hud.y;
        int theme = 0xFF000000 | Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        DrawUtils.drawRoundedRect(x, y, x + WIDTH, y + HEIGHT, RADIUS, 0x801A1A1A);
        DrawUtils.drawBorderedRoundedRect(x, y, x + WIDTH, y + HEIGHT, RADIUS, 2, theme, 0x00000000);

        float row = y + 9.5f;
        fr.drawStringWithShadow("Timer", x + PAD, row, 0xFFFFFFFF, fr.CENTREY);
        String tickText = ticks + (ticks == 1 ? " tick" : " ticks");
        fr.drawStringWithShadow(tickText, x + WIDTH - PAD - fr.getWidth(tickText), row, theme, fr.CENTREY);

        int barY = y + 18, barWidth = WIDTH - PAD * 2;
        DrawUtils.drawRoundedRect(x + PAD, barY, x + PAD + barWidth, barY + BAR_HEIGHT, BAR_HEIGHT / 2f, 0x40404040);
        float fillWidth = barWidth * easeFill(maxMs <= 0 ? 0 : Math.min(1f, (float) balanceMs / maxMs));
        if (fillWidth >= 1)
            DrawUtils.drawRoundedRect(x + PAD, barY, x + PAD + Math.max(BAR_HEIGHT, fillWidth), barY + BAR_HEIGHT, BAR_HEIGHT / 2f, theme);

        float detail = y + 31.5f;
        String left = balanceMs + " / " + maxMs + " ms";
        fr.drawString(left, x + PAD, detail, 0xFFCCCCCC, fr.CENTREY);
        String right = showFlags.getValue() ? flags + " flags" : ping + " ping";
        fr.drawString(right, x + WIDTH - PAD - fr.getWidth(right), detail, 0xFFCCCCCC, fr.CENTREY);
        hud.setSize(WIDTH, HEIGHT);
    };

    /** Takes new numbers once every Update Rate. */
    private void sample() {
        long now = System.currentTimeMillis();
        if (now - sampledAt < updateRate.getValue().getInput())
            return;
        sampledAt = now;
        balanceMs = (int) Math.max(0, Math.round(TimerBalance.getBalanceMs()));
        maxMs = (int) Math.max(0, Math.round(TimerBalance.getMaxBalanceMs()));
        ticks = TimerBalance.getSpareTicks(0);
        ping = PingTracker.getPing();
        flags = TimerBalance.getPredictedFlags();
    }

    private float easeFill(float target) {
        long now = System.currentTimeMillis();
        float frames = lastFrame == 0 ? 60 : Math.min(60, (now - lastFrame) / (1000f / 60f));
        lastFrame = now;
        shownFill += (target - shownFill) * (1 - (float) Math.pow(1 - BAR_EASE, frames));
        return shownFill;
    }
}

package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventShader;
import arsenic.gui.design.M3;
import arsenic.gui.hud.HudEditorScreen;
import arsenic.gui.hud.HudElement;
import arsenic.gui.hud.HudStyle;
import arsenic.utils.render.DrawUtils;
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
import net.minecraft.client.gui.GuiChat;

/**
 * Shows the timer balance {@link TimerBalance} estimates Grim has for us: how far movement packets can run ahead of
 * real time, out of the most that can be banked at the current transaction ping. The numbers are sampled every Update
 * Rate so they stay readable; the bar eases toward each new sample.
 */
@ModuleInfo(name = "TimerBalance", category = ModuleCategory.RENDER, description = "Shows how many ticks Grim's timer would let you send ahead", tier = ModuleTier.QOL)
public class TimerBalanceHud extends Module {

    @PropertyGroup(Group.ADVANCED)
    public final DoubleProperty updateRate = new DoubleProperty("Update Rate (ms)", new DoubleValue(50, 2000, 250, 50));
    @PropertyGroup(Group.ADVANCED)
    public final BooleanProperty showFlags = new BooleanProperty("Show Predicted Flags", false);

    private static final int WIDTH = 136, HEIGHT = 42;
    private static final float PAD = 7f, BAR_HEIGHT = 4.5f;
    /** Fraction of the remaining distance the bar closes per 1/60 s. */
    private static final float BAR_EASE = 0.15f;

    private final HudElement hud = hudElement("Timer Balance", 6, 240, WIDTH, HEIGHT);

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

    private boolean shouldRender() {
        return mc.currentScreen == null
                || mc.currentScreen instanceof GuiChat
                || mc.currentScreen instanceof HudEditorScreen;
    }

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        if (mc.thePlayer == null || !shouldRender())
            return;
        sample();
        FontRendererExtension<?> bodyFr = HudStyle.body();
        FontRendererExtension<?> labelFr = HudStyle.label();
        if (bodyFr == null || labelFr == null)
            return;

        hud.setSize(WIDTH, HEIGHT);
        float x = HudStyle.snap(hud.x);
        float y = HudStyle.snap(hud.y);

        HudStyle.card(x, y, x + WIDTH, y + HEIGHT, HudStyle.RADIUS_CARD, 195);

        // Row 1: "Timer" on left, spare ticks pill chip on right
        float row1Cy = y + PAD + 5.5f;
        labelFr.drawString("Timer", x + PAD, row1Cy, M3.onSurface(), labelFr.CENTREY);

        String tickText = ticks + (ticks == 1 ? " tick" : " ticks");
        HudStyle.rightChip(labelFr, tickText, x + WIDTH - PAD, row1Cy, 11.5f, 4.5f,
                M3.primaryContainer(), M3.onPrimaryContainer());

        // Row 2: M3 linear progress bar
        float barY1 = y + 21f;
        float barY2 = barY1 + BAR_HEIGHT;
        float fillPct = easeFill(maxMs <= 0 ? 0f : Math.min(1f, (float) balanceMs / maxMs));
        HudStyle.progressBar(x + PAD, barY1, x + WIDTH - PAD, barY2,
                fillPct, 0f, M3.primary(), 0, M3.alpha(M3.surfaceContainerHighest(), 220));

        // Row 3: ms balance on left, ping / flags on right
        float row2Cy = y + HEIGHT - PAD - 3.5f;
        String left = balanceMs + " / " + maxMs + " ms";
        String right = showFlags.getValue() ? flags + " flags" : ping + " ms";
        bodyFr.setScale(0.88f);
        bodyFr.drawString(left, x + PAD, row2Cy, M3.onSurfaceVariant(), bodyFr.CENTREY);
        bodyFr.drawString(right, x + WIDTH - PAD - bodyFr.getWidth(right), row2Cy, M3.onSurfaceVariant(), bodyFr.CENTREY);
        bodyFr.resetScale();

        HudStyle.resetGl();
    };

    @EventLink
    public final Listener<EventShader.Blur> blurListener = event -> {
        if (mc.thePlayer == null || !shouldRender())
            return;
        float x = HudStyle.snap(hud.x);
        float y = HudStyle.snap(hud.y);
        DrawUtils.drawRoundedRect(x, y, x + WIDTH, y + HEIGHT, HudStyle.RADIUS_CARD, 0xFFFFFFFF);
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

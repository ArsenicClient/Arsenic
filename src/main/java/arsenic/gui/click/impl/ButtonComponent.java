package arsenic.gui.click.impl;

import arsenic.gui.click.Component;
import arsenic.gui.click.UITheme;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;

public abstract class ButtonComponent extends Component {

    private final Component parentComponent;

    protected ButtonComponent(Component parentComponent) {
        this.parentComponent = parentComponent;
    }

    private float trackX1 = Float.MAX_VALUE;

    public final float getTrackX1() { return trackX1; }

    private final AnimationTimer toggleTimer = new AnimationTimer(UITheme.DUR_TOGGLE, this::isEnabled, TickMode.CUBIC);

    protected abstract boolean isEnabled();

    protected abstract void setEnabled(boolean enabled);

    @Override
    protected float drawComponent(RenderInfo ri) {
        float pct = toggleTimer.getPercent();
        float hover = hoverPct();

        float trackHeight = height * 0.44f;
        float trackWidth = trackHeight * 1.85f;
        float trackX2 = x2;
        float trackX1 = trackX2 - trackWidth;
        this.trackX1 = trackX1;
        float trackY1 = midPointY - trackHeight / 2f;
        float trackY2 = midPointY + trackHeight / 2f;
        float radius = UITheme.radiusPill(trackHeight);

        // Material switch: primary track with a small light knob when on, outlined track with a dark knob when off
        int track = UITheme.mix(arsenic.gui.design.M3.surfaceContainerHighest(), UITheme.accent(), pct);
        DrawUtils.drawRoundedRect(trackX1, trackY1, trackX2, trackY2, radius, track);
        if (pct < 0.98f)
            DrawUtils.drawRoundedOutline(trackX1, trackY1, trackX2, trackY2, radius, 1f,
                    UITheme.alpha(arsenic.gui.design.M3.outline(), (int) (255 * (1f - pct))));

        float knobRadius = trackHeight * (0.26f + 0.12f * pct);
        float travel = trackWidth - trackHeight;
        float knobX = trackX1 + trackHeight / 2f + travel * pct;

        if (hover > 0.02f)
            DrawUtils.drawCircle(knobX, midPointY, knobRadius * 1.7f,
                    UITheme.alpha(UITheme.mix(arsenic.gui.design.M3.onSurface(), UITheme.accent(), pct), (int) (28 * hover)));

        int knob = UITheme.mix(arsenic.gui.design.M3.outline(), UITheme.readableOn(UITheme.accent()), pct);
        DrawUtils.drawCircle(knobX, midPointY, knobRadius * (1f - 0.1f * pressPct()), knob);

        return height;
    }

    @Override
    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {
        boolean turningOn = !isEnabled();
        setEnabled(turningOn);
        if (turningOn)
            SoundUtils.chordEnable();
        else
            SoundUtils.chordDisable();
        Arsenic.getArsenic().getConfigManager().saveConfig();
    }

    @Override
    protected void playClickSound() {
    }

    @Override
    public int getHeight(int i) {
        return parentComponent.getHeight(i);
    }

    @Override
    public int getWidth(int i) {
        return parentComponent.getWidth(i);
    }
}

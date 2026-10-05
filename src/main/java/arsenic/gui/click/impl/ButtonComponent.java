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

        int offTrack = ThemeManager.getButtonBackground();
        int onLeft = UITheme.mix(offTrack, UITheme.accent(), pct);
        int onRight = UITheme.mix(offTrack, UITheme.accentAlt(), pct);

        if (pct > 0.02f)
            DrawUtils.drawShadow(trackX1, trackY1, trackX2, trackY2, radius,
                    arsenic.gui.click.GuiStyle.shadowSpread(trackHeight * 0.5f),
                    arsenic.gui.click.GuiStyle.shadowAlpha((int) (110 * pct)), 5);

        DrawUtils.drawGradientRoundedRect(trackX1, trackY1, trackX2, trackY2, radius,
                onLeft, onLeft, onRight, onRight);

        DrawUtils.drawRoundedOutline(trackX1, trackY1, trackX2, trackY2, radius, 0.8f,
                UITheme.alpha(pct > 0.5f ? ThemeManager.getWhite() : ThemeManager.getBlack(),
                        (int) (30 + 40 * pct)));

        float knobRadius = trackHeight * 0.36f;
        float travel = trackWidth - (knobRadius * 2f) - trackHeight * 0.14f;
        float knobX = trackX1 + trackHeight * 0.07f + knobRadius + travel * pct;

        if (hover > 0.02f)
            DrawUtils.drawCircle(knobX, midPointY, knobRadius * (1.35f + 0.25f * hover),
                    UITheme.alpha(UITheme.accent(), (int) (55 * hover)));

        DrawUtils.drawCircle(knobX, midPointY + knobRadius * 0.12f, knobRadius * 1.05f,
                ThemeManager.getButtonCircleShadow());
        DrawUtils.drawCircle(knobX, midPointY, knobRadius * (1f - 0.08f * pressPct()),
                UITheme.mix(ThemeManager.getWhite(), 0xFFFFFFFF, pct));
        DrawUtils.drawCircle(knobX - knobRadius * 0.28f, midPointY - knobRadius * 0.28f,
                knobRadius * 0.3f, ThemeManager.getButtonCircleHighlight());

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

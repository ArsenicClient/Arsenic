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

/**
 * The toggle switch used for modules and boolean properties.
 * <p>
 * Knob position, track colour and glow all run off one {@link TickMode#CUBIC} timer, so every part
 * of the switch arrives at the same moment and it reads as a single object moving.
 */
public abstract class ButtonComponent extends Component {

    private final Component parentComponent;

    protected ButtonComponent(Component parentComponent) {
        this.parentComponent = parentComponent;
    }

    /** Left edge of the drawn track, published so a parent can lay out beside it exactly. */
    private float trackX1 = Float.MAX_VALUE;

    /** X where the switch visually begins. Parents use it as the right boundary of their content. */
    public final float getTrackX1() { return trackX1; }

    // One timer, one easing. The knob used to run on TickMode.BACK for an overshoot, but its
    // travel was then clamped to the track - so the overshoot was clipped rather than shown, and
    // all it actually did was make the knob arrive early and sit there while the colour caught up.
    // A plain ease-out moves the knob and the colour together, which is what reads as one object.
    private final AnimationTimer toggleTimer = new AnimationTimer(UITheme.DUR_TOGGLE, this::isEnabled, TickMode.CUBIC);

    protected abstract boolean isEnabled();

    protected abstract void setEnabled(boolean enabled);

    @Override
    protected float drawComponent(RenderInfo ri) {
        float pct = toggleTimer.getPercent();
        float hover = hoverPct();

        // Track: a pill two-and-a-bit knobs wide, vertically centred on the row.
        float trackHeight = height * 0.44f;
        float trackWidth = trackHeight * 1.85f;
        float trackX2 = x2;
        float trackX1 = trackX2 - trackWidth;
        this.trackX1 = trackX1;
        float trackY1 = midPointY - trackHeight / 2f;
        float trackY2 = midPointY + trackHeight / 2f;
        float radius = UITheme.radiusPill(trackHeight);

        // Off is a recessed neutral slot; on is the accent gradient. Interpolating the whole
        // gradient (not just one colour) keeps the theme's two-tone identity in the on state.
        int offTrack = ThemeManager.getButtonBackground();
        int onLeft = UITheme.mix(offTrack, UITheme.accent(), pct);
        int onRight = UITheme.mix(offTrack, UITheme.accentAlt(), pct);

        if (pct > 0.02f)
            DrawUtils.drawShadow(trackX1, trackY1, trackX2, trackY2, radius,
                    arsenic.gui.click.GuiStyle.shadowSpread(trackHeight * 0.5f),
                    arsenic.gui.click.GuiStyle.shadowAlpha((int) (110 * pct)), 5);

        DrawUtils.drawGradientRoundedRect(trackX1, trackY1, trackX2, trackY2, radius,
                onLeft, onLeft, onRight, onRight);

        // Inner rim reads as a recess when off and as a lit edge when on.
        DrawUtils.drawRoundedOutline(trackX1, trackY1, trackX2, trackY2, radius, 0.8f,
                UITheme.alpha(pct > 0.5f ? ThemeManager.getWhite() : ThemeManager.getBlack(),
                        (int) (30 + 40 * pct)));

        float knobRadius = trackHeight * 0.36f;
        float travel = trackWidth - (knobRadius * 2f) - trackHeight * 0.14f;
        float knobX = trackX1 + trackHeight * 0.07f + knobRadius + travel * pct;

        // Halo grows with hover so the control acknowledges the cursor before it is clicked.
        if (hover > 0.02f)
            DrawUtils.drawCircle(knobX, midPointY, knobRadius * (1.35f + 0.25f * hover),
                    UITheme.alpha(UITheme.accent(), (int) (55 * hover)));

        DrawUtils.drawCircle(knobX, midPointY + knobRadius * 0.12f, knobRadius * 1.05f,
                ThemeManager.getButtonCircleShadow());
        DrawUtils.drawCircle(knobX, midPointY, knobRadius * (1f - 0.08f * pressPct()),
                UITheme.mix(ThemeManager.getWhite(), 0xFFFFFFFF, pct));
        // Off-centre specular dot - the cheapest possible read of a sphere.
        DrawUtils.drawCircle(knobX - knobRadius * 0.28f, midPointY - knobRadius * 0.28f,
                knobRadius * 0.3f, ThemeManager.getButtonCircleHighlight());

        return height;
    }

    @Override
    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {
        boolean turningOn = !isEnabled();
        setEnabled(turningOn);
        // Base the chord on what the user asked for, not the resulting state:
        // some modules disable themselves inside onEnable(), which would
        // otherwise make an "enable" click play the disable chord.
        if (turningOn)
            SoundUtils.chordEnable();  // C major - bright, switching ON
        else
            SoundUtils.chordDisable(); // A minor - soft, switching OFF
        Arsenic.getArsenic().getConfigManager().saveConfig();
    }

    @Override
    protected void playClickSound() {
        // The enable/disable chord is played in clickComponent (based on
        // intent), so there's nothing to add here.
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

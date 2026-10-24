package arsenic.gui.click.impl;

import arsenic.gui.click.Component;
import arsenic.gui.click.UITheme;
import arsenic.gui.design.M3;
import arsenic.gui.design.M3Components;
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

        float trackHeight = height * 0.56f;
        float trackWidth = trackHeight * 1.75f;
        float trackX2 = x2;
        float trackX1 = trackX2 - trackWidth;
        this.trackX1 = trackX1;

        M3Components.switchControl(trackX1, y1, height, pct, hover);
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

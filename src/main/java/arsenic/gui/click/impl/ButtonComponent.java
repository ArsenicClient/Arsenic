package arsenic.gui.click.impl;

import arsenic.gui.click.Component;
import arsenic.gui.themes.ThemeManager;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;

import java.awt.*;

public abstract class ButtonComponent extends Component {

    private final Component parentComponent;

    protected ButtonComponent(Component parentComponent) {
        this.parentComponent = parentComponent;
    }

    private final AnimationTimer animationTimer = new AnimationTimer(350, this::isEnabled, TickMode.SINE);

    protected abstract boolean isEnabled();

    protected abstract void setEnabled(boolean enabled);

    @Override
    protected float drawComponent(RenderInfo ri) {
        float radius = height / 5f;
        float midPointY = (y2 - height / 2f);
        float buttonY1 = midPointY - radius;
        float buttonY2 = midPointY + radius;
        float buttonWidth = radius * 2.5f;
        float buttonX = x2 - buttonWidth;

        float percent = animationTimer.getPercent();
        Color color = new Color(RenderUtils.interpolateColoursInt(getDisabledColor(), getEnabledColor(), percent));

        int trackColor = RenderUtils.interpolateColoursInt(
                ThemeManager.getButtonBackground(),
                ColorUtils.setColor(color.getRGB(), 0, 120),
                percent
        );
        DrawUtils.drawRoundedRect(x2 - buttonWidth, buttonY1, x2, buttonY2, radius * 2, trackColor);

        float circleOffset = buttonWidth * ((percent - .5f) * 0.8f);
        float knobX = buttonX + buttonWidth / 2f + circleOffset;

        DrawUtils.drawCircle(knobX, midPointY, radius * 1.3f, ThemeManager.getButtonCircleShadow());
        DrawUtils.drawCircle(knobX, midPointY, radius * 1.2f, color.darker().darker().getRGB());
        DrawUtils.drawCircle(knobX - radius * 0.15f, midPointY - radius * 0.15f, radius * 0.35f, ThemeManager.getButtonCircleHighlight());

        return height;
    }

    @Override
    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {
        setEnabled(!isEnabled());
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

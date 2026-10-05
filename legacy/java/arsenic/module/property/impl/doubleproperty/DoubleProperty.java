package arsenic.module.property.impl.doubleproperty;

import arsenic.utils.java.MathUtils;
import arsenic.gui.click.UITheme;
import arsenic.gui.click.impl.PropertyComponent;
import arsenic.gui.themes.ThemeManager;
import arsenic.module.property.SerializableProperty;
import arsenic.module.property.impl.DisplayMode;
import arsenic.module.property.impl.SliderScale;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Mouse;

public class DoubleProperty extends SerializableProperty<DoubleValue> {

    private final DisplayMode displayMode;
    private final SliderScale scale;

    public DoubleProperty(String name, DoubleValue value) {
        this(name, value, SliderScale.LINEAR);
    }

    public DoubleProperty(String name, DoubleValue value, SliderScale scale) {
        super(name, value);
        this.displayMode = DisplayMode.NORMAL;
        this.scale = scale;
    }

    @Override
    public JsonObject saveInfoToJson(@NotNull JsonObject obj) {
        obj.add("value", new JsonPrimitive(value.getInput()));
        return obj;
    }

    @Override
    public void loadFromJson(@NotNull JsonObject obj) {
        value.setInput(obj.get("value").getAsDouble());
    }

    public final @NotNull String getValueString() {
        double v = value.getInput();
        String num = (v == Math.rint(v) && !Double.isInfinite(v))
                ? String.valueOf((long) v)
                : String.valueOf(Math.round(v * 100.0) / 100.0);
        return num + getDisplayMode().getSuffix();
    }

    public DisplayMode getDisplayMode() { return displayMode; }

    @Override
    public PropertyComponent<DoubleProperty> createComponent() {
        return new PropertyComponent<DoubleProperty>(this) {

            private boolean dragging;
            private float trackX1, trackX2, trackWidth;
            private float dragX1, dragWidth;

            private final AnimationTimer grabTimer =
                    new AnimationTimer(UITheme.DUR_HOVER, () -> dragging, TickMode.CUBIC);

            @Override
            protected float draw(RenderInfo ri) {
                float percent = scale.toPercent(getValue().getInput(), getValue().getMinBound(), getValue().getMaxBound());

                float grab = grabTimer.getPercent();

                float chipHeight = height * 0.5f;
                float chipWidth = UITheme.chip(ri.getFr(), self.getValueString(), x2, midPointY, chipHeight,
                        UITheme.mix(UITheme.textSecondary(), UITheme.accent(), Math.max(percent * 0.35f, grab)),
                        UITheme.alpha(ThemeManager.getBlack(), 70));

                trackX1 = controlX1();
                trackX2 = x2 - chipWidth - pad() * 0.7f;
                trackWidth = Math.max(1f, trackX2 - trackX1);
                float fillX = trackX1 + percent * trackWidth;

                float trackH = Math.max(3f, height * 0.3f) + grab * height * 0.04f;
                float trackY1 = midPointY - trackH / 2f, trackY2 = midPointY + trackH / 2f;
                float trackRadius = trackH / 2f;

                DrawUtils.drawRoundedRect(trackX1, trackY1, trackX2, trackY2, trackRadius,
                        UITheme.alpha(ThemeManager.getButtonBackground(), 200));
                float fillEnd = Math.max(fillX, trackX1 + trackH);
                DrawUtils.drawGradientRoundedRect(trackX1, trackY1, fillEnd, trackY2, trackRadius,
                        UITheme.accent(), UITheme.accent(), UITheme.accentAlt(), UITheme.accentAlt());

                return height;
            }

            @Override
            protected void click(int mouseX, int mouseY, int mouseButton) {
                if (mouseX < trackX1 || mouseX > trackX2)
                    return;
                dragging = true;
                dragX1 = trackX1;
                dragWidth = trackWidth;
                applyFromMouse(mouseX);
            }

            @Override
            public void mouseReleased(int mouseX, int mouseY, int state) {
                dragging = false;
            }

            @Override
            public void mouseUpdate(int mouseX, int mouseY) {
                super.mouseUpdate(mouseX, mouseY);
                if (!Mouse.isButtonDown(0))
                    dragging = false;
                if (dragging)
                    applyFromMouse(mouseX);
            }

            private void applyFromMouse(int mouseX) {
                float pct = MathUtils.clamp01((mouseX - dragX1) / dragWidth);
                double min = getValue().getMinBound(), max = getValue().getMaxBound();
                getValue().setInput(scale.fromPercent(pct, min, max));
                onValueUpdate();
                arsenic.utils.java.SoundUtils.slide(scale.toPercent(getValue().getInput(), min, max));
            }
        };
    }

}

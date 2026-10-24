package arsenic.module.property.impl.doubleproperty;

import arsenic.utils.java.MathUtils;
import arsenic.gui.click.UITheme;
import arsenic.gui.click.impl.PropertyComponent;
import arsenic.gui.design.M3;
import arsenic.gui.design.M3Components;
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

            private final AnimationTimer grabTimer =
                    new AnimationTimer(UITheme.DUR_HOVER, () -> dragging, TickMode.CUBIC);

            @Override
            protected float draw(RenderInfo ri) {
                float percent = scale.toPercent(getValue().getInput(), getValue().getMinBound(), getValue().getMaxBound());

                float grab = grabTimer.getPercent();
                float hover = hoverPct();
                float active = Math.max(grab, hover * 0.6f);

                float chipHeight = height * 0.5f;
                int chipFill = dragging
                        ? UITheme.alpha(M3.primary(), 235)
                        : UITheme.alpha(ThemeManager.getButtonBackground(), 190);
                int chipFg = dragging ? M3.onPrimary() : M3.onSurfaceVariant();
                float chipWidth = UITheme.chip(ri.getFr(), self.getValueString(), x2, midPointY, chipHeight,
                        chipFill, chipFg);

                trackX1 = controlX1();
                trackX2 = x2 - chipWidth - pad() * 0.7f;
                trackWidth = Math.max(1f, trackX2 - trackX1);

                float handleX = M3Components.sliderControl(trackX1, midPointY, trackWidth, percent, active);
                if (grab > 0.02f) {
                    float bubbleBottom = midPointY - height * 0.62f;
                    M3Components.sliderLabel(ri.getFr(), self.getValueString(), handleX, bubbleBottom - 20f);
                }

                return height;
            }

            @Override
            protected void click(int mouseX, int mouseY, int mouseButton) {
                if (mouseX < trackX1 - pad() || mouseX > trackX2 + pad())
                    return;
                dragging = true;
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
                float pct = MathUtils.clamp01((mouseX - trackX1) / trackWidth);
                double min = getValue().getMinBound(), max = getValue().getMaxBound();
                getValue().setInput(scale.fromPercent(pct, min, max));
                onValueUpdate();
                arsenic.utils.java.SoundUtils.slide(scale.toPercent(getValue().getInput(), min, max));
            }
        };
    }

}

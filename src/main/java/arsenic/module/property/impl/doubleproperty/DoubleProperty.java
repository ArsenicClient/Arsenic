package arsenic.module.property.impl.doubleproperty;

import arsenic.utils.java.MathUtils;
import arsenic.gui.click.UITheme;
import arsenic.gui.click.impl.PropertyComponent;
import arsenic.gui.design.Hover;
import arsenic.gui.design.M3Controls;
import arsenic.gui.design.M3Components;
import arsenic.module.property.SerializableProperty;
import arsenic.module.property.impl.DisplayMode;
import arsenic.module.property.impl.SliderScale;
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
        return format(value.getInput()) + getDisplayMode().getSuffix();
    }

    private static String format(double v) {
        return (v == Math.rint(v) && !Double.isInfinite(v))
                ? String.valueOf((long) v)
                : String.valueOf(Math.round(v * 100.0) / 100.0);
    }

    public DisplayMode getDisplayMode() { return displayMode; }

    @Override
    public PropertyComponent<DoubleProperty> createComponent() {
        return new PropertyComponent<DoubleProperty>(this) {

            private boolean dragging;
            private boolean handleHovered;
            private float trackX1, trackX2, trackWidth, handleX;
            private final Object handleKey = new Object();

            private final AnimationTimer grabTimer =
                    new AnimationTimer(UITheme.DUR_HOVER, () -> dragging, TickMode.CUBIC);
            private final AnimationTimer handleTimer =
                    new AnimationTimer(UITheme.DUR_HOVER, () -> handleHovered, TickMode.CUBIC);

            @Override
            protected float draw(RenderInfo ri) {
                double min = getValue().getMinBound(), max = getValue().getMaxBound();
                float percent = scale.toPercent(getValue().getInput(), min, max);

                float grab = grabTimer.getPercent();
                float chipHeight = height * 0.5f;
                String text = self.getValueString();
                String suffix = getDisplayMode().getSuffix();
                float reserve = Math.max(ri.getFr().getWidth(format(min) + suffix), ri.getFr().getWidth(format(max) + suffix));
                float chipWidth = M3Controls.valueChip(ri.getFr(), text, x2, midPointY, chipHeight, grab, reserve);

                // the track is frozen while dragging so it can't shift under the cursor
                if (!dragging) {
                    trackX1 = controlX1();
                    trackX2 = x2 - chipWidth - pad() * 0.7f;
                    trackWidth = Math.max(1f, trackX2 - trackX1);
                }
                handleX = trackX1 + trackWidth * percent;

                handleHovered = !dragging && Hover.test(handleKey, handleX - 10f, y1, handleX + 10f, y2);
                float active = Math.max(grab, handleTimer.getPercent());

                M3Components.sliderControl(trackX1, midPointY, trackWidth, percent, active);
                return height;
            }

            @Override
            protected void click(int mouseX, int mouseY, int mouseButton) {
                if (mouseButton != 0 || mouseX < trackX1 - pad() || mouseX > trackX2 + pad())
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
                if (dragging) {
                    hovered = M3Controls.dragHold(this);
                    applyFromMouse(mouseX);
                }
            }

            private void applyFromMouse(int mouseX) {
                float pct = MathUtils.clamp01((mouseX - trackX1) / trackWidth);
                double min = getValue().getMinBound(), max = getValue().getMaxBound();
                double before = getValue().getInput();
                getValue().setInput(scale.fromPercent(pct, min, max));
                if (getValue().getInput() != before) {
                    onValueUpdate();
                    arsenic.utils.java.SoundUtils.slide(scale.toPercent(getValue().getInput(), min, max));
                }
            }
        };
    }

}

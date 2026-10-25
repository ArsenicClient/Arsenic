package arsenic.module.property.impl.rangeproperty;

import arsenic.utils.java.MathUtils;
import arsenic.gui.click.UITheme;
import arsenic.gui.click.impl.PropertyComponent;
import arsenic.gui.design.Hover;
import arsenic.gui.design.M3Controls;
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

import java.util.function.BiConsumer;

public class RangeProperty extends SerializableProperty<RangeValue> {

    private final DisplayMode displayMode;
    private final SliderScale scale;

    public RangeProperty(String name, RangeValue value) {
        this(name, value, SliderScale.LINEAR);
    }

    public RangeProperty(String name, RangeValue value, SliderScale scale) {
        super(name, value);
        this.displayMode = DisplayMode.NORMAL;
        this.scale = scale;
    }

    private float toPercent(double v) {
        return scale.toPercent(v, getValue().getMinBound(), getValue().getMaxBound());
    }

    private double fromPercent(double pct) {
        return scale.fromPercent(pct, getValue().getMinBound(), getValue().getMaxBound());
    }

    @Override
    public JsonObject saveInfoToJson(JsonObject obj) {
        obj.add("min", new JsonPrimitive(value.getMin()));
        obj.add("max", new JsonPrimitive(value.getMax()));
        return obj;
    }

    @Override
    public void loadFromJson(@NotNull JsonObject obj) {
        value.setMaxSilently(obj.get("max").getAsDouble());
        value.setMinSilently(obj.get("min").getAsDouble());
    }

    public final @NotNull String getValueString() {
        return trim(value.getMin()) + " - " + trim(value.getMax()) + displayMode.getSuffix();
    }

    private static String trim(double v) {
        return (v == Math.rint(v) && !Double.isInfinite(v))
                ? String.valueOf((long) v)
                : String.valueOf(Math.round(v * 100.0) / 100.0);
    }

    public boolean hasInRange(double value) {
        return value <= getValue().getMaxBound() && value >= getValue().getMinBound();
    }

    public DisplayMode getDisplayMode() { return displayMode; }

    @Override
    public PropertyComponent<RangeProperty> createComponent() {
        return new PropertyComponent<RangeProperty>(this) {

            private boolean dragging;
            private Helping helping = Helping.MIN;
            private float trackX1, trackX2, trackWidth, minX, maxX;
            private boolean minHovered, maxHovered;
            private final Object minKey = new Object(), maxKey = new Object();

            private final AnimationTimer grabTimer =
                    new AnimationTimer(UITheme.DUR_HOVER, () -> dragging, TickMode.CUBIC);
            private final AnimationTimer minTimer = new AnimationTimer(UITheme.DUR_HOVER,
                    () -> minHovered || (dragging && helping == Helping.MIN), TickMode.CUBIC);
            private final AnimationTimer maxTimer = new AnimationTimer(UITheme.DUR_HOVER,
                    () -> maxHovered || (dragging && helping == Helping.MAX), TickMode.CUBIC);

            @Override
            protected float draw(RenderInfo ri) {
                float pctMin = toPercent(getValue().getMin());
                float pctMax = toPercent(getValue().getMax());
                float grab = grabTimer.getPercent();

                float chipHeight = height * 0.5f;
                String widest = trim(getValue().getMaxBound()) + " - " + trim(getValue().getMaxBound()) + displayMode.getSuffix();
                float reserve = Math.max(ri.getFr().getWidth(widest), ri.getFr().getWidth(
                        trim(getValue().getMinBound()) + " - " + trim(getValue().getMinBound()) + displayMode.getSuffix()));
                float chipWidth = M3Controls.valueChip(ri.getFr(), self.getValueString(), x2, midPointY,
                        chipHeight, grab, reserve);

                // the track is frozen while dragging so it can't shift under the cursor
                if (!dragging) {
                    trackX1 = controlX1();
                    trackX2 = x2 - chipWidth - pad() * 0.7f;
                    trackWidth = Math.max(1f, trackX2 - trackX1);
                }
                minX = trackX1 + pctMin * trackWidth;
                maxX = trackX1 + pctMax * trackWidth;

                // when both handles sit together the one on the cursor's side gets it
                float mouse = ri.getMouseX();
                boolean together = Math.abs(maxX - minX) < 2f;
                minHovered = !dragging && Hover.test(minKey, minX - 10f, y1, minX + 10f, y2)
                        && (!together || mouse < minX);
                maxHovered = !dragging && Hover.test(maxKey, maxX - 10f, y1, maxX + 10f, y2)
                        && (!together || mouse >= maxX);

                M3Controls.rangeSlider(trackX1, midPointY, trackWidth, pctMin, pctMax,
                        minTimer.getPercent(), maxTimer.getPercent());
                return height;
            }

            @Override
            protected void click(int mouseX, int mouseY, int mouseButton) {
                if (mouseButton != 0 || mouseX < trackX1 - pad() || mouseX > trackX2 + pad())
                    return;
                dragging = true;
                if (Math.abs(minX - maxX) < 1f) {
                    helping = mouseX >= minX ? Helping.MAX : Helping.MIN;
                } else {
                    helping = Math.abs(mouseX - minX) <= Math.abs(mouseX - maxX) ? Helping.MIN : Helping.MAX;
                }
                applyFromMouse(mouseX);
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

            @Override
            public void mouseReleased(int mouseX, int mouseY, int state) {
                dragging = false;
            }

            private void applyFromMouse(int mouseX) {
                float pct = MathUtils.clamp01((mouseX - trackX1) / trackWidth);
                double before = helping == Helping.MIN ? getValue().getMin() : getValue().getMax();
                helping.setValue(self, fromPercent(pct));
                double after = helping == Helping.MIN ? getValue().getMin() : getValue().getMax();
                if (after != before)
                    arsenic.utils.java.SoundUtils.slide(pct);
            }
        };
    }

    public enum Helping {
        MIN((rangeProperty, value) -> rangeProperty.getValue().setMin(value)),
        MAX((rangeProperty, value) -> rangeProperty.getValue().setMax(value));

        private final BiConsumer<RangeProperty, Double> v;

        Helping(BiConsumer<RangeProperty, Double> f) {
            v = f;
        }

        private void setValue(RangeProperty r, double value) {
            v.accept(r, value);
        }
    }
}

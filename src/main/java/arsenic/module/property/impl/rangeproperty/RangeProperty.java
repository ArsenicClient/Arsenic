package arsenic.module.property.impl.rangeproperty;

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
import arsenic.utils.io.Keys;

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
            private Helping helping;
            private float trackX1, trackX2, trackWidth, minX, maxX;
            private float dragX1, dragWidth;

            private final AnimationTimer grabTimer =
                    new AnimationTimer(UITheme.DUR_HOVER, () -> dragging, TickMode.CUBIC);

            @Override
            protected float draw(RenderInfo ri) {
                float pctMin = toPercent(getValue().getMin());
                float pctMax = toPercent(getValue().getMax());
                float grab = grabTimer.getPercent();

                float chipHeight = height * 0.5f;
                float chipWidth = UITheme.chip(ri.getFr(), self.getValueString(), x2, midPointY, chipHeight,
                        UITheme.mix(UITheme.textSecondary(), UITheme.accent(), grab),
                        UITheme.alpha(ThemeManager.getBlack(), 70));

                trackX1 = controlX1();
                trackX2 = x2 - chipWidth - pad() * 0.7f;
                trackWidth = Math.max(1f, trackX2 - trackX1);
                minX = trackX1 + pctMin * trackWidth;
                maxX = trackX1 + pctMax * trackWidth;

                float trackH = Math.max(3f, height * 0.3f) + grab * height * 0.04f;
                float trackY1 = midPointY - trackH / 2f, trackY2 = midPointY + trackH / 2f;
                float radius = trackH / 2f;

                DrawUtils.drawRoundedRect(trackX1, trackY1, trackX2, trackY2, radius,
                        UITheme.alpha(ThemeManager.getButtonBackground(), 200));
                float fillEnd = Math.max(maxX, minX + trackH);
                DrawUtils.drawGradientRoundedRect(minX, trackY1, fillEnd, trackY2, radius,
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
                if (!Keys.isMouseDown(0))
                    dragging = false;
                if (dragging)
                    applyFromMouse(mouseX);
            }

            @Override
            public void mouseReleased(int mouseX, int mouseY, int state) {
                dragging = false;
            }

            private void applyFromMouse(int mouseX) {
                float pct = MathUtils.clamp01((mouseX - dragX1) / dragWidth);
                helping.setValue(self, fromPercent(pct));
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

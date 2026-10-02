package arsenic.module.property.impl.rangeproperty;

import arsenic.gui.click.UITheme;
import arsenic.gui.click.impl.PropertyComponent;
import arsenic.gui.themes.ThemeManager;
import arsenic.module.property.SerializableProperty;
import arsenic.module.property.impl.DisplayMode;
import arsenic.utils.render.DrawUtils;
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

    public RangeProperty(String name, RangeValue value) {
        super(name, value);
        this.displayMode = DisplayMode.NORMAL;
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

    /** Whole numbers print without a decimal tail; a "100.0 - 200.0" chip is just wider, not clearer. */
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
                double lo = getValue().getMinBound(), hi = getValue().getMaxBound();
                float pctMin = (float) ((getValue().getMin() - lo) / (hi - lo));
                float pctMax = (float) ((getValue().getMax() - lo) / (hi - lo));
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
                if (!Mouse.isButtonDown(0))
                    dragging = false;
                if (dragging)
                    applyFromMouse(mouseX);
            }

            @Override
            public void mouseReleased(int mouseX, int mouseY, int state) {
                dragging = false;
            }

            private void applyFromMouse(int mouseX) {
                float pct = Math.max(0f, Math.min(1f, (mouseX - dragX1) / dragWidth));
                double lo = getValue().getMinBound(), hi = getValue().getMaxBound();
                helping.setValue(self, lo + (pct * (hi - lo)));
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

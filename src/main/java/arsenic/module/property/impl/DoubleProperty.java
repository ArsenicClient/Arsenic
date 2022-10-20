package arsenic.module.property.impl;

import arsenic.module.property.Property;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class DoubleProperty extends Property<Double> {

    private final Double min, max, inc;
    private final DisplayMode displayMode;

    public DoubleProperty(String name, Double value, Double min, Double max, Double inc, DisplayMode displayMode) {
        super(name, value);
        this.min = min;
        this.max = max;
        this.inc = inc;
        this.displayMode = displayMode;
    }

    public DoubleProperty(String name, Double value, Double min, Double max, Double inc) {
        this(name, value, min, max, inc, DisplayMode.NORMAL);
    }

    @Override
    protected JsonObject saveInfoToJson(JsonObject obj) {
        obj.add("value", new JsonPrimitive(value));
        return obj;
    }

    @Override
    protected void loadInfoFromJson(JsonObject obj) {
        value = obj.get("value").getAsDouble();
    }

    public final String getValueString() {
        return (value%1==0)? String.valueOf(value.intValue()) : String.valueOf(value.doubleValue()) + displayMode;
    }

    public final void setValueWithinBounds(Double value) {
        this.setValue(Math.min(Math.max(value, min), max));
    }

    public Double getMin() {
        return min;
    }

    public Double getMax() {
        return max;
    }

    public Double getInc() {
        return inc;
    }

    public DisplayMode getDisplayMode() {
        return displayMode;
    }

    public enum DisplayMode {
        NORMAL(""),
        PERCENT("%"),
        MILLIS("ms");

        private final String suffix;

        DisplayMode(String suffix) {
            this.suffix = suffix;
        }

        public String getSuffix() {
            return suffix;
        }

        @Override
        public String toString() {
            return suffix;
        }
    }

}

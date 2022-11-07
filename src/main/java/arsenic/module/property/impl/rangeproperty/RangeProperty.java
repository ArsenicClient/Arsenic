package arsenic.module.property.impl.rangeproperty;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import arsenic.module.property.Property;

public class RangeProperty extends Property<RangeValue> {

    private final DisplayMode displayMode;

    public RangeProperty(String name, double value, double minBound, double maxBound, double min, double max, double inc, DisplayMode displayMode) {
        super(name, new RangeValue(minBound, maxBound, min, max, inc));
        this.displayMode = displayMode;
    }



    @Override
    protected JsonObject saveInfoToJson(@NotNull JsonObject obj) {
        obj.add("min", new JsonPrimitive(value.getMin()));
        obj.add("max", new JsonPrimitive(value.getMax()));
        return obj;
    }

    @Override
    protected void loadInfoFromJson(@NotNull JsonObject obj) {
        value.setMax(obj.get("max").getAsDouble());
        value.setMin(obj.get("min").getAsDouble());
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

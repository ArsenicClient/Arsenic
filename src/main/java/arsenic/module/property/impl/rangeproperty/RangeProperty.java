package arsenic.module.property.impl.rangeproperty;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import arsenic.module.property.Property;
import arsenic.module.property.impl.DisplayMode;

public class RangeProperty extends Property<RangeValue> {

    private final DisplayMode displayMode;

    public RangeProperty(String name, double minBound, double maxBound, double min, double max, double inc, DisplayMode displayMode) {
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

    public final @NotNull String getValueString() {
        return value.getMin() + " -  " + value.getMax() + displayMode.getSuffix();
    }

    public DisplayMode getDisplayMode() {
        return displayMode;
    }
}

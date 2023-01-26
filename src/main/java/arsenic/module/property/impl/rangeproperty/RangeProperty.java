package arsenic.module.property.impl.rangeproperty;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import arsenic.module.property.SerializableProperty;
import arsenic.module.property.impl.DisplayMode;

public class RangeProperty extends SerializableProperty<RangeValue> {

    private final DisplayMode displayMode;

    public RangeProperty(String name, RangeValue value, DisplayMode displayMode) {
        super(name, value);
        this.displayMode = displayMode;
    }
    
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

package arsenic.module.property.impl.doubleProperty;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import arsenic.module.property.SerializableProperty;
import arsenic.module.property.impl.DisplayMode;

public class DoubleProperty extends SerializableProperty<DoubleValue> {

    private final DisplayMode displayMode;

    public DoubleProperty(String name, DoubleValue value, DisplayMode displayMode) {
        super(name, value);
        this.displayMode = displayMode;
    }

    public DoubleProperty(String name, DoubleValue value) {
        super(name, value);
        this.displayMode = DisplayMode.NORMAL;
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
        return value.getInput() + displayMode.getSuffix();
    }

    public DisplayMode getDisplayMode() {
        return displayMode;
    }

}

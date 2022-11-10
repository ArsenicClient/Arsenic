package arsenic.module.property.impl.doubleProperty;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import arsenic.module.property.Property;
import arsenic.module.property.impl.DisplayMode;

public class DoubleProperty extends Property<DoubleValue> {

    private final DisplayMode displayMode;

    public DoubleProperty(String name, Double value, Double min, Double max, Double inc, DisplayMode displayMode) {
        super(name, new DoubleValue(min, max, value, inc));
        this.displayMode = displayMode;
    }

    public DoubleProperty(String name, Double value, Double min, Double max, Double inc) {
        this(name, value, min, max, inc, DisplayMode.NORMAL);
    }

    @Override
    protected JsonObject saveInfoToJson(@NotNull JsonObject obj) {
        obj.add("value", new JsonPrimitive(value.getInput()));
        return obj;
    }

    @Override
    protected void loadInfoFromJson(@NotNull JsonObject obj) {
        value.setInput(obj.get("value").getAsDouble());
    }

    /*
    public final @NotNull String getValueString() {
        return ((value.getInput() % 1==0) ? String.valueOf((int) value.getInput()) : value.getInput()) + displayMode.getSuffix();
    } */

    public final @NotNull String getValueString() {
        return value.getInput() + displayMode.getSuffix();
    }

    public DisplayMode getDisplayMode() {
        return displayMode;
    }

}

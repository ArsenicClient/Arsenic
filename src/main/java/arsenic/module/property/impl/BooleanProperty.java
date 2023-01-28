package arsenic.module.property.impl;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;

import arsenic.module.property.SerializableProperty;

public class BooleanProperty extends SerializableProperty<Boolean> implements IReliable {

    public BooleanProperty(String name, Boolean value) {
		super(name, value);
	}

	@Override
    public JsonObject saveInfoToJson(@NotNull JsonObject obj) {
        obj.addProperty("enabled", value);
        return obj;
    }

    @Override
	public void loadFromJson(@NotNull JsonObject obj) {
        value = obj.get("enabled").getAsBoolean();
    }

    @Override
    public IVisible valueCheck(String value) {
        return () -> Boolean.parseBoolean(value) == this.value;
    }
}

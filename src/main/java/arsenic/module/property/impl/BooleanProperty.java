package arsenic.module.property.impl;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import arsenic.module.property.Property;

public class BooleanProperty extends Property<Boolean> {

    public BooleanProperty(String name, Boolean value) {
        super(name, value);
    }

    @Override
    protected JsonObject saveInfoToJson(@NotNull JsonObject obj) {
        obj.add("enabled", new JsonPrimitive(value));
        return obj;
    }

    @Override
    protected void loadInfoFromJson(@NotNull JsonObject obj) {
        value = obj.get("enabled").getAsBoolean();
    }

}

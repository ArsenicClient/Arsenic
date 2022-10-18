package dev.kino.module.property.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.kino.module.property.Property;

public class BooleanProperty extends Property<Boolean> {

    public BooleanProperty(String name, Boolean value) {
        super(name, value);
    }

    @Override
    protected JsonObject saveInfoToJson(JsonObject obj) {
        obj.add("value", new JsonPrimitive(value));
        return obj;
    }

    @Override
    protected void loadInfoFromJson(JsonObject obj) {
        value = obj.get("value").getAsBoolean();
    }

}

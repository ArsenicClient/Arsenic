package arsenic.module.property.impl;

import arsenic.module.property.Property;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;

public class BooleanProperty extends Property<Boolean> {

    public BooleanProperty(String name, Boolean value) {
        super(name, value);
    }

    @Override
    protected JsonObject saveInfoToJson(@NotNull JsonObject obj) {
        obj.add("value", new JsonPrimitive(value));
        return obj;
    }

    @Override
    protected void loadInfoFromJson(@NotNull JsonObject obj) {
        value = obj.get("value").getAsBoolean();
    }

}

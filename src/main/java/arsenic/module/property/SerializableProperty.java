package arsenic.module.property;

import com.google.gson.JsonObject;

import arsenic.utils.interfaces.ISerializable;

public abstract class SerializableProperty<T> extends Property<T> implements ISerializable {

    protected String name;

    public SerializableProperty(String name, T value) {
        super(value);
        this.name = name;
    }

    public abstract JsonObject saveInfoToJson(JsonObject obj);
    public abstract void loadFromJson(JsonObject obj);

    @Override
    public final JsonObject addToJson(JsonObject obj) {
        JsonObject config = new JsonObject();
        saveInfoToJson(config);
        obj.add(name, config);
        return obj;
    }

    @Override
    public final String getJsonKey() { return name; }

    public final String getName() { return name; }

}

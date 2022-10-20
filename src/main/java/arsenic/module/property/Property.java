package arsenic.module.property;

import arsenic.utils.interfaces.ISerializable;
import com.google.gson.JsonObject;

public abstract class Property<T> implements ISerializable {

    private final String name;
    protected T value;

    protected Property(String name, T value) {
        this.name = name;
        this.value = value;
    }

    protected abstract JsonObject saveInfoToJson(JsonObject obj);
    protected abstract void loadInfoFromJson(JsonObject obj);

    @Override
    public final void loadFromJson(JsonObject obj) {
        loadInfoFromJson(obj);
    }

    @Override
    public final JsonObject saveToJson() {
        return saveInfoToJson(new JsonObject());
    }

    @Override
    public final String getJsonKey() {
        return name;
    }

    public final String getName() {
        return name;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

}

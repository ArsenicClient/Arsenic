package arsenic.module.property.impl;

import com.google.gson.JsonObject;

import arsenic.module.property.Property;
import arsenic.module.property.SerializableProperty;

public class EnumProperty<T extends Enum<?>> extends SerializableProperty<T> {

    private T[] modes;

    @SuppressWarnings("unchecked")
    public EnumProperty(String name, T value) {
        super(name, value);
        try {this.modes = (T[]) value.getClass().getMethod("values").invoke(null);} catch (Exception e) {e.printStackTrace();}
    }

    @Override
	public JsonObject saveInfoToJson(JsonObject obj) {
        obj.addProperty("mode", value.toString());
        return obj;
    }

    @Override
	public void loadFromJson(JsonObject obj) {
        String mode = obj.get("mode").getAsString();
        for (T opt : modes)
            if (opt.toString().equals(mode))
                setValue(opt);
    }

    public void nextMode() {
        value = modes[(value.ordinal() + 1) % (modes.length + 1)];
    }

    public void prevMode() {
    	value = modes[(value.ordinal() - 1) % (modes.length + 1)];
    }

}

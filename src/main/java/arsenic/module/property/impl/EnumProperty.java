package arsenic.module.property.impl;

import com.google.gson.JsonObject;

import arsenic.module.property.Property;

public class EnumProperty<T extends Enum<?>> extends Property<T> {

    private T[] modes;

    @SuppressWarnings("unchecked")
    public EnumProperty(String name, T value) {
        super(name, value);
        try {this.modes = (T[]) value.getClass().getMethod("values").invoke(null);} catch (Exception e) {e.printStackTrace();}
    }

    @Override
    protected JsonObject saveInfoToJson(JsonObject obj) {
        obj.addProperty("mode", value.toString());
        return obj;
    }

    @Override
    protected void loadInfoFromJson(JsonObject obj) {
        String mode = obj.get("mode").getAsString();
        for (T opt : modes)
            if (opt.toString().equals(mode))
                setValue(opt);
    }

    public void nextMode() {
        value = modes[value.ordinal() == 0 ? modes.length - 1 : value.ordinal() + 1];
    }

    public void prevMode() {
        value = modes[value.ordinal() == 0 ? modes.length - 1 : value.ordinal() - 1];
    }

}

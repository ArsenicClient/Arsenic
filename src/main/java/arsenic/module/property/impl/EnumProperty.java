package arsenic.module.property.impl;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.module.property.IReliable;
import arsenic.module.property.SerializableProperty;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import com.google.gson.JsonObject;

public class EnumProperty<T extends Enum<?>> extends SerializableProperty<T> implements IReliable {

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

    @Override
    public IVisible valueCheck(String value) {
        return () -> value == this.value.name() && isVisible();
    }

    @Override
    public PropertyComponent createComponent() {
        return new PropertyComponent<EnumProperty>(this) {

            @Override
            protected int draw(RenderInfo ri) {
                RenderUtils.drawRect(x1, y1, x2,  y2, 0xFF00FF00);
                ri.getFr().drawString(getName(), x1, y1 + (height)/2, 0xFF00FFFF);
                return height;
            }
        };
    }
}

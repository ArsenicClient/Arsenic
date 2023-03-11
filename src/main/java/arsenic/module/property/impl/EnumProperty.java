package arsenic.module.property.impl;

import com.google.gson.JsonObject;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.module.property.IReliable;
import arsenic.module.property.SerializableProperty;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderInfo;

public class EnumProperty<T extends Enum<?>> extends SerializableProperty<T> implements IReliable {

    private T[] modes;

    @SuppressWarnings("unchecked")
    public EnumProperty(String name, T value) {
        super(name, value);
        try {
            this.modes = (T[]) value.getClass().getMethod("values").invoke(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        value = modes[(value.ordinal() + 1) % modes.length];
    }

    public void prevMode() {
        value = modes[(value.ordinal() == 0 ? modes.length : value.ordinal()) - 1];
    }

    @Override
    public IVisible valueCheck(String value) {
        return () -> value.equals(this.value.name()) && isVisible();
    }

    @Override
    public PropertyComponent<EnumProperty> createComponent() {
        return new PropertyComponent<EnumProperty>(this) {
            @Override
            protected int draw(RenderInfo ri) {
                float centreY = y1 + (height)/2f;
                ri.getFr().drawString(getName(), x1, centreY - (ri.getFr().getHeight(getName())/2), 0xFFFFFFFF);
                ri.getFr().drawString(getValue().name(), x2 - ri.getFr().getWidth(getValue().name()), centreY - (ri.getFr().getHeight(getName())/2), 0xFF00FFFF);
                return height;
            }

            @Override
            protected void click(int mouseX, int mouseY, int mouseButton) {
                if(mouseButton == 0)
                    nextMode();
                else if (mouseButton == 1)
                    prevMode();

            }
        };
    }
}

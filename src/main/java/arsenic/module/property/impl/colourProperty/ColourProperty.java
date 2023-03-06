package arsenic.module.property.impl.colourProperty;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.module.property.SerializableProperty;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderInfo;

public class ColourProperty extends SerializableProperty<Integer> {

    protected ColourProperty(String name, int value) {
        super(name, value);
    }

    @Override
    public JsonObject saveInfoToJson(@NotNull JsonObject obj) {
        obj.add("value", new JsonPrimitive(value));
        return obj;
    }

    @Override
    public void loadFromJson(@NotNull JsonObject obj) {
        value = (obj.get("value").getAsInt());
    }

    // think i can replace (getRed() & 0xFF) with just value >> 8 but idk
    public void setAlpha(int alpha) {
        value = ((alpha & 0xFF) << 24) | ((getRed() & 0xFF) << 16) | ((getGreen() & 0xFF) << 8)
                | ((getBlue() & 0xFF) << 0);
    }

    public void setRed(int red) {
        value = ((getAlpha() & 0xFF) << 24) | ((red & 0xFF) << 16) | ((getGreen() & 0xFF) << 8)
                | ((getBlue() & 0xFF) << 0);
    }

    public void setGreen(int green) {
        value = ((getAlpha() & 0xFF) << 24) | ((getRed() & 0xFF) << 16) | ((green & 0xFF) << 8)
                | ((getBlue() & 0xFF) << 0);
    }

    public void setBlue(int blue) {
        value = ((getAlpha() & 0xFF) << 24) | ((getRed() & 0xFF) << 16) | ((getGreen() & 0xFF) << 8)
                | ((blue & 0xFF) << 0);
    }

    public int getAlpha() { return value & 0xFF; }

    public int getRed() { return (value >> 8) & 0xFF; }

    public int getGreen() { return (value >> 16) & 0xFF; }

    public int getBlue() { return (value >> 24) & 0xFF; }

    @Override
    public PropertyComponent createComponent() {
        return new PropertyComponent<ColourProperty>(this) {
            @Override
            protected int draw(RenderInfo ri) {
                DrawUtils.drawRect(x1, y1, x2, y2, 0xFF00FF00);
                ri.getFr().drawString(getName(), x1, y1 + (height) / 2, 0xFF00FFFF);
                return height;
            }
        };
    }

}

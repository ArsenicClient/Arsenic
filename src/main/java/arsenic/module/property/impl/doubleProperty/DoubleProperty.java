package arsenic.module.property.impl.doubleProperty;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.module.property.SerializableProperty;
import arsenic.module.property.impl.DisplayMode;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;

public class DoubleProperty extends SerializableProperty<DoubleValue> {

    private final DisplayMode displayMode;

    public DoubleProperty(String name, DoubleValue value, DisplayMode displayMode) {
        super(name, value);
        this.displayMode = displayMode;
    }

    public DoubleProperty(String name, DoubleValue value) {
        super(name, value);
        this.displayMode = DisplayMode.NORMAL;
    }

    @Override
    public JsonObject saveInfoToJson(@NotNull JsonObject obj) {
        obj.add("value", new JsonPrimitive(value.getInput()));
        return obj;
    }

    @Override
    public void loadFromJson(@NotNull JsonObject obj) {
        value.setInput(obj.get("value").getAsDouble());
    }

    public final @NotNull String getValueString() { return value.getInput() + displayMode.getSuffix(); }

    public DisplayMode getDisplayMode() { return displayMode; }

    @Override
    public PropertyComponent createComponent() {
        return new PropertyComponent<DoubleProperty>(this) {
            @Override
            protected int draw(RenderInfo ri) {
                RenderUtils.drawRect(x1, y1, x2, y2, 0xFF00FF00);
                ri.getFr().drawString(getName(), x1, y1 + (height) / 2, 0xFF00FFFF);
                return height;
            }
        };
    }

}

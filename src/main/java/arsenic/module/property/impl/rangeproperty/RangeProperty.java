package arsenic.module.property.impl.rangeproperty;

import arsenic.utils.render.DrawUtils;
import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.module.property.SerializableProperty;
import arsenic.module.property.impl.DisplayMode;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;

public class RangeProperty extends SerializableProperty<RangeValue> {

    private final DisplayMode displayMode;

    public RangeProperty(String name, RangeValue value, DisplayMode displayMode) {
        super(name, value);
        this.displayMode = displayMode;
    }

    public RangeProperty(String name, RangeValue value) {
        super(name, value);
        this.displayMode = DisplayMode.NORMAL;
    }

    @Override
    public JsonObject saveInfoToJson(JsonObject obj) {
        obj.add("min", new JsonPrimitive(value.getMin()));
        obj.add("max", new JsonPrimitive(value.getMax()));
        return obj;
    }

    @Override
    public void loadFromJson(@NotNull JsonObject obj) {
        value.setMax(obj.get("max").getAsDouble());
        value.setMin(obj.get("min").getAsDouble());
    }

    public final @NotNull String getValueString() {
        return value.getMin() + " -  " + value.getMax() + displayMode.getSuffix();
    }

    public DisplayMode getDisplayMode() { return displayMode; }

    @Override
    public PropertyComponent createComponent() {
        return new PropertyComponent<RangeProperty>(this) {
            @Override
            protected int draw(RenderInfo ri) {
                DrawUtils.drawRect(x1, y1, x2, y2, 0xFF00FF00);
                ri.getFr().drawString(getName(), x1, y1 + (height) / 2, 0xFF00FFFF);
                return height;
            }
        };
    }
}

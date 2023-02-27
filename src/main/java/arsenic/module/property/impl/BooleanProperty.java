package arsenic.module.property.impl;

import arsenic.utils.render.DrawUtils;
import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.module.property.IReliable;
import arsenic.module.property.SerializableProperty;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;

public class BooleanProperty extends SerializableProperty<Boolean> implements IReliable {

    public BooleanProperty(String name, Boolean value) {
        super(name, value);
    }

    @Override
    public JsonObject saveInfoToJson(@NotNull JsonObject obj) {
        obj.addProperty("enabled", value);
        return obj;
    }

    @Override
    public void loadFromJson(@NotNull JsonObject obj) {
        value = obj.get("enabled").getAsBoolean();
    }

    @Override
    public IVisible valueCheck(String value) {
        return () -> Boolean.parseBoolean(value) == this.value && isVisible();
    }

    @Override
    public PropertyComponent createComponent() {
        return new PropertyComponent<BooleanProperty>(this) {

            private int color = self.getValue() ? 0xFF00FF00 : 0xFFFF0000;

            @Override
            protected int draw(RenderInfo ri) {
                DrawUtils.drawRect(x1, y1, x2, y2, color);
                ri.getFr().drawString(getName(), x1, y1 + (height) / 2, 0xFFFFFFFF);
                return height;
            }

            @Override
            protected void click(int mouseX, int mouseY, int mouseButton) {
                self.setValue(!self.getValue());
                color = self.getValue() ? 0xFF00FF00 : 0xFFFF0000;
            }
        };
    }
}

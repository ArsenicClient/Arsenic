package arsenic.module.property.impl;

import java.awt.Color;

import arsenic.gui.click.impl.ButtonComponent;
import arsenic.utils.functionalinterfaces.INoParamFunction;
import arsenic.utils.render.PosInfo;
import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.module.property.IReliable;
import arsenic.module.property.SerializableProperty;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;

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
    public INoParamFunction<Boolean> valueCheck(String value) {
        return () -> Boolean.parseBoolean(value) == this.value && isVisible();
    }

    @Override
    public PropertyComponent<BooleanProperty> createComponent() {
        return new PropertyComponent<BooleanProperty>(this) {
            private final AnimationTimer animationTimer = new AnimationTimer(350, () -> getValue(), TickMode.SINE);
            private final ButtonComponent buttonComponent = new ButtonComponent(this) {
                @Override
                protected boolean isEnabled() {
                    return getValue();
                }

                @Override
                protected void setEnabled(boolean enabled) {
                    setValue(enabled);
                }
            };

            @Override
            public float updateComponent(PosInfo pi, RenderInfo ri) {
                if(isVisible())
                    buttonComponent.updateComponent(pi, ri);
                return super.updateComponent(pi, ri);
            }

            @Override
            protected float draw(RenderInfo ri) {
                return height;
            }

            @Override
            protected void click(int mouseX, int mouseY, int mouseButton) {
                buttonComponent.handleClick(mouseX, mouseY, mouseButton);
            }
        };
    }
}

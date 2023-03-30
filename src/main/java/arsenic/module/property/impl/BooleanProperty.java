package arsenic.module.property.impl;

import java.awt.Color;

import arsenic.utils.functionalinterfaces.INoParamFunction;
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

            private final Color disabledColor = new Color(0xFF4B5F55);
            private final Color enabledColor = new Color(0xFF2ECC71);
            private final AnimationTimer animationTimer = new AnimationTimer(350, () -> getValue(), TickMode.SINE);

            @Override
            protected float draw(RenderInfo ri) {
                float radius = height/5f;
                float midPointY = (y2 - height/2f);
                float buttonY1 = midPointY - radius;
                float buttonY2 = midPointY + radius;
                float buttonWidth = radius * 2.5f;
                float buttonX = x2 - buttonWidth;

                float percent =  animationTimer.getPercent();
                Color color = RenderUtils.interpolateColoursColor(disabledColor, enabledColor, percent);
                int darkerColor = color.darker().darker().getRGB();
                int normalColour = color.getRGB();

                //oval
                DrawUtils.drawBorderedRoundedRect(x2 - buttonWidth, buttonY1, x2, buttonY2, radius * 2, radius/3f, normalColour, darkerColor);

                //circle
                float circleOffset = buttonWidth * ((percent - .5f) * 0.8f);
                DrawUtils.drawBorderedCircle(
                        buttonX + buttonWidth/2f + circleOffset,
                        midPointY,
                        radius * 1.1f,
                        radius/3f,
                        normalColour,
                        darkerColor
                );

                return height;
            }

            @Override
            protected void click(int mouseX, int mouseY, int mouseButton) {
                self.setValue(!self.getValue());
            }
        };
    }
}

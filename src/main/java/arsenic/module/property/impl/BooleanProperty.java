package arsenic.module.property.impl;

import java.awt.Color;

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
    public IVisible valueCheck(String value) {
        return () -> Boolean.parseBoolean(value) == this.value && isVisible();
    }

    @Override
    public PropertyComponent createComponent() {
        return new PropertyComponent<BooleanProperty>(this) {

            private Color disabledColor = new Color(0xFF4B5F55), enabledColor = new Color(0xFF2ECC71);
            private AnimationTimer animationTimer = new AnimationTimer(700, () -> getValue(), TickMode.SINE);

            @Override
            protected int draw(RenderInfo ri) {
                float buttonWidth = 24;
                float percent =  animationTimer.getPercent();
                int color = RenderUtils.interpolateColours(disabledColor, enabledColor, percent);

                //name
                ri.getFr().drawString(getName(), x1, (y1 + height/2) - (ri.getFr().getHeight(getName())/2), 0xFFFFFFFE);

                //oval
                DrawUtils.drawRoundedRect(
                        x2 - buttonWidth,
                        y1,
                        x2,
                        y2,
                        height,
                        new Color(color).darker().darker().getRGB());

                //circle
                buttonWidth = buttonWidth - 8;
                x2 = x2 - 4;
                float circleOffset = buttonWidth * percent;
                DrawUtils.drawRoundedRect(
                        x2 - buttonWidth + circleOffset - (height/2) - 0.5f,
                        y1 - 0.5f,
                        x2 - buttonWidth + circleOffset + (height/2) + 0.5f,
                        y2 + 0.5f,
                        height + 1f,
                        color);

                return height;
            }

            @Override
            protected void click(int mouseX, int mouseY, int mouseButton) {
                self.setValue(!self.getValue());
            }
        };
    }
}

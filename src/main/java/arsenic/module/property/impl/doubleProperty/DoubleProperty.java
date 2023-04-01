package arsenic.module.property.impl.doubleproperty;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.module.property.SerializableProperty;
import arsenic.module.property.impl.DisplayMode;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class DoubleProperty extends SerializableProperty<DoubleValue> {

    private final DisplayMode displayMode;

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

    public final @NotNull String getValueString() { return value.getInput() + getDisplayMode().getSuffix(); }

    public DisplayMode getDisplayMode() { return displayMode; }

    @Override
    public PropertyComponent<DoubleProperty> createComponent() {
        return new PropertyComponent<DoubleProperty>(this) {

            private float lineWidth;
            private float lineX1;
            private boolean clicked;

            @Override
            protected float draw(RenderInfo ri) {

                float percent = (float) (getValue().getInput() / getValue().getMaxBound());

                //draws name
                ri.getFr().drawString(getName(), x1, y1 + (height/2f) - (ri.getFr().getHeight(self.getName())/2), 0xFFFFFFFE);

                //draws value
                ri.getFr().drawString(
                        self.getValueString(),
                        x2 - ri.getFr().getWidth(self.getValueString()) + 4,
                        (y1 + height/2f) - (ri.getFr().getHeight(self.getValueString())/2),
                        0xFFFFFFFE);

                //draws lines
                lineX1 = x2 - width/2f;
                float lineX2 = x2 - width / 5f;
                lineWidth = lineX2 - lineX1;
                float lineXChangePoint = (lineX1 + (percent * lineWidth));
                float lineY = y1 + height / 2f;


                //draws first bit (colored) of line
                DrawUtils.drawRect(lineX1, lineY - 0.5f, lineXChangePoint, lineY + 0.5f, enabledColor.getRGB());

                //draws second bit (uncolored) of the line
                DrawUtils.drawRect(lineXChangePoint, lineY - 0.5f, lineX2, lineY + 0.5f, disabledColor.getRGB());

                //draws the circle
                float radius = height/5f;
                Color color = new Color(RenderUtils.interpolateColours(disabledColor, enabledColor, percent));
                DrawUtils.drawBorderedCircle(lineXChangePoint, lineY, radius, radius/3f, color.darker().getRGB(), color.getRGB());
                return height;
            }

            @Override
            protected void click(int mouseX, int mouseY, int mouseButton) {
                clicked = true;
            }

            @Override
            public void mouseReleased(int mouseX, int mouseY, int state) {
                clicked = false;
            }

            @Override
            public void mouseUpdate(int mouseX, int mouseY) {
                if(!clicked)
                    return;
                float mousePercent = (mouseX - lineX1) / lineWidth;
                getValue().setInput(getValue().getMinBound() + (mousePercent * (getValue().getMaxBound() - getValue().getMinBound())));
            }
        };
    }

}

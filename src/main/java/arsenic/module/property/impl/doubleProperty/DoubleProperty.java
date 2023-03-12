package arsenic.module.property.impl.doubleProperty;

import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.module.property.SerializableProperty;
import arsenic.module.property.impl.DisplayMode;
import arsenic.utils.render.RenderInfo;

import java.awt.*;

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

            private final Color disabledColor = new Color(0xFF4B5F55);
            private final Color enabledColor = new Color(0xFF2ECC71);
            private final int radius = 5;
            private float lineXChangePoint, lineY, lineWidth, lineX1, lineX2;
            private boolean clicked;

            @Override
            protected int draw(RenderInfo ri) {

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
                lineX2 = x2 - width/5f;
                lineWidth = lineX2 - lineX1;
                lineXChangePoint = (lineX1 + (percent * lineWidth));
                lineY = y1 + height/2f;


                //draws first bit (uncolored) of line
                DrawUtils.drawRect(lineX1, lineY - 0.5d, lineXChangePoint, lineY + 0.5d, disabledColor.getRGB());

                //draws second bit (colored) of the line
                DrawUtils.drawRect(lineXChangePoint, lineY - 0.5d, lineX2, lineY + 0.5d, enabledColor.getRGB());

                //draws the circle
                DrawUtils.drawCircle(lineXChangePoint, lineY, radius, RenderUtils.interpolateColours(disabledColor, enabledColor, percent));
                return height;
            }

            @Override
            protected void click(int mouseX, int mouseY, int mouseButton) {
                if(mouseX > lineX1 && mouseX < lineX2 && mouseY > lineY - radius && mouseY < lineY + radius)
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



            @Override
            protected int getHeight(int i) {
                return 7 * (i / 100);
            }
        };
    }

}

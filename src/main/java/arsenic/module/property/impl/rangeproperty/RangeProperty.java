package arsenic.module.property.impl.rangeproperty;

import arsenic.utils.render.RenderUtils;
import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.module.property.SerializableProperty;
import arsenic.module.property.impl.DisplayMode;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderInfo;

import java.awt.*;

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

            private final Color disabledColor = new Color(0xFF4B5F55);
            private final Color enabledColor = new Color(0xFF2ECC71);

            private final int radius = 5;

            private float lineXChangePoint1, lineXChangePoint2, lineY, lineWidth, lineX1, lineX2;

            @Override
            protected int draw(RenderInfo ri) {

                float percentMax = (float) (getValue().getMax() / getValue().getMaxBound());
                float percentMin = (float) (getValue().getMin() / getValue().getMaxBound());
                String name = getName() + getDisplayMode();

                //draws name
                ri.getFr().drawString(name, x1, y1 + (height/2f) - (ri.getFr().getHeight(name)/2), 0xFFFFFFFE);

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
                lineXChangePoint1 = (lineX1 + (percentMin * lineWidth));
                lineXChangePoint2 = (lineX1 + (percentMax * lineWidth));
                lineY = y1 + height/2f;


                //draws first bit (uncolored) of line
                DrawUtils.drawRect(lineX1, lineY - 0.5d, lineXChangePoint1, lineY + 0.5d, disabledColor.getRGB());

                //draws third bit (uncolored) of the line
                DrawUtils.drawRect(lineXChangePoint2, lineY - 0.5d, lineX2, lineY + 0.5d, disabledColor.getRGB());

                //draws second bit (colored) of the line
                DrawUtils.drawRect(lineXChangePoint1, lineY - 0.5d, lineXChangePoint2, lineY + 0.5d,enabledColor.getRGB());

                //draws the circles
                DrawUtils.drawCircle(lineXChangePoint1, lineY, radius, RenderUtils.interpolateColours(disabledColor, enabledColor, percentMin));
                DrawUtils.drawCircle(lineXChangePoint2, lineY, radius, RenderUtils.interpolateColours(disabledColor, enabledColor, percentMax));
                return height;
            }
        };
    }
}

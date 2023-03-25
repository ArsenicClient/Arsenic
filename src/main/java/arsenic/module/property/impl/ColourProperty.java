package arsenic.module.property.impl;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.module.property.SerializableProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderInfo;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;

public class ColourProperty extends SerializableProperty<Integer> {

    public ColourProperty(String name, int value) {
        super(name, value);
    }

    @Override
    public JsonObject saveInfoToJson(@NotNull JsonObject obj) {
        obj.add("value", new JsonPrimitive(value));
        return obj;
    }

    @Override
    public void loadFromJson(@NotNull JsonObject obj) {
        //value = (obj.get("value").getAsInt());
    }

    public void setColor(int i, int newValue) {
        value = ColorUtils.setColor(value, i, newValue);
    }

    public int getColor(int i) {
        return ColorUtils.getColor(value, i);
    }

    @Override
    public PropertyComponent<ColourProperty> createComponent() {
        return new PropertyComponent<ColourProperty>(this) {
            private final int thickness = 5;
            private boolean clicked;
            private int helping;

            private float lineX1, lineX2, lineY, lineWidth;

            @Override
            protected int draw(RenderInfo ri) {
                String name = getName();

                //draws name
                ri.getFr().drawString(name, x1, y1 + (height/2f) - ((ri.getFr().getHeight(name)/2)), 0xFFFFFFFE);

                lineX1 = x2 - width/2f;
                lineX2 = x2;
                lineWidth = lineX2 - lineX1;
                lineY = y1 + height/2f;

                //draws line
                DrawUtils.drawRect(lineX1, lineY - 0.5f, lineX2, lineY + 0.5f, value);


                //draws the colored bars
                for(int i = 0; i < 4; i++) {
                    float pointX = (lineX1 + ((getColor(i)/ 255f) * lineWidth));
                    int colorInner;
                    int colorBorder;
                    if(i == 0) { // for a
                        colorInner = ColorUtils.setColor(0x60000000, i, (getColor(i)/2) + 127);
                        colorBorder = 0xFFFFFFFF;
                    } else { //for rgb
                        colorInner = ColorUtils.setColor(0x60000000, i, (getColor(i)/2) + 127);
                        colorBorder = ColorUtils.setColor(0xFF000000, i, 255);
                    }
                    //DrawUtils.drawBorderedRoundedRect(pointX - thickness/3f, lineY + thickness, pointX + thickness/3f, lineY - thickness, thickness/10f, thickness/6f, colorBorder, colorInner);
                    DrawUtils.drawCircle(pointX, lineY, thickness/2f, colorBorder);
                }

                return height;
            }


            @Override
            protected void click(int mouseX, int mouseY, int mouseButton) {
                if(mouseX > lineX1 && mouseX < lineX2) {
                    clicked = true;
                    float closestDist = 1;
                    float mousePercent = (mouseX - lineX1) / lineWidth;
                    //probs a better way to do this
                    for(int i = 0; i < 4; i++) {
                        float dist = Math.abs(mousePercent - (getColor(i) / 255f));
                        if(dist > closestDist)
                            continue;
                        closestDist = dist;
                        helping = i;
                    }
                }
            }


            @Override
            public void mouseUpdate(int mouseX, int mouseY) {
                if(!clicked)
                    return;
                float mousePercent = (mouseX - lineX1) / lineWidth;
                System.out.println(helping + " " + mousePercent * 255);
                setColor(helping, (int) (mousePercent * 255));
                System.out.println(getColor(helping));
            }



            @Override
            public void mouseReleased(int mouseX, int mouseY, int state) {
                clicked = false;
            }
        };
    }

}

package arsenic.module.property.impl;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.module.property.Property;
import arsenic.module.property.SerializableProperty;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.interfaces.ISetNotAlwaysClickable;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.ScissorUtils;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class FolderProperty extends SerializableProperty<List<Property<?>>> {

    // does not save the config of properties inside the folder
    // properties inside the folder cannot use @PropertyInfo
    // very unfinished just here to remind me that at some point i should make it
    // Cant access the properties inside the folder -> probably remake how it inits

    //this will cause an issue with its name
    public FolderProperty(String name, Property<?>... values) {
        super(name, Arrays.asList(values));
    }


    @Override
    public PropertyComponent<FolderProperty> createComponent() {
        return new FolderComponent(this);
    }

    @Override
    public void loadFromJson(JsonObject obj) {
        getValue().forEach(p -> {
            if (p instanceof SerializableProperty) ((SerializableProperty<?>) p).loadFromJson(obj);
        });
    }

    @Override
    public JsonObject saveInfoToJson(JsonObject obj) {
        getValue().forEach(p -> {
            if (p instanceof SerializableProperty) ((SerializableProperty<?>) p).saveInfoToJson(obj);
        });
        return obj;
    }

    private class FolderComponent extends PropertyComponent<FolderProperty> implements IContainer<PropertyComponent<?>> {
        private boolean open;
        private final List<PropertyComponent<?>> components;

        private float lastHeight;
        private final AnimationTimer animationTimer = new AnimationTimer(350, () -> open, TickMode.SINE);

        private FolderComponent(FolderProperty p) {
            super(p);
            components = getValue().stream().map(Property::createComponent).collect(Collectors.toList());
        }

        @Override
        protected float draw(RenderInfo ri) {
            float borderWidth = height/15f;
            float expand = width/10f;


            PosInfo pi = new PosInfo(x1, y2);
            if(animationTimer.getPercent() > 0) {
                pi.moveX(expand);
                ScissorUtils.subScissor((int) x1, (int) y2, (int) (x2 + expandX * 2), (int) (y2 + expandY), 2);
                components.forEach(component -> pi.moveY(component.updateComponent(pi, ri)));
                ScissorUtils.endSubScissor();
                if(open) lastHeight = (pi.getY() - y2);
            }

            expandX = expand;
            expandY = animationTimer.getPercent() * lastHeight;

            DrawUtils.drawRoundedOutline(
                    x1,
                    y1,
                    x2 + (expandX * 2f),
                    y2 + expandY,
                    height/2f,
                    borderWidth,
                    enabledColor.getRGB()
            );

            float triangleLength = (height - (borderWidth * 2f));
            DrawUtils.drawTriangle(
                    x2 - height - (borderWidth * 2),
                    y1 + (borderWidth * 2) + ((height - (borderWidth * 4)) * animationTimer.getPercent()),
                    triangleLength,
                    (-(animationTimer.getPercent() - .5f) * 2) * triangleLength,
                    enabledColor.getRGB()
            );

            return height + expandY;
        }

        @Override
        protected void click(int mouseX, int mouseY, int mouseButton) {
            open = !open;
            if(!open) {
                getContents().forEach(component ->  {
                    if(component instanceof ISetNotAlwaysClickable)
                        ((ISetNotAlwaysClickable) component).setNotAlwaysClickable();
                });
            }
        }

        @Override
        public Collection<PropertyComponent<?>> getContents() {
            return components;
        }
    }
}

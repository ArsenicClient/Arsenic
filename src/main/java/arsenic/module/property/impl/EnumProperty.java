package arsenic.module.property.impl;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.main.Arsenic;
import arsenic.module.property.IReliable;
import arsenic.module.property.SerializableProperty;
import arsenic.utils.functionalinterfaces.INoParamFunction;
import arsenic.utils.functionalinterfaces.IVoidFunction;
import arsenic.utils.interfaces.ISetNotAlwaysClickable;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import com.google.gson.JsonObject;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class EnumProperty<T extends Enum<?>> extends SerializableProperty<T> implements IReliable {

    private T[] modes;

    @SuppressWarnings("unchecked")
    public EnumProperty(String name, T value) {
        super(name, value);
        try {
            this.modes = (T[]) value.getClass().getMethod("values").invoke(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public JsonObject saveInfoToJson(JsonObject obj) {
        obj.addProperty("mode", value.toString());
        return obj;
    }

    @Override
    public void loadFromJson(JsonObject obj) {
        String mode = obj.get("mode").getAsString();
        for (T opt : modes)
            if (opt.toString().equals(mode))
                setValue(opt);
    }

    public void nextMode() {
        value = modes[(value.ordinal() + 1) % modes.length];
    }

    public void prevMode() {
        value = modes[(value.ordinal() == 0 ? modes.length : value.ordinal()) - 1];
    }

    @Override
    public INoParamFunction<Boolean> valueCheck(String value) {
        return () -> value.equals(this.value.name()) && isVisible();
    }

    @Override
    public PropertyComponent<EnumProperty<?>> createComponent() {
        return new EnumComponent(this);
    }

    private class EnumComponent extends PropertyComponent<EnumProperty<?>> implements ISetNotAlwaysClickable {
        private boolean open;
        private final AnimationTimer animationTimer = new AnimationTimer(350, () -> open, TickMode.SINE);
        private float boxY1;
        private float boxX1;
        private float boxHeight;
        public EnumComponent(EnumProperty<?> p) {
            super(p);
        }

        @Override
        protected float draw(RenderInfo ri) {
            boxX1 = x2 - width/3f;
            float borderWidth = height/15f;
            boxY1 = midPointY - height/3f;
            float boxY2 = midPointY + height/3f;
            boxHeight = boxY2 - boxY1;
            float maxBoxHeight = animationTimer.getPercent() * ((modes.length)  * boxHeight);

            IVoidFunction render = () -> {

                //box
                DrawUtils.drawBorderedRoundedRect(
                        boxX1,
                        boxY1,
                        x2,
                        boxY2 + maxBoxHeight,
                        boxHeight / 2f,
                        borderWidth,
                        enabledColor.getRGB(),
                        disabledColor.getRGB()
                );

                //Other value that aren't selected
                if (animationTimer.getPercent() > 0) {
                    DrawUtils.drawRect(boxX1, boxY2, x2, boxY2 + 1, enabledColor.getRGB());

                    GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
                    RenderUtils.glScissor((int) boxX1, (int) boxY2, (int) (x2 - boxX1), (int) maxBoxHeight, 2);

                    for (int i = 0; i < modes.length; i++) {
                        T m = modes[i];
                        ri.getFr().drawYCenteredString(m.name(), boxX1 + (borderWidth * 2), midPointY + ((i + 1) * boxHeight), 0xFFFFFFFE);
                    }

                    GL11.glPopAttrib();
                }

                //name in box
                ri.getFr().drawYCenteredString(getValue().name(), boxX1 + (borderWidth * 2), midPointY, 0xFFFFFFFE);

                //triangle in box
                float triangleLength = (boxHeight - (borderWidth * 2f));
                DrawUtils.drawTriangle(
                        x2 - boxHeight - (borderWidth * 2),
                        boxY1 + (borderWidth * 2) + ((boxHeight - (borderWidth * 4)) * animationTimer.getPercent()),
                        triangleLength,
                        (-(animationTimer.getPercent() - .5f) * 2) * triangleLength,
                        enabledColor.getRGB()
                );

            };
            //so that it draws over the other properties
            if(animationTimer.getPercent() > 0) {
                Arsenic.getArsenic().getClickGuiScreen().addToRenderLastList(render);
            } else {
                render.voidFunction();
            }

            return height;
        }

        @Override
        protected void click(int mouseX, int mouseY, int mouseButton) {
            open = !open;
            Arsenic.getArsenic().getClickGuiScreen().setAlwaysClickedComponent(open ? this : null);
        }

        @Override
        public boolean clickFirstClickable(int mouseX, int mouseY, int mouseButton) {
            if(mouseX > x2 || mouseX < boxX1)
                return false;
            float mouseOffset = mouseY - boxY1;
            if(mouseOffset < 0 || mouseOffset > ((modes.length + 1) * boxHeight))
                return false;
            int box = (int) (mouseOffset/boxHeight);
            if(box == 0) {
                click(mouseX, mouseY, mouseButton);
                return true;
            }
            setValue(modes[box - 1]);
            open = !open;
            Arsenic.getArsenic().getClickGuiScreen().setAlwaysClickedComponent(null);
            return true;
        }

        @Override
        public void setNotAlwaysClickable() {
            open = false;
        }
    }
}
package arsenic.module.property.impl;

import arsenic.gui.click.ClickGuiScreen;
import arsenic.main.Arsenic;
import arsenic.module.ModuleManager;
import arsenic.module.impl.visual.ClickGui;
import arsenic.utils.interfaces.ISetNotAlwaysClickable;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import com.google.gson.JsonObject;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.module.property.IReliable;
import arsenic.module.property.SerializableProperty;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderInfo;
import net.minecraft.client.gui.GuiScreen;
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
    public IVisible valueCheck(String value) {
        return () -> value.equals(this.value.name()) && isVisible();
    }

    @Override
    public PropertyComponent<EnumProperty<?>> createComponent() {
        return new EnumComponent(this);
    }

    private class EnumComponent extends PropertyComponent<EnumProperty<?>> implements ISetNotAlwaysClickable {
        private boolean open;
        private final AnimationTimer animationTimer = new AnimationTimer(350, () -> open, TickMode.SINE);

        private final Color disabledColor = new Color(0x604B5F55);
        private final Color enabledColor = new Color(0xFF2ECC71);

        public EnumComponent(EnumProperty<?> p) {
            super(p);
        }

        @Override
        protected int draw(RenderInfo ri) {
            float boxX1 = x2 - width/3f;
            float midPointY = y1 + (height/2f);
            float borderWidth = height/15f;

            //name
            ri.getFr().drawYCenteredString(name, x1, midPointY, 0xFFFFFFFE);

            float boxY1 = midPointY - height/3f;
            float boxY2 = midPointY + height/3f;
            float boxHeight = boxY2 - boxY1;
            float maxBoxHeight = animationTimer.getPercent() * ((modes.length - 1)  * boxHeight);

            //box
            DrawUtils.drawBorderedRoundedRect(
                    boxX1,
                    boxY1,
                    x2,
                    boxY2 + maxBoxHeight,
                    boxHeight/2f,
                    borderWidth,
                    enabledColor.getRGB(),
                    disabledColor.getRGB()
            );

            //Other value that aren't selected
            RenderUtils.glScissor((int) boxX1, (int) boxY1, (int) (x2 - boxX1), (int) (boxHeight + maxBoxHeight), 2);
            if(open) {
                GuiScreen uis = ((ClickGui)(ModuleManager.Modules.CLICKGUI.getModule())).getScreen();
                DrawUtils.drawRect(0,0, uis.width, uis.height, 0xFFFFFFFE);
            }

            for(int i = 0; i < modes.length; i++) {
                T m = modes[i];
                if(m == getValue()) continue;
                ri.getFr().drawYCenteredString(m.name(), boxX1 + (borderWidth * 2), midPointY + (i * boxHeight), 0xFFFFFFFE);
            }

            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            //triangle in box
            float triangleLength = (boxHeight - (borderWidth * 2f));
            drawCustom(
                    x2 - boxHeight - (borderWidth * 2),
                    boxY1 + (borderWidth * 2) + ((boxHeight  - (borderWidth * 4)) * animationTimer.getPercent()),
                    triangleLength,
                    (-(animationTimer.getPercent() - .5f) * 2) * triangleLength,
                    enabledColor.getRGB()
            );

            //name in box
            ri.getFr().drawYCenteredString(getValue().name(), boxX1 + (borderWidth * 2), midPointY, 0xFFFFFFFE);

            return height;
        }

        @Override
        protected void click(int mouseX, int mouseY, int mouseButton) {
            open = !open;

            //aa
        }

        @Override
        public void setNotAlwaysClickable() {
            open = false;
        }

        //draws a perfect triangle
        public void drawCustom(float x1, float y1, float width, float height, int colour) {
            final float realY1 = y1 * 2;
            final float realX1 = x1 * 2;
            final float realWidth = width * 2;
            final float realHeight = (float) ((height)*Math.sqrt(3));
            DrawUtils.drawCustom(colour, () -> {
                if(realHeight > 0) {
                    GL11.glVertex2d(realX1, realY1);
                    GL11.glVertex2d(realX1 + (realWidth/2f), realY1 + realHeight);
                    GL11.glVertex2d(realX1 + realWidth, realY1);
                } else {
                    GL11.glVertex2d(realX1 + (realWidth/2f), realY1 + realHeight);
                    GL11.glVertex2d(realX1, realY1);
                    GL11.glVertex2d(realX1 + realWidth, realY1);
                }
            });
        }
    }
}
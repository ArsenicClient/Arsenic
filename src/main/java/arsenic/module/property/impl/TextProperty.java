package arsenic.module.property.impl;

import arsenic.utils.timer.MSTimer;
import arsenic.utils.timer.TimeUtils;
import arsenic.gui.click.UITheme;
import arsenic.gui.click.impl.PropertyComponent;
import arsenic.main.Arsenic;
import arsenic.module.property.SerializableProperty;
import arsenic.utils.interfaces.IAlwaysKeyboardInput;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderInfo;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import arsenic.utils.io.Keys;
import com.mojang.blaze3d.platform.InputConstants;

public class TextProperty extends SerializableProperty<String> {

    private final int maxLength;

    public TextProperty(String name, String value) {
        this(name, value, 32);
    }

    public TextProperty(String name, String value, int maxLength) {
        super(name, value);
        this.maxLength = maxLength;
        this.value = clean(value);
    }

    private String clean(String s) {
        if (s == null)
            return "";
        return s.length() > maxLength ? s.substring(0, maxLength) : s;
    }

    @Override
    public void setValue(String value) {
        super.setValue(clean(value));
    }

    @Override
    public void setValueSilently(String value) {
        super.setValueSilently(clean(value));
    }

    @Override
    public JsonObject saveInfoToJson(@NotNull JsonObject obj) {
        obj.addProperty("value", value);
        return obj;
    }

    @Override
    public void loadFromJson(@NotNull JsonObject obj) {
        if (obj.has("value"))
            setValueSilently(obj.get("value").getAsString());
    }

    @Override
    public PropertyComponent<TextProperty> createComponent() {
        return new TextComponent(this);
    }

    private static final class TextComponent extends PropertyComponent<TextProperty> implements IAlwaysKeyboardInput {

        private boolean focused, selectAll;
        private float boxX1, boxY1, boxY2;
        private final MSTimer lastDraw = MSTimer.expired();

        TextComponent(TextProperty p) {
            super(p);
        }

        @Override
        protected float draw(RenderInfo ri) {
            lastDraw.reset();

            float bh = height * 0.7f;
            boxX1 = controlX1();
            boxY1 = midPointY - bh / 2f;
            boxY2 = midPointY + bh / 2f;
            float radius = UITheme.radiusChip(bh);
            float pad = bh * 0.35f;

            if (focused && Keys.isMouseDown(0) && !insideBox(ri.getMouseX(), ri.getMouseY()))
                blur();

            UITheme.surface(boxX1, boxY1, x2, boxY2, radius,
                    UITheme.alpha(0x000000, focused ? 120 : 80), UITheme.Elevation.FLAT, 0.4f);
            DrawUtils.drawRoundedOutline(boxX1, boxY1, x2, boxY2, radius, 1f,
                    focused ? UITheme.alpha(UITheme.accent(), 230)
                            : UITheme.alpha(UITheme.accent(), (int) (60 + 90 * hoverPct())));

            String text = self.getValue();
            float room = x2 - boxX1 - pad * 2f - 2f;
            String shown = text;
            while (shown.length() > 0 && ri.getFr().getWidth(shown) > room)
                shown = shown.substring(1);

            float textX = boxX1 + pad;
            if (focused && selectAll && !shown.isEmpty())
                DrawUtils.drawRoundedRect(textX - 1, boxY1 + bh * 0.16f, textX + ri.getFr().getWidth(shown) + 1,
                        boxY2 - bh * 0.16f, 1.5f, UITheme.alpha(UITheme.accent(), 110));

            ri.getFr().drawString(shown, textX, midPointY,
                    focused ? UITheme.textPrimary() : UITheme.textSecondary(), ri.getFr().CENTREY);

            if (focused && !selectAll && TimeUtils.blink(500)) {
                float cx = textX + ri.getFr().getWidth(shown) + 1;
                DrawUtils.drawRect(cx, boxY1 + bh * 0.2f, cx + 1, boxY2 - bh * 0.2f, UITheme.textPrimary());
            }
            return height;
        }

        private boolean insideBox(int mx, int my) {
            return mx >= boxX1 && mx <= x2 && my >= boxY1 && my <= boxY2;
        }

        @Override
        protected void click(int mouseX, int mouseY, int mouseButton) {
            if (mouseButton != 0 || !insideBox(mouseX, mouseY))
                return;
            focused = true;
            selectAll = false;
            Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(this);
        }

        private void blur() {
            if (!focused)
                return;
            Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(null);
            focused = false;
            selectAll = false;
            Arsenic.getArsenic().getConfigManager().saveConfig();
        }

        @Override
        public void setNotAlwaysRecieveInput() {
            focused = false;
            selectAll = false;
        }

        @Override
        public boolean recieveInput(int key) {
            if (lastDraw.getTime() > 300) {
                blur();
                return false;
            }
            if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                SoundUtils.chordKeybind();
                blur();
                return true;
            }

            String text = self.getValue();
            if (key == InputConstants.KEY_BACKSPACE) {
                if (selectAll)
                    self.setValue("");
                else if (!text.isEmpty())
                    self.setValue(text.substring(0, text.length() - 1));
                selectAll = false;
                return false;
            }

            // control, or command on a Mac (SDL scancodes 227 and 231 are the left and right GUI keys)
            boolean ctrl = Keys.isKeyDown(InputConstants.KEY_LCONTROL) || Keys.isKeyDown(InputConstants.KEY_RCONTROL)
                    || Keys.isKeyDown(227) || Keys.isKeyDown(231);
            if (key == InputConstants.KEY_A && ctrl) {
                selectAll = true;
                return false;
            }
            return false;
        }

        @Override
        public boolean recieveChar(char c) {
            if (!StringUtil.isAllowedChatCharacter(c))
                return false;
            self.setValue((selectAll ? "" : self.getValue()) + c);
            selectAll = false;
            return true;
        }
    }
}

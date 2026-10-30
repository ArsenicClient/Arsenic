package arsenic.module.property.impl;

import arsenic.utils.timer.MSTimer;
import arsenic.utils.timer.TimeUtils;
import arsenic.gui.click.UITheme;
import arsenic.gui.design.M3;
import arsenic.gui.design.M3Components;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import arsenic.gui.click.impl.PropertyComponent;
import arsenic.main.Arsenic;
import arsenic.module.property.SerializableProperty;
import arsenic.utils.interfaces.IAlwaysKeyboardInput;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderInfo;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatAllowedCharacters;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

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
        private final AnimationTimer focusTimer = new AnimationTimer(UITheme.DUR_HOVER, () -> focused, TickMode.CUBIC);

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

            if (focused && Mouse.isButtonDown(0) && !insideBox(ri.getMouseX(), ri.getMouseY()))
                blur();

            float focus = focusTimer.getPercent();
            float textX = M3Components.textFieldBg(boxX1, boxY1, x2, boxY2, hoverPct(), focus);
            float room = x2 - textX - 8f;

            String text = self.getValue();
            String shown = text;
            while (shown.length() > 0 && ri.getFr().getWidth(shown) > room)
                shown = shown.substring(1);

            if (focused && selectAll && !shown.isEmpty())
                DrawUtils.drawRoundedRect(textX - 1, boxY1 + bh * 0.16f, textX + ri.getFr().getWidth(shown) + 1,
                        boxY2 - bh * 0.16f - 1f, 1.5f, M3.alpha(M3.primary(), 90));

            ri.getFr().drawString(shown, textX, midPointY - 0.5f,
                    focused ? M3.onSurface() : M3.onSurfaceVariant(), ri.getFr().CENTREY);

            if (focused && !selectAll && TimeUtils.blink(500)) {
                float cx = textX + ri.getFr().getWidth(shown) + 1;
                DrawUtils.drawRect(cx, boxY1 + bh * 0.22f, cx + 1, boxY2 - bh * 0.22f - 1f, M3.primary());
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
            if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                SoundUtils.chordKeybind();
                blur();
                return true;
            }

            String text = self.getValue();
            if (key == Keyboard.KEY_BACK) {
                if (selectAll)
                    self.setValue("");
                else if (!text.isEmpty())
                    self.setValue(text.substring(0, text.length() - 1));
                selectAll = false;
                return false;
            }

            boolean ctrl = Minecraft.isRunningOnMac
                    ? Keyboard.isKeyDown(219) || Keyboard.isKeyDown(220)
                    : Keyboard.isKeyDown(29) || Keyboard.isKeyDown(157);
            if (key == Keyboard.KEY_A && ctrl) {
                selectAll = true;
                return false;
            }

            if (key == Keyboard.KEY_V && ctrl) {
                String clip = net.minecraft.client.gui.GuiScreen.getClipboardString();
                StringBuilder sb = new StringBuilder(selectAll ? "" : text);
                for (char ch : clip.toCharArray())
                    if (ChatAllowedCharacters.isAllowedCharacter(ch))
                        sb.append(ch);
                self.setValue(sb.toString());
                selectAll = false;
                return false;
            }
            if (key == Keyboard.KEY_C && ctrl) {
                net.minecraft.client.gui.GuiScreen.setClipboardString(text);
                return false;
            }

            char c = Keyboard.getEventCharacter();
            if (ChatAllowedCharacters.isAllowedCharacter(c)) {
                self.setValue((selectAll ? "" : text) + c);
                selectAll = false;
            }
            return false;
        }
    }
}

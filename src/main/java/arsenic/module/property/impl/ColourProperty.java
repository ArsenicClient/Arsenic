package arsenic.module.property.impl;

import arsenic.gui.click.UITheme;
import arsenic.gui.click.impl.PropertyComponent;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.property.SerializableProperty;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import arsenic.utils.io.Keys;

public class ColourProperty extends SerializableProperty<Integer> {

    private cMode mode = cMode.CUSTOM;

    public ColourProperty(String name, int value) {
        super(name, value);
    }

    @Override
    public JsonObject saveInfoToJson(@NotNull JsonObject obj) {
        obj.addProperty("mode", mode.name());
        obj.addProperty("value", value);
        return obj;
    }

    @Override
    public void loadFromJson(@NotNull JsonObject obj) {
        setMode(cMode.valueOf(obj.get("mode").getAsString()));
        setValueSilently(obj.get("value").getAsInt());
    }

    public void setMode(cMode mode) {
        if (mode == null)
            return;
        this.mode = mode;
    }

    public void setColor(int i, int newValue) {
        value = ColorUtils.setColor(value, i, newValue);
    }

    @Override
    public Integer getValue() {
        if (mode == cMode.THEME) {
            return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        }
        return super.getValue();
    }

    public int getColor(int i) {
        return ColorUtils.getColor(value, i);
    }

    /**
     * The stored colour, ignoring theme-follow mode. The channel sliders must edit the user's own
     * value even while the swatch is displaying the theme colour, or switching back off THEME would
     * silently discard what they set.
     */
    private int rawValue() { return value; }

    /** Channel order used by {@link ColorUtils}: 0 = alpha, 1 = red, 2 = green, 3 = blue. */
    private static final String[] CHANNEL_NAMES = {"A", "R", "G", "B"};

    /**
     * Colour picker: a swatch that opens four labelled channel sliders.
     * <p>
     * The old control crammed four draggable dots onto one shared line, which meant the dots
     * overlapped whenever two channels held similar values and there was no way to tell which one
     * you had grabbed. Each channel now owns its own row and its own track, tinted to show what
     * that channel actually does to the colour, with the numeric value on the right.
     * <p>
     * Right-click still flips between a custom colour and following the active theme.
     */
    @Override
    public PropertyComponent<ColourProperty> createComponent() {
        return new PropertyComponent<ColourProperty>(this) {

            private boolean open;
            private int dragging = -1;
            private float trackX1, trackX2, trackWidth, rowHeight, swatchX1;
            // Always four channel rows, so the distance never varies and a fixed duration is
            // already a fixed speed.
            private final AnimationTimer openTimer = new AnimationTimer(UITheme.DUR_EXPAND, () -> open, TickMode.CUBIC);

            @Override
            protected float draw(RenderInfo ri) {
                float openPct = openTimer.getPercent();
                float hover = hoverPct();

                float swatchHeight = height * 0.55f;
                float swatchWidth = swatchHeight * 1.9f;
                swatchX1 = x2 - swatchWidth;
                float swatchY1 = midPointY - swatchHeight / 2f;
                float swatchY2 = midPointY + swatchHeight / 2f;
                float radius = UITheme.radiusChip(swatchHeight);

                if (mode == cMode.THEME) {
                    ri.getFr().drawString(
                            Arsenic.getArsenic().getThemeManager().getCurrentTheme().getName(),
                            swatchX1 - pad(), midPointY, UITheme.textMuted(),
                            ri.getFr().getScaleModifier(0.8f), ri.getFr().LEFTSHIFTX, ri.getFr().CENTREY);
                }

                // Checkerboard behind the swatch so a low-alpha colour reads as transparent
                // rather than as a darker shade of itself.
                checker(swatchX1, swatchY1, x2, swatchY2, swatchHeight / 3f);
                DrawUtils.drawRoundedRect(swatchX1, swatchY1, x2, swatchY2, radius, getValue());
                DrawUtils.drawRoundedOutline(swatchX1, swatchY1, x2, swatchY2, radius, 1f,
                        UITheme.alpha(ThemeManager.getWhite(), (int) (60 + 90 * Math.max(hover, openPct))));

                float total = height;
                if (openPct > 0.01f && mode == cMode.CUSTOM) {
                    rowHeight = height * 0.62f;
                    trackX1 = x1 + pad() * 2f;
                    trackX2 = x2 - height * 1.2f;
                    trackWidth = Math.max(1f, trackX2 - trackX1);

                    for (int i = 0; i < 4; i++) {
                        float rowMid = y2 + rowHeight * (i + 0.5f);
                        drawChannel(ri, i, rowMid, openPct);
                    }
                    total += rowHeight * 4 * openPct;
                }
                expandY = total - height;
                return total;
            }

            private void drawChannel(RenderInfo ri, int channel, float rowMid, float openPct) {
                int raw = ColorUtils.getColor(rawValue(), channel);
                float pct = raw / 255f;

                ri.getFr().drawString(CHANNEL_NAMES[channel], x1 + pad() * 0.6f, rowMid,
                        UITheme.fade(UITheme.textMuted(), openPct),
                        ri.getFr().getScaleModifier(0.8f), ri.getFr().CENTREY);

                float trackH = Math.max(2f, rowHeight * 0.22f);
                float ty1 = rowMid - trackH / 2f, ty2 = rowMid + trackH / 2f;
                float r = trackH / 2f;

                DrawUtils.drawRoundedRect(trackX1, ty1, trackX2, ty2, r,
                        UITheme.fade(UITheme.alpha(ThemeManager.getButtonBackground(), 200), openPct));

                // Fill tinted with this channel at full strength - the bar shows its own meaning.
                int tint = channel == 0
                        ? ThemeManager.getWhite()
                        : ColorUtils.setColor(0xFF000000, channel, 255);
                float fillX = trackX1 + pct * trackWidth;
                DrawUtils.drawRoundedRect(trackX1, ty1, fillX, ty2, r, UITheme.fade(tint, openPct));

                float knobR = rowHeight * 0.2f;
                DrawUtils.drawCircle(fillX, rowMid, knobR, UITheme.fade(ThemeManager.getWhite(), openPct));
                DrawUtils.drawCircle(fillX, rowMid, knobR * 0.45f, UITheme.fade(tint, openPct));

                ri.getFr().drawString(String.valueOf(raw), x2, rowMid,
                        UITheme.fade(UITheme.textMuted(), openPct),
                        ri.getFr().getScaleModifier(0.8f), ri.getFr().LEFTSHIFTX, ri.getFr().CENTREY);
            }

            /** Two-tone grid used as the transparency backdrop for the swatch. */
            private void checker(float cx1, float cy1, float cx2, float cy2, float cell) {
                DrawUtils.drawRoundedRect(cx1, cy1, cx2, cy2, UITheme.radiusChip(cy2 - cy1), 0xFF9A9A9A);
                boolean dark = false;
                for (float yy = cy1; yy < cy2; yy += cell) {
                    boolean d = dark;
                    for (float xx = cx1; xx < cx2; xx += cell) {
                        if (d)
                            DrawUtils.drawRect(xx, yy, Math.min(xx + cell, cx2), Math.min(yy + cell, cy2), 0xFF5E5E5E);
                        d = !d;
                    }
                    dark = !dark;
                }
            }

            @Override
            public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
                // The expanded channel rows live below this component's own rect, and the base
                // class only forwards into that overflow for containers. This control keeps its
                // rows as plain geometry, so it claims the whole expanded area itself.
                if (self.isVisible() && mouseX >= x1 && mouseX <= x2
                        && mouseY >= y1 && mouseY <= y2 + expandY) {
                    click(mouseX, mouseY, mouseButton);
                    playClickSound();
                    return true;
                }
                return super.handleClick(mouseX, mouseY, mouseButton);
            }

            @Override
            protected void click(int mouseX, int mouseY, int mouseButton) {
                if (mouseButton == 1) {
                    setMode(cMode.values()[(mode.ordinal() + 1) % cMode.values().length]);
                    return;
                }
                if (mode != cMode.CUSTOM)
                    return;

                // Clicking the swatch expands or collapses the channel rows.
                if (mouseY <= y2 && mouseX >= swatchX1) {
                    open = !open;
                    return;
                }
                if (!open || mouseY <= y2)
                    return;

                int row = (int) ((mouseY - y2) / rowHeight);
                if (row < 0 || row > 3)
                    return;
                dragging = row;
                applyFromMouse(mouseX);
            }

            @Override
            public void mouseUpdate(int mouseX, int mouseY) {
                super.mouseUpdate(mouseX, mouseY);
                if (!Keys.isMouseDown(0))
                    dragging = -1;
                if (dragging >= 0)
                    applyFromMouse(mouseX);
            }

            private void applyFromMouse(int mouseX) {
                float pct = Math.max(0f, Math.min(1f, (mouseX - trackX1) / trackWidth));
                setColor(dragging, (int) (pct * 255));
                // ringing tone tracks the channel value: C (min) up to C (max)
                arsenic.utils.java.SoundUtils.slide(pct);
            }

            @Override
            public void mouseReleased(int mouseX, int mouseY, int state) {
                dragging = -1;
            }
        };
    }

    public enum cMode {
        CUSTOM,
        THEME,
    }
}

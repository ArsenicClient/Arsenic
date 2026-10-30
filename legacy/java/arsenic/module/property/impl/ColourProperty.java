package arsenic.module.property.impl;

import arsenic.utils.java.MathUtils;
import arsenic.gui.click.UITheme;
import arsenic.gui.click.impl.PropertyComponent;
import arsenic.gui.design.Hover;
import arsenic.gui.design.M3;
import arsenic.gui.design.M3Components;
import arsenic.gui.design.M3Controls;
import arsenic.utils.render.ScissorUtils;
import java.awt.Color;
import arsenic.main.Arsenic;
import arsenic.module.property.SerializableProperty;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Mouse;

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
        try { setMode(cMode.valueOf(obj.get("mode").getAsString())); } catch (RuntimeException ignored) { }
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

    private int rawValue() { return value; }

    private static final String[] CHANNEL_NAMES = {"A", "R", "G", "B"};

    @Override
    public PropertyComponent<ColourProperty> createComponent() {
        return new PropertyComponent<ColourProperty>(this) {

            private static final int NONE = 0, SV = 1, HUE = 2, ALPHA = 3;

            private boolean open;
            private int dragging = NONE;
            private float hue, sat = 1f, bri = 1f;
            private int lastRgb = -1;
            private float swatchX1;
            private float svX1, svY1, svX2, svY2, hueY1, hueY2, alphaY1, alphaY2;
            private float chipY1, chipY2, customX1, customX2, themeX1, themeX2;
            private float fullHeight;
            private final Object svKey = new Object(), hueKey = new Object(), alphaKey = new Object();
            private final Object customKey = new Object(), themeKey = new Object();
            private final AnimationTimer openTimer = new AnimationTimer(UITheme.DUR_EXPAND, () -> open, TickMode.CUBIC);
            private final AnimationTimer customTimer =
                    new AnimationTimer(UITheme.DUR_HOVER, () -> mode == cMode.CUSTOM, TickMode.CUBIC);

            /** Keeps the hue when the colour is grey or black, and follows outside changes (config load). */
            private void sync() {
                int rgb = rawValue() & 0xFFFFFF;
                if (rgb == lastRgb)
                    return;
                lastRgb = rgb;
                float[] hsb = Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
                if (hsb[1] > 0.001f && hsb[2] > 0.001f)
                    hue = hsb[0];
                sat = hsb[1];
                bri = hsb[2];
            }

            @Override
            protected float labelRight() {
                return swatchX1 > 0 ? swatchX1 - pad() * 3f : super.labelRight();
            }

            @Override
            protected float draw(RenderInfo ri) {
                sync();
                float openPct = openTimer.getPercent();
                float hover = hoverPct();

                float swatchHeight = height * 0.55f;
                float swatchWidth = swatchHeight * 1.9f;
                swatchX1 = x2 - swatchWidth;
                float swatchY1 = midPointY - swatchHeight / 2f;
                float swatchY2 = midPointY + swatchHeight / 2f;
                float radius = swatchHeight / 2f;

                if (mode == cMode.THEME) {
                    ri.getFr().drawString(
                            Arsenic.getArsenic().getThemeManager().getCurrentTheme().getName(),
                            swatchX1 - pad(), midPointY, M3.onSurfaceVariant(),
                            ri.getFr().getScaleModifier(0.8f), ri.getFr().LEFTSHIFTX, ri.getFr().CENTREY);
                }

                M3Controls.checker(swatchX1, swatchY1, x2, swatchY2, radius, swatchHeight / 3f);
                DrawUtils.drawRoundedRect(swatchX1, swatchY1, x2, swatchY2, radius, getValue());
                DrawUtils.drawRoundedOutline(swatchX1, swatchY1, x2, swatchY2, radius, 1f,
                        M3.mix(M3.alpha(M3.outline(), 150), M3.primary(), Math.max(hover, openPct)));

                layout();
                float panel = fullHeight * openPct;
                expandY = panel;
                if (openPct > 0.01f) {
                    ScissorUtils.subScissor((int) (x1 - 6), (int) y2, (int) (x2 + 6), (int) (y2 + panel + 1), 2);
                    Hover.pushClip(x1 - 6, y2, x2 + 6, y2 + panel);
                    drawPanel(ri, openPct);
                    Hover.popClip();
                    ScissorUtils.endSubScissor();
                }
                return height + panel;
            }

            private void layout() {
                float gap = pad() * 0.5f;
                float chipH = height * 0.52f;
                chipY1 = y2 + gap;
                chipY2 = chipY1 + chipH;
                float cursor = chipY2 + gap;
                if (mode == cMode.CUSTOM) {
                    svX1 = x1;
                    svX2 = x2;
                    svY1 = cursor;
                    svY2 = svY1 + height * 2.1f;
                    float bar = height * 0.3f;
                    hueY1 = svY2 + gap * 1.4f;
                    hueY2 = hueY1 + bar;
                    alphaY1 = hueY2 + gap * 1.4f;
                    alphaY2 = alphaY1 + bar;
                    cursor = alphaY2 + gap;
                }
                fullHeight = cursor - y2;
            }

            private void drawPanel(RenderInfo ri, float openPct) {
                float chipH = chipY2 - chipY1;
                float cy = (chipY1 + chipY2) / 2f;
                float customW = ri.getFr().getWidth("Custom") + chipH * 1.1f + 3f;
                float themeW = ri.getFr().getWidth("Theme") + chipH * 1.1f + 3f;
                customX1 = x1;
                customX2 = customX1 + customW;
                themeX1 = customX2 + pad() * 0.5f;
                themeX2 = themeX1 + themeW;
                float custom = customTimer.getPercent();
                M3Components.chipSelected(ri.getFr(), "Custom", customX1, cy, chipH, custom,
                        Hover.test(customKey, customX1, chipY1, customX2, chipY2) ? 1f : 0f);
                M3Components.chipSelected(ri.getFr(), "Theme", themeX1, cy, chipH, 1f - custom,
                        Hover.test(themeKey, themeX1, chipY1, themeX2, chipY2) ? 1f : 0f);

                if (mode != cMode.CUSTOM)
                    return;

                int raw = rawValue();
                String hex = String.format("#%06X  %d%%", raw & 0xFFFFFF, Math.round((raw >>> 24) / 2.55f));
                ri.getFr().drawString(hex, x2, cy, M3.onSurfaceVariant(), ri.getFr().getScaleModifier(0.85f),
                        ri.getFr().LEFTSHIFTX, ri.getFr().CENTREY);

                int hueColour = Color.HSBtoRGB(hue, 1f, 1f) | 0xFF000000;
                int rgbSolid = (Color.HSBtoRGB(hue, sat, bri) & 0xFFFFFF) | 0xFF000000;

                // saturation / brightness field
                float r = 6f;
                DrawUtils.drawGradientRoundedRect(svX1, svY1, svX2, svY2, r, 0xFFFFFFFF, 0xFFFFFFFF, hueColour, hueColour);
                DrawUtils.drawGradientRoundedRect(svX1, svY1, svX2, svY2, r, 0xFF000000, 0x00000000, 0xFF000000, 0x00000000);
                DrawUtils.drawRoundedOutline(svX1, svY1, svX2, svY2, r, 1f, M3.alpha(M3.outlineVariant(), 150));
                boolean svOver = dragging == SV || Hover.test(svKey, svX1, svY1, svX2, svY2);
                knob(svX1 + sat * (svX2 - svX1), svY1 + (1f - bri) * (svY2 - svY1), rgbSolid, svOver);

                // hue bar
                float hr = (hueY2 - hueY1) / 2f;
                float seg = (svX2 - svX1) / 6f;
                for (int i = 0; i < 6; i++) {
                    int a = Color.HSBtoRGB(i / 6f, 1f, 1f) | 0xFF000000;
                    int b = Color.HSBtoRGB((i + 1) / 6f, 1f, 1f) | 0xFF000000;
                    float sx1 = svX1 + seg * i, sx2 = svX1 + seg * (i + 1);
                    DrawUtils.drawGradientRoundedRect(sx1, hueY1, sx2 + (i < 5 ? 0.5f : 0f), hueY2, 0f, a, a, b, b);
                }
                knob(svX1 + hue * (svX2 - svX1), (hueY1 + hueY2) / 2f, hueColour,
                        dragging == HUE || Hover.test(hueKey, svX1, hueY1, svX2, hueY2));

                // alpha bar
                M3Controls.checker(svX1, alphaY1, svX2, alphaY2, hr, (alphaY2 - alphaY1) / 2f);
                int clear = rgbSolid & 0xFFFFFF;
                DrawUtils.drawGradientRoundedRect(svX1, alphaY1, svX2, alphaY2, hr, clear, clear, rgbSolid, rgbSolid);
                knob(svX1 + (raw >>> 24) / 255f * (svX2 - svX1), (alphaY1 + alphaY2) / 2f,
                        M3.alpha(rgbSolid, raw >>> 24),
                        dragging == ALPHA || Hover.test(alphaKey, svX1, alphaY1, svX2, alphaY2));
            }

            private void knob(float cx, float cy, int fill, boolean big) {
                float rr = big ? 7.5f : 6f;
                DrawUtils.drawCircle(cx, cy, rr + 1f, 0x66000000);
                DrawUtils.drawCircle(cx, cy, rr, 0xFFFFFFFF);
                DrawUtils.drawCircle(cx, cy, rr - 2f, fill);
            }

            @Override
            public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
                if (self.isVisible() && MathUtils.inside(mouseX, mouseY, x1, y1, x2, y2 + expandY)) {
                    click(mouseX, mouseY, mouseButton);
                    return true;
                }
                return super.handleClick(mouseX, mouseY, mouseButton);
            }

            @Override
            protected void click(int mouseX, int mouseY, int mouseButton) {
                if (mouseY <= y2) {
                    if (mouseButton == 1) {
                        setMode(cMode.values()[(mode.ordinal() + 1) % cMode.values().length]);
                    } else if (mouseButton == 0) {
                        open = !open;
                        arsenic.utils.java.SoundUtils.chordOpen();
                    }
                    return;
                }
                if (!open || mouseButton != 0)
                    return;
                arsenic.utils.java.SoundUtils.cmajStep();
                if (MathUtils.inside(mouseX, mouseY, customX1, chipY1, customX2, chipY2)) {
                    setMode(cMode.CUSTOM);
                } else if (MathUtils.inside(mouseX, mouseY, themeX1, chipY1, themeX2, chipY2)) {
                    setMode(cMode.THEME);
                } else if (mode == cMode.CUSTOM) {
                    if (MathUtils.inside(mouseX, mouseY, svX1, svY1, svX2, svY2))
                        dragging = SV;
                    else if (MathUtils.inside(mouseX, mouseY, svX1, hueY1 - 2f, svX2, hueY2 + 2f))
                        dragging = HUE;
                    else if (MathUtils.inside(mouseX, mouseY, svX1, alphaY1 - 2f, svX2, alphaY2 + 2f))
                        dragging = ALPHA;
                    if (dragging != NONE)
                        applyFromMouse(mouseX, mouseY);
                }
            }

            @Override
            protected void playClickSound() {
            }

            @Override
            public void mouseUpdate(int mouseX, int mouseY) {
                super.mouseUpdate(mouseX, mouseY);
                if (!Mouse.isButtonDown(0))
                    dragging = NONE;
                if (dragging != NONE) {
                    hovered = M3Controls.dragHold(this);
                    applyFromMouse(mouseX, mouseY);
                }
            }

            private void applyFromMouse(int mouseX, int mouseY) {
                float w = Math.max(1f, svX2 - svX1);
                float px = MathUtils.clamp01((mouseX - svX1) / w);
                int alpha = rawValue() >>> 24;
                if (dragging == SV) {
                    sat = px;
                    bri = 1f - MathUtils.clamp01((mouseY - svY1) / Math.max(1f, svY2 - svY1));
                } else if (dragging == HUE) {
                    hue = Math.min(px, 0.999f);
                } else if (dragging == ALPHA) {
                    alpha = Math.round(px * 255f);
                } else {
                    return;
                }
                int rgb = Color.HSBtoRGB(hue, sat, bri) & 0xFFFFFF;
                int next = (alpha << 24) | rgb;
                if (next != rawValue()) {
                    lastRgb = rgb;
                    ColourProperty.this.setValue(next);
                    arsenic.utils.java.SoundUtils.slide(px);
                }
            }

            @Override
            public void mouseReleased(int mouseX, int mouseY, int state) {
                dragging = NONE;
            }
        };
    }

    public enum cMode {
        CUSTOM,
        THEME,
    }
}

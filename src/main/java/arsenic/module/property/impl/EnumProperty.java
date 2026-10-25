package arsenic.module.property.impl;

import arsenic.gui.click.UITheme;
import arsenic.gui.click.impl.PropertyComponent;
import arsenic.gui.design.Hover;
import arsenic.gui.design.M3;
import arsenic.gui.design.M3Controls;
import arsenic.utils.java.SoundUtils;
import arsenic.main.Arsenic;
import arsenic.module.property.IReliable;
import arsenic.module.property.SerializableProperty;
import arsenic.utils.interfaces.IAlwaysClickable;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.render.ScissorUtils;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import com.google.gson.JsonObject;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
                setValueSilently(opt);
    }

    public void nextMode() {
        value = modes[(value.ordinal() + 1) % modes.length];
    }

    public void prevMode() {
        value = modes[(value.ordinal() == 0 ? modes.length : value.ordinal()) - 1];
    }

    public boolean setByName(String name) {
        for (T opt : modes) {
            if (opt.name().equalsIgnoreCase(name)) {
                setValue(opt);
                return true;
            }
        }
        return false;
    }

    public List<String> getModeNames() {
        return Arrays.stream(modes).map(Enum::name).collect(Collectors.toList());
    }

    @Override
    public Supplier<Boolean> valueCheck(String value) {
        return () -> value.equals(this.value.name()) && isVisible();
    }

    @Override
    public String currentValue() {
        return value.name();
    }

    @Override
    public List<String> possibleValues() {
        return getModeNames();
    }

    @Override
    public PropertyComponent<EnumProperty<?>> createComponent() {
        return new EnumComponent(this);
    }

    private class EnumComponent extends PropertyComponent<EnumProperty<?>> implements IAlwaysClickable {

        private boolean open;
        private final AnimationTimer openTimer = new AnimationTimer(UITheme.DUR_EXPAND, () -> open, TickMode.CUBIC);

        private float fieldX1, fieldY1, fieldY2, fieldHeight, rowHeight, menuY1, menuY2;
        private final Object menuKey = new Object();
        private final Object[] optionKeys;

        public EnumComponent(EnumProperty<?> p) {
            super(p);
            optionKeys = new Object[modes.length];
            for (int i = 0; i < optionKeys.length; i++)
                optionKeys[i] = new Object();
        }

        @Override
        protected float draw(RenderInfo ri) {
            openTimer.setMaxMs(UITheme.expandDuration(modes.length * height * 0.62f));
            float openPct = openTimer.getPercent();
            float hover = hoverPct();

            fieldHeight = height * 0.62f;
            rowHeight = fieldHeight;
            fieldX1 = controlX1();
            fieldY1 = midPointY - fieldHeight / 2f;
            fieldY2 = midPointY + fieldHeight / 2f;
            float radius = UITheme.radiusChip(fieldHeight);
            float pad = fieldHeight * 0.38f;
            float menuHeight = openPct * (modes.length * rowHeight + pad);
            menuY1 = fieldY2 + pad * 0.35f;
            menuY2 = menuY1 + menuHeight;
            final boolean showMenu = openPct > 0.01f;

            Runnable render = () -> {
                M3Controls.field(fieldX1, fieldY1, x2, fieldY2, radius, hover, openPct);

                String current = fit(ri, getValue().name(), x2 - fieldX1 - pad * 3f - fieldHeight * 0.3f);
                ri.getFr().drawString(current, fieldX1 + pad, midPointY,
                        M3.mix(M3.onSurfaceVariant(), M3.onSurface(), Math.max(hover, openPct)),
                        ri.getFr().CENTREY);

                UITheme.chevron(x2 - pad, midPointY, fieldHeight * 0.3f, Math.max(1f, fieldHeight * 0.075f),
                        M3.alpha(M3.onSurfaceVariant(), (int) (170 + 85 * Math.max(hover, openPct))),
                        openPct);

                if (showMenu) {
                    // the menu covers the rows below it, so it takes the cursor from them
                    Hover.test(menuKey, fieldX1, menuY1, x2, menuY2);
                    DrawUtils.drawRoundedRect(fieldX1, menuY1, x2, menuY2, radius,
                            M3.alpha(M3.surfaceContainerHighest(), (int) (255 * Math.min(1f, openPct * 1.5f))));
                    DrawUtils.drawRoundedOutline(fieldX1, menuY1, x2, menuY2, radius, 1f,
                            M3.alpha(M3.outline(), (int) (150 * openPct)));

                    ScissorUtils.subScissor((int) fieldX1, (int) menuY1, (int) x2, (int) menuY2, 2);
                    for (int i = 0; i < modes.length; i++) {
                        T m = modes[i];
                        float rowY1 = menuY1 + pad * 0.5f + i * rowHeight;
                        float rowMid = rowY1 + rowHeight / 2f;
                        boolean selected = m == getValue();
                        boolean over = open && Hover.test(optionKeys[i], fieldX1, rowY1, x2, rowY1 + rowHeight);

                        float ix1 = fieldX1 + pad * 0.35f, ix2 = x2 - pad * 0.35f;
                        if (selected)
                            DrawUtils.drawRoundedRect(ix1, rowY1, ix2, rowY1 + rowHeight, radius * 0.8f,
                                    M3.alpha(M3.secondaryContainer(), (int) (230 * openPct)));
                        if (over)
                            DrawUtils.drawRoundedRect(ix1, rowY1, ix2, rowY1 + rowHeight, radius * 0.8f,
                                    M3.alpha(M3.onSurface(), (int) (22 * openPct)));

                        ri.getFr().drawString(fit(ri, m.name(), ix2 - fieldX1 - pad * 2.4f), fieldX1 + pad, rowMid,
                                UITheme.fade(selected ? M3.onSecondaryContainer() : M3.onSurface(), openPct),
                                ri.getFr().CENTREY);

                        if (selected)
                            UITheme.check(x2 - pad, rowMid, rowHeight * 0.34f,
                                    Math.max(1f, rowHeight * 0.09f),
                                    M3.alpha(M3.onSecondaryContainer(), (int) (255 * openPct)));
                    }
                    ScissorUtils.endSubScissor();
                }
            };

            if (showMenu)
                Arsenic.getArsenic().getClickGuiScreen().addToRenderLastList(render);
            else
                render.run();

            return height;
        }

        @Override
        protected void click(int mouseX, int mouseY, int mouseButton) {
            if (mouseButton != 0)
                return;
            // the field itself is handled by clickAlwaysClickable while open
            setOpen(!open);
        }

        private void setOpen(boolean state) {
            open = state;
            Arsenic.getArsenic().getClickGuiScreen().setAlwaysClickedComponent(state ? this : null);
        }

        @Override
        public boolean clickAlwaysClickable(int mouseX, int mouseY, int mouseButton) {
            boolean inField = mouseX >= fieldX1 && mouseX <= x2 && mouseY >= fieldY1 && mouseY <= fieldY2;
            if (inField) {
                setOpen(false);
                SoundUtils.chordEnum();
                return true;
            }

            boolean inMenu = mouseX >= fieldX1 && mouseX <= x2 && mouseY > fieldY2 && mouseY <= menuY2;
            if (inMenu) {
                if (mouseButton == 0) {
                    float rowY1 = menuY1 + fieldHeight * 0.38f * 0.5f;
                    int row = (int) Math.floor((mouseY - rowY1) / rowHeight);
                    if (row >= 0 && row < modes.length) {
                        setValue(modes[row]);
                        setOpen(false);
                        SoundUtils.chordEnum();
                    }
                }
                return true; // never fall through to the rows underneath
            }

            // a click anywhere else closes the menu; on this row's own label it just closes, otherwise it carries on
            setOpen(false);
            return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
        }

        @Override
        public void setNotAlwaysClickable() {
            open = false;
        }
    }
}

package arsenic.module.property.impl;

import arsenic.gui.click.UITheme;
import arsenic.gui.click.impl.PropertyComponent;
import arsenic.gui.themes.ThemeManager;
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
    public PropertyComponent<EnumProperty<?>> createComponent() {
        return new EnumComponent(this);
    }

    private class EnumComponent extends PropertyComponent<EnumProperty<?>> implements IAlwaysClickable {

        private boolean open;
        private final AnimationTimer openTimer = new AnimationTimer(UITheme.DUR_EXPAND, () -> open, TickMode.CUBIC);

        private float fieldX1, fieldY1, fieldY2, fieldHeight, rowHeight;
        private int hoveredRow = -1;

        public EnumComponent(EnumProperty<?> p) {
            super(p);
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

            Runnable render = () -> {
                UITheme.surface(fieldX1, fieldY1, x2, fieldY2, radius, ThemeManager.getEnumBackground(),
                        UITheme.Elevation.FLAT);
                DrawUtils.drawRoundedOutline(fieldX1, fieldY1, x2, fieldY2, radius, 1f,
                        UITheme.alpha(UITheme.accent(), (int) (40 + 90 * Math.max(hover, openPct))));

                ri.getFr().drawString(getValue().name(), fieldX1 + pad, midPointY,
                        UITheme.mix(UITheme.textSecondary(), UITheme.textPrimary(), Math.max(hover, openPct)),
                        ri.getFr().CENTREY);

                UITheme.chevron(x2 - pad, midPointY, fieldHeight * 0.3f, Math.max(1f, fieldHeight * 0.075f),
                        UITheme.alpha(UITheme.textMuted(), (int) (170 + 85 * Math.max(hover, openPct))),
                        openPct);

                if (openPct > 0.01f) {
                    float menuY1 = fieldY2 + pad * 0.35f;
                    float menuY2 = menuY1 + menuHeight;
                    UITheme.surface(fieldX1, menuY1, x2, menuY2, radius,
                            UITheme.fade(ThemeManager.getEnumBackground(), 1f),
                            UITheme.Elevation.FLOATING, openPct);

                    ScissorUtils.subScissor((int) fieldX1, (int) menuY1, (int) x2, (int) menuY2, 2);
                    for (int i = 0; i < modes.length; i++) {
                        T m = modes[i];
                        float rowY1 = menuY1 + pad * 0.5f + i * rowHeight;
                        float rowMid = rowY1 + rowHeight / 2f;
                        boolean selected = m == getValue();

                        if (i == hoveredRow)
                            DrawUtils.drawRoundedRect(fieldX1 + pad * 0.35f, rowY1, x2 - pad * 0.35f,
                                    rowY1 + rowHeight, radius * 0.8f,
                                    UITheme.alpha(ThemeManager.getModuleHover(), (int) (40 * openPct)));
                        if (selected)
                            DrawUtils.drawRoundedRect(fieldX1 + pad * 0.35f, rowY1, x2 - pad * 0.35f,
                                    rowY1 + rowHeight, radius * 0.8f,
                                    UITheme.alpha(UITheme.accent(), (int) (46 * openPct)));

                        ri.getFr().drawString(m.name(), fieldX1 + pad, rowMid,
                                UITheme.fade(selected ? UITheme.textPrimary() : UITheme.textSecondary(), openPct),
                                ri.getFr().CENTREY);

                        if (selected)
                            UITheme.check(x2 - pad, rowMid, rowHeight * 0.34f,
                                    Math.max(1f, rowHeight * 0.09f),
                                    UITheme.alpha(UITheme.accent(), openPct));
                    }
                    ScissorUtils.endSubScissor();
                }
            };

            if (openPct > 0.01f)
                Arsenic.getArsenic().getClickGuiScreen().addToRenderLastList(render);
            else
                render.run();

            return height;
        }

        @Override
        public void mouseUpdate(int mouseX, int mouseY) {
            super.mouseUpdate(mouseX, mouseY);
            hoveredRow = -1;
            if (!open || mouseX < fieldX1 || mouseX > x2)
                return;
            float offset = mouseY - (fieldY2 + rowHeight * 0.5f);
            if (offset < 0)
                return;
            int row = (int) (offset / rowHeight);
            if (row < modes.length)
                hoveredRow = row;
        }

        @Override
        protected void click(int mouseX, int mouseY, int mouseButton) {
            setOpen(!open);
        }

        private void setOpen(boolean state) {
            open = state;
            Arsenic.getArsenic().getClickGuiScreen().setAlwaysClickedComponent(state ? this : null);
        }

        @Override
        public boolean clickAlwaysClickable(int mouseX, int mouseY, int mouseButton) {
            if (mouseX < fieldX1 || mouseX > x2)
                return false;

            if (mouseY >= fieldY1 && mouseY <= fieldY2) {
                setOpen(false);
                arsenic.utils.java.SoundUtils.chordEnum();
                return true;
            }

            float offset = mouseY - (fieldY2 + rowHeight * 0.5f);
            if (offset < 0 || offset > modes.length * rowHeight)
                return false;

            int row = (int) (offset / rowHeight);
            if (row < 0 || row >= modes.length)
                return false;

            setValue(modes[row]);
            setOpen(false);
            arsenic.utils.java.SoundUtils.chordEnum();
            return true;
        }

        @Override
        public void setNotAlwaysClickable() {
            open = false;
            hoveredRow = -1;
        }
    }
}

package arsenic.module.property.impl;

import arsenic.gui.click.UITheme;
import arsenic.gui.click.impl.PropertyComponent;
import arsenic.gui.themes.ThemeManager;
import arsenic.module.property.Property;
import arsenic.module.property.SerializableProperty;
import arsenic.utils.interfaces.IAlwaysClickable;
import arsenic.utils.interfaces.IContainer;
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
            if (p instanceof SerializableProperty)
                ((SerializableProperty<?>) p).loadFromJson(obj.get(p.getName()).getAsJsonObject());
        });
    }

    @Override
    public JsonObject saveInfoToJson(JsonObject obj) {
        getValue().forEach(p -> {
            if (p instanceof SerializableProperty)
                obj.add(p.getName(), ((SerializableProperty<?>) p).saveInfoToJson(new JsonObject()));
        });
        return obj;
    }

    private class FolderComponent extends PropertyComponent<FolderProperty> implements IContainer<PropertyComponent<?>> {

        private boolean open;
        private final List<PropertyComponent<?>> components;
        private float lastHeight;
        private final AnimationTimer openTimer = new AnimationTimer(UITheme.DUR_EXPAND, () -> open, TickMode.CUBIC);

        private FolderComponent(FolderProperty p) {
            super(p);
            components = getValue().stream().map(Property::createComponent).collect(Collectors.toList());
        }

        @Override
        protected float draw(RenderInfo ri) {
            openTimer.setMaxMs(UITheme.expandDuration(
                    lastHeight > 0 ? lastHeight : components.size() * height * 1.06f));
            float openPct = openTimer.getPercent();
            float hover = hoverPct();
            float pad = pad();

            expandX = pad;
            expandY = openPct * lastHeight;

            float wellX1 = x1 - pad * 0.7f;
            float wellX2 = x2 + pad * 0.7f;
            float radius = UITheme.radiusCard(height);

            UITheme.surface(wellX1, y1, wellX2, y2 + expandY, radius,
                    ThemeManager.getFolderBackground(), UITheme.Elevation.FLAT);
            UITheme.hoverWash(wellX1, y1, wellX2, y2, radius, hover * (1f - openPct * 0.5f));

            float railW = Math.max(1f, height * 0.06f);
            DrawUtils.drawRoundedRect(wellX1, y1 + pad * 0.4f, wellX1 + railW, y2 + expandY - pad * 0.4f,
                    railW / 2f, UITheme.alpha(UITheme.accent(), (int) (110 + 110 * openPct)));

            UITheme.chevron(x2 - pad * 0.6f, midPointY, height * 0.28f, Math.max(1f, height * 0.06f),
                    UITheme.alpha(UITheme.textMuted(), (int) (160 + 95 * Math.max(hover, openPct))), openPct);

            if (openPct < 0.9f)
                ri.getFr().drawString(components.size() + " settings", x2 - pad * 1.6f, midPointY,
                        UITheme.alpha(UITheme.textMuted(), (int) (170 * (1f - openPct))),
                        ri.getFr().getScaleModifier(0.75f), ri.getFr().LEFTSHIFTX, ri.getFr().CENTREY);

            PosInfo pi = new PosInfo(x1 + pad, y2);
            if (openPct > 0.001f) {
                ScissorUtils.subScissor((int) wellX1, (int) y2, (int) wellX2, (int) (y2 + expandY), 2);
                pi.moveY(pad * 0.4f);
                components.forEach(component -> pi.moveY(component.updateComponent(pi, ri) * 1.06f));
                pi.moveY(pad * 0.5f);
                ScissorUtils.endSubScissor();
                if (open)
                    lastHeight = pi.getY() - y2;
            }

            return height + expandY;
        }

        @Override
        protected void click(int mouseX, int mouseY, int mouseButton) {
            open = !open;
            if (!open) {
                getContents().forEach(component -> {
                    if (component instanceof IAlwaysClickable)
                        ((IAlwaysClickable) component).setNotAlwaysClickable();
                });
            }
        }

        @Override
        public Collection<PropertyComponent<?>> getContents() {
            return components;
        }
    }
}

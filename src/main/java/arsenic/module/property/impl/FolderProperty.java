package arsenic.module.property.impl;

import arsenic.gui.click.UITheme;
import arsenic.gui.click.impl.PropertyComponent;
import arsenic.gui.design.Hover;
import arsenic.gui.design.M3;
import arsenic.gui.design.M3;
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
            if (!(p instanceof SerializableProperty) || !obj.has(p.getName()) || !obj.get(p.getName()).isJsonObject())
                return;
            try {
                ((SerializableProperty<?>) p).loadFromJson(obj.getAsJsonObject(p.getName()));
            } catch (RuntimeException ignored) {
                // a broken value keeps its current one, the rest of the folder still loads
            }
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
                    railW / 2f, M3.alpha(M3.primary(), (int) (110 + 110 * openPct)));

            UITheme.chevron(x2 - pad * 0.6f, midPointY, height * 0.28f, Math.max(1f, height * 0.06f),
                    UITheme.alpha(UITheme.textMuted(), (int) (160 + 95 * Math.max(hover, openPct))), openPct);

            PosInfo pi = new PosInfo(x1 + pad, y2);
            if (openPct > 0.001f) {
                ScissorUtils.subScissor((int) wellX1, (int) y2, (int) wellX2, (int) (y2 + expandY), 2);
                Hover.pushClip(wellX1, y2, wellX2, y2 + expandY);
                pi.moveY(pad * 0.4f);
                float previousRow = PropertyComponent.rowWidth;
                PropertyComponent.rowWidth = width - pad * 2f;
                components.forEach(component -> pi.moveY(component.updateComponent(pi, ri) * 1.06f));
                PropertyComponent.rowWidth = previousRow;
                pi.moveY(pad * 0.5f);
                Hover.popClip();
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

        private int countVisible() {
            int n = 0;
            for (Property<?> c : getValue())
                if (c.isVisible())
                    n++;
            return n;
        }

        @Override
        public Collection<PropertyComponent<?>> getContents() {
            return components;
        }
    }
}

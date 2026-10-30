package arsenic.module.property.impl;

import arsenic.gui.click.UITheme;
import arsenic.gui.design.M3Components;
import arsenic.main.Arsenic;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;

import arsenic.gui.click.impl.PropertyComponent;
import arsenic.module.property.IReliable;
import arsenic.module.property.SerializableProperty;
import arsenic.utils.render.RenderInfo;

import java.util.function.Supplier;

public class BooleanProperty extends SerializableProperty<Boolean> implements IReliable {

    public BooleanProperty(String name, Boolean value) {
        super(name, value);
    }

    @Override
    public JsonObject saveInfoToJson(@NotNull JsonObject obj) {
        obj.addProperty("enabled", value);
        return obj;
    }

    @Override
    public void loadFromJson(@NotNull JsonObject obj) {
        setValueSilently(obj.get("enabled").getAsBoolean());
    }

    @Override
    public Supplier<Boolean> valueCheck(String value) {
        return () -> Boolean.parseBoolean(value) == this.value && isVisible();
    }

    @Override
    public String currentValue() {
        return String.valueOf(value);
    }

    @Override
    public java.util.List<String> possibleValues() {
        return java.util.Arrays.asList("true", "false");
    }

    @Override
    public PropertyComponent<BooleanProperty> createComponent() {
        return new PropertyComponent<BooleanProperty>(this) {
            private final AnimationTimer toggleTimer =
                    new AnimationTimer(UITheme.DUR_TOGGLE, BooleanProperty.this::getValue, TickMode.CUBIC);

            @Override
            protected float labelRight() {
                return x2 - height * 0.56f * 1.625f - pad();
            }

            @Override
            protected float draw(RenderInfo ri) {
                float trackW = height * 0.56f * 1.625f;
                M3Components.switchControl(x2 - trackW, y1, height, toggleTimer.getPercent(), hoverPct());
                return height;
            }

            @Override
            protected void click(int mouseX, int mouseY, int mouseButton) {
                if (mouseButton != 0)
                    return;
                boolean on = !getValue();
                setValue(on);
                if (on)
                    SoundUtils.chordEnable();
                else
                    SoundUtils.chordDisable();
                Arsenic.getArsenic().getConfigManager().saveConfig();
            }

            @Override
            protected void playClickSound() {
            }
        };
    }
}

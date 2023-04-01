package arsenic.module.impl.misc;

import arsenic.module.property.impl.*;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import org.lwjgl.input.Keyboard;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;

@ModuleInfo(name = "TestModule3", category = ModuleCategory.OTHER, keybind = Keyboard.KEY_R)
public class TestModule3 extends Module {
    public final DoubleProperty doubleProperty = new DoubleProperty("Double prop", new DoubleValue(0, 180, 70, 1));
    public final DoubleProperty doubleProperty2 = new DoubleProperty("Double prop", new DoubleValue(0, 180, 70, 1));

    @Override
    protected void onEnable() {
        mc.thePlayer.sendChatMessage("enable");
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        mc.thePlayer.sendChatMessage("disable");
        super.onDisable();
    }

    public enum testEnum {
        A,B,C
    }

}
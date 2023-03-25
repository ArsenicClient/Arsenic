package arsenic.module.impl.misc;

import arsenic.module.property.impl.*;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import org.lwjgl.input.Keyboard;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.doubleProperty.DoubleProperty;
import arsenic.module.property.impl.doubleProperty.DoubleValue;

@ModuleInfo(name = "TestModule", category = ModuleCategory.OTHER, keybind = Keyboard.KEY_R)
public class TestModule extends Module {



    public final ColourProperty colourProperty = new ColourProperty("color", 0xFF20BB00);

    public final EnumProperty<testEnum> enumProperty = new EnumProperty<>("Mode: ", testEnum.C);

    public final DoubleProperty doubleProperty = new DoubleProperty("Double prop", new DoubleValue(0, 180, 70, 1));

    public final RangeProperty rangeProperty = new RangeProperty("Range prop", new RangeValue(50,150, 60, 120, 0.5d));

    public final BooleanProperty test1 = new BooleanProperty("Test1", true);

    @PropertyInfo(reliesOn = "Test1", value = "true")
    public final BooleanProperty test2 = new BooleanProperty("Test2", true);

    @PropertyInfo(reliesOn = "Test2", value = "true")
    public final DescriptionProperty test3 = new DescriptionProperty("Test3");

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

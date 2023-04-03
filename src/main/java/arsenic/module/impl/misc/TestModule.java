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

@ModuleInfo(name = "TestModule", category = ModuleCategory.OTHER, keybind = Keyboard.KEY_R)
public class TestModule extends Module {

    private final BooleanProperty booleanProp =  new BooleanProperty("Cool beans", false);
    private final EnumProperty<testEnum> enumProp =  new EnumProperty<testEnum>("Range Mode:", testEnum.Close);
    public final FolderProperty folderProperty = new FolderProperty("folder prop", booleanProp, enumProp);

    public final EnumProperty<testEnum> enumProperty = new EnumProperty<testEnum>("Range Mode:", testEnum.Close);

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
        Far,Close,NotAsFar
    }

}

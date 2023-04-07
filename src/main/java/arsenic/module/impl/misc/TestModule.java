package arsenic.module.impl.misc;

import arsenic.module.property.impl.*;
import org.lwjgl.input.Keyboard;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;


@ModuleInfo(name = "TestModule", category = ModuleCategory.OTHER, keybind = Keyboard.KEY_R)
public class TestModule extends Module {

    private final BooleanProperty booleanProp =  new BooleanProperty("Coolbeans", false);

    @PropertyInfo(reliesOn = "Coolbeans", value = "true")
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

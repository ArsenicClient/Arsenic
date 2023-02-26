package arsenic.module.impl.misc;

import org.lwjgl.input.Keyboard;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.DescriptionProperty;

@ModuleInfo(name = "TestModule", category = ModuleCategory.OTHER, keybind = Keyboard.KEY_R)
public class TestModule extends Module {

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

}

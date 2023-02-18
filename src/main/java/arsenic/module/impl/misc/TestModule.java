package arsenic.module.impl.misc;

import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import org.lwjgl.input.Keyboard;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;

@ModuleInfo(name = "TestModule", category = ModuleCategory.OTHER, keybind = Keyboard.KEY_R)
public class TestModule extends Module {

    public final BooleanProperty test1 = new BooleanProperty("Test", true);
    @PropertyInfo(reliesOn = "Test", value = "true")
    public final BooleanProperty test2 = new BooleanProperty("ReliesOnTest", true);
    public final BooleanProperty test3 = new BooleanProperty("ReliesOnReliesOnTest", true);

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

package arsenic.module.impl.misc;

import org.lwjgl.input.Keyboard;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;

@ModuleInfo(name = "Test Module", category = ModuleCategory.MISC, keybind = Keyboard.KEY_R)
public class TestModule extends Module {

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

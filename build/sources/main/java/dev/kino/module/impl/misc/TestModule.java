package dev.kino.module.impl.misc;

import dev.kino.module.Module;
import dev.kino.module.ModuleCategory;
import dev.kino.module.ModuleInfo;
import org.lwjgl.input.Keyboard;

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

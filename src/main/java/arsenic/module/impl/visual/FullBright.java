package arsenic.module.impl.visual;

import org.lwjgl.input.Keyboard;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;

@ModuleInfo(name = "FullBright", category = ModuleCategory.WORLD, keybind = Keyboard.KEY_F)
public class FullBright extends Module {

    int OriginalGamma = 0;

    @Override
    protected void onEnable() {
        OriginalGamma = (int) mc.gameSettings.gammaSetting;
        mc.gameSettings.gammaSetting = 1000;
    }

    @Override
    protected void onDisable() {
        mc.gameSettings.gammaSetting = OriginalGamma;
    }

}

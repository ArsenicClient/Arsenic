import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * One key turns every other module off. Enable this module (its keybind, set in the ClickGUI) and everything that was on
 * is switched off and remembered; disable it again and those modules come back on. Modules you turn on while panic is
 * active stay on. The HUD is not hidden.
 */
@ModuleInfo(name = "PanicKey", description = "One key turns every other module off, and back on", category = ModuleCategory.PLAYER, keybind = Keyboard.KEY_END)
public class PanicKey extends Module {

    private final List<Module> switchedOff = new ArrayList<>();

    @Override
    protected void onEnable() {
        switchedOff.clear();
        for (Module m : Arsenic.getArsenic().getModuleManager().getModules()) {
            if (m == this || !m.isEnabled()) continue;
            switchedOff.add(m);
            m.setEnabled(false);
        }
    }

    @Override
    protected void onDisable() {
        for (Module m : switchedOff) m.setEnabled(true);
        switchedOff.clear();
    }
}

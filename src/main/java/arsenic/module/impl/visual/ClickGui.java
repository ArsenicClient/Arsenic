package arsenic.module.impl.visual;

import arsenic.gui.click.ClickGuiScreen;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import org.lwjgl.input.Keyboard;

@ModuleInfo(name = "Click GUI", category = ModuleCategory.VISUAL,
        hidden = true, keybind = Keyboard.KEY_RSHIFT)
public class ClickGui extends Module {

    private ClickGuiScreen screen;

    @Override
    protected void onEnable() {
        if(screen == null)
            screen = new ClickGuiScreen();

        mc.displayGuiScreen(screen);

        super.onEnable();
        setEnabled(false);
    }

}

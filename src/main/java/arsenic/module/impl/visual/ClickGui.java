package arsenic.module.impl.visual;

import arsenic.gui.click.ClickGuiScreen;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import org.lwjgl.input.Keyboard;

@ModuleInfo(name = "ClickGUI", category = ModuleCategory.SETTINGS, hidden = true, keybind = Keyboard.KEY_RSHIFT)
public class ClickGui extends Module {
    public final BooleanProperty customFont = new BooleanProperty("Custom Font", true);

    private ClickGuiScreen screen;

    @Override
    protected void postApplyConfig() {
        screen = Arsenic.getArsenic().getClickGuiScreen();
        screen.init(this);
    }

    @Override
    protected void onEnable() {
        mc.displayGuiScreen(screen);
        setEnabled(false);
    }

    public final ClickGuiScreen getScreen() { return screen; }

}

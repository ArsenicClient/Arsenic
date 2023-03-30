package arsenic.module.impl.visual;

import arsenic.main.Arsenic;
import org.lwjgl.input.Keyboard;

import arsenic.gui.click.ClickGuiScreen;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;

@ModuleInfo(name = "ClickGUI", category = ModuleCategory.CLIENT, hidden = true, keybind = Keyboard.KEY_RSHIFT)
public class ClickGui extends Module {

    public final DoubleProperty expandTop = new DoubleProperty("Expand Top", new DoubleValue(2D, 0D, 3D, 0.5D));
    public final BooleanProperty customFont = new BooleanProperty("Custom Font", false);

    private ClickGuiScreen screen;

    @Override
    protected void onEnable() {
        if (screen == null) {
            screen = Arsenic.getArsenic().getClickGuiScreen();
            screen.init();
        }

        mc.displayGuiScreen(screen);

        setEnabled(false);
    }

    public final ClickGuiScreen getScreen() { return screen; }

}

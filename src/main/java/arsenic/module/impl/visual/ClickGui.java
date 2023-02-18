package arsenic.module.impl.visual;

import org.lwjgl.input.Keyboard;

import arsenic.gui.click.ClickGuiScreen;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleProperty.DoubleProperty;
import arsenic.module.property.impl.doubleProperty.DoubleValue;

@ModuleInfo(name = "ClickGUI", category = ModuleCategory.CLIENT, hidden = true, keybind = Keyboard.KEY_RSHIFT)
public class ClickGui extends Module {

    public final DoubleProperty expandTop = new DoubleProperty("Expand Top", new DoubleValue(2D, 0D, 3D, 0.5D));
    public final BooleanProperty customFont = new BooleanProperty("Custom Font", false);

    private ClickGuiScreen screen;


    @Override
    protected void onEnable() {
        if (screen == null)
            screen = new ClickGuiScreen();

        mc.displayGuiScreen(screen);

        setEnabled(false);
    }

    public final ClickGuiScreen getScreen() {
        return screen;
    }

}

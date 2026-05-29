package arsenic.module.impl.visual;

import arsenic.gui.click.ClickGuiScreen;
import arsenic.gui.themes.Theme;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import org.lwjgl.input.Keyboard;

import java.util.function.Supplier;

@ModuleInfo(name = "ClickGUI", category = ModuleCategory.SETTINGS, hidden = true, keybind = Keyboard.KEY_RSHIFT)
public class ClickGui extends Module {
    public final BooleanProperty customFont = new BooleanProperty("Custom Font", true);
    public final EnumProperty<LogoMode> logoMode = new EnumProperty<>("Logo", LogoMode.CLASSIC);
    public final EnumProperty<ThemeMode> themeMode = new EnumProperty<ThemeMode>("Theme", ThemeMode.CLASSIC) {
        @Override
        public void onValueUpdate() {
            Arsenic.getArsenic().getThemeManager().setCurrentTheme(Arsenic.getArsenic().getThemeManager().getContentByJsonKey(getValue().get()));
        }
    };

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

    public enum LogoMode {
        CLASSIC, MODERN
    }

    public enum ThemeMode {
        CLASSIC, VOID, SPECTER, EMBER, JADE, OBSIDIAN, SAKURA, TOXIN, CLOUD, MONO;

        public String get() {
            return name().charAt(0) + name().substring(1).toLowerCase();
        }
    }

}

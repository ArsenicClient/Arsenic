package arsenic.module.impl.visual;

import arsenic.gui.click.ClickGuiScreen;
import arsenic.gui.themes.Theme;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import org.lwjgl.input.Keyboard;

import java.util.function.Supplier;

@ModuleInfo(name = "ClickGUI", category = ModuleCategory.SETTINGS, hidden = true, keybind = Keyboard.KEY_RSHIFT)
public class ClickGui extends Module {
    public final BooleanProperty customFont = new BooleanProperty("Custom Font", true);
    public final EnumProperty<LogoMode> logoMode = new EnumProperty<>("Logo", LogoMode.CLASSIC);

    // --- overdone shader eye-candy ---
    public final BooleanProperty shaderBackground = new BooleanProperty("Shader Background", true);
    public final EnumProperty<BgShader> backgroundShader = new EnumProperty<>("Background", BgShader.ZIPPYZAPS);
    public final DoubleProperty backgroundOpacity = new DoubleProperty("Background Opacity", new DoubleValue(0, 100, 45, 1));
    public final DoubleProperty backgroundSpeed = new DoubleProperty("Background Speed", new DoubleValue(0.1, 5, 1, 0.1));
    public final BooleanProperty scanlineOverlay = new BooleanProperty("Scanline Overlay", false);
    public final BooleanProperty sounds = new BooleanProperty("CMaj Sounds", true);
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

    public enum BgShader {
        PLASMA("plasma"), AURORA("aurora"), STARFIELD("starfield"), MATRIX("matrix"),
        SYNTHWAVE("synthwave"), WARP("warpTunnel"), VORONOI("voronoiGlow"),
        FRACTAL("fractalPyramid"), CHROME("liquidChrome"), HEXGRID("hexGrid"),
        FIRESTORM("fireStorm"), CAUSTICS("oceanCaustics"), NEBULA("nebula"),
        VHS("vhsGlitch"), ZIPPYZAPS("zippyZaps"), RAINBOW("rainbowShader");

        public final String fsh;

        BgShader(String fsh) {
            this.fsh = fsh;
        }
    }

    public enum ThemeMode {
        CLASSIC, VOID, SPECTER, EMBER, JADE, OBSIDIAN, SAKURA, TOXIN, CLOUD, MONO;

        public String get() {
            return name().charAt(0) + name().substring(1).toLowerCase();
        }
    }

}

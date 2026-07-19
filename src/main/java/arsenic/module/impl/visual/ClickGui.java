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
    public final EnumProperty<BgShader> backgroundShader = new EnumProperty<>("Background", BgShader.FIRESTORM);
    public final DoubleProperty backgroundOpacity = new DoubleProperty("Background Opacity", new DoubleValue(0, 100, 45, 1));
    public final DoubleProperty backgroundSpeed = new DoubleProperty("Background Speed", new DoubleValue(0.1, 5, 1, 0.1));
    public final BooleanProperty scanlineOverlay = new BooleanProperty("Scanline Overlay", true);
    public final BooleanProperty burnTransition = new BooleanProperty("Burn Transition", true);
    public final EnumProperty<Transition> transition = new EnumProperty<>("Transition", Transition.BURN);
    public final DoubleProperty burnTime = new DoubleProperty("Burn Time", new DoubleValue(0.2, 3.0, 0.7, 0.1));
    public final BooleanProperty sounds = new BooleanProperty("CMaj Sounds", true);

    // --- 3D depth (drop shadows, elevation & edge glow) ---
    // 100 = the built-in defaults; slide up for more pop, down for a flatter look.
    public final BooleanProperty depth = new BooleanProperty("3D Depth", true);
    public final DoubleProperty shadowStrength = new DoubleProperty("Shadow Strength", new DoubleValue(0, 200, 100, 5));
    public final DoubleProperty elevation = new DoubleProperty("Elevation", new DoubleValue(0, 200, 100, 5));
    public final DoubleProperty edgeGlow = new DoubleProperty("Edge Glow", new DoubleValue(0, 500, 100, 5));
    public final EnumProperty<ThemeMode> themeMode = new EnumProperty<ThemeMode>("Theme", ThemeMode.EMBER) {
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

    // ---------------------------------------------------------------
    //  Depth helpers - the GUI render pulls its shadow/edge values through
    //  these so the "3D Depth" sliders take effect live. A base value is the
    //  built-in look at 100%; the slider scales it. When depth is off the
    //  shadow/edge alphas return 0 so nothing is drawn.
    // ---------------------------------------------------------------
    private static ClickGui self() {
        try {
            return Arsenic.getArsenic().getModuleManager().getModuleByClass(ClickGui.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static int shadowAlpha(int base) {
        ClickGui cg = self();
        if (cg == null || !cg.depth.getValue()) return 0;
        return (int) (base * (cg.shadowStrength.getValue().getInput() / 100.0));
    }

    public static float shadowSpread(float base) {
        ClickGui cg = self();
        if (cg == null) return base;
        return (float) (base * (cg.elevation.getValue().getInput() / 100.0));
    }

    public static int edgeAlpha(int base) {
        ClickGui cg = self();
        if (cg == null || !cg.depth.getValue()) return 0;
        return (int) (base * (cg.edgeGlow.getValue().getInput() / 100.0));
    }

    public static float scrollEase() {
        return 0.15f; // fixed smooth-scroll easing
    }

    public enum LogoMode {
        CLASSIC, MODERN
    }

    /** Open/close transition styles. Ordinals are passed straight to
     *  paperBurn.fsh / burnMaskFade.fsh as the {@code style} uniform. */
    public enum Transition {
        BURN, DISSOLVE, GLITCH, FADE
    }

    public enum BgShader {
        AURORA("aurora"), STARFIELD("starfield"), SYNTHWAVE("synthwave"),
        CHROME("liquidChrome"), FIRESTORM("fireStorm"), CAUSTICS("oceanCaustics"),
        NEBULA("nebula"), ZIPPYZAPS("zippyZaps");

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

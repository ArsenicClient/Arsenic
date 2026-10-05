package arsenic.gui.click;

import arsenic.utils.java.MathUtils;
import arsenic.utils.interfaces.ISerializable;
import com.google.gson.JsonObject;

public final class GuiStyle implements ISerializable {

    private static final GuiStyle INSTANCE = new GuiStyle();

    public static GuiStyle get() { return INSTANCE; }

    private GuiStyle() {}


    public enum BgShader {
        AURORA("aurora"), STARFIELD("starfield"), SYNTHWAVE("synthwave"),
        CHROME("liquidChrome"), FIRESTORM("fireStorm"), CAUSTICS("oceanCaustics"),
        NEBULA("nebula"), ZIPPYZAPS("zippyZaps");

        public final String fsh;

        BgShader(String fsh) { this.fsh = fsh; }
    }

    public enum Transition {
        BURN, DISSOLVE, GLITCH, FADE
    }

    public enum LogoMode { CLASSIC, MODERN }

    public enum Preset {

        CLEAN("Clean", "Flat surfaces, no shaders",
                false, BgShader.AURORA, 0, 1f,
                false, 0f, 100f,
                true, 55, 60, 40,
                false, true, Transition.FADE, 0.25f, LogoMode.MODERN),

        GLASS("Glass", "Frosted panels over a live backdrop",
                true, BgShader.FIRESTORM, 45, 1f,
                true, 1f, 55f,
                true, 100, 100, 100,
                true, true, Transition.BURN, 0.7f, LogoMode.CLASSIC),

        OVERDONE("Overdone", "Every effect, turned up",
                true, BgShader.SYNTHWAVE, 80, 1.6f,
                true, 1.8f, 40f,
                true, 170, 160, 280,
                true, true, Transition.BURN, 1.2f, LogoMode.MODERN),

        PERFORMANCE("Performance", "No shaders, no shadows",
                false, BgShader.AURORA, 0, 1f,
                false, 0f, 100f,
                false, 0, 0, 0,
                false, false, Transition.FADE, 0.15f, LogoMode.MODERN);

        public final String label, description;

        public final boolean background;
        public final BgShader shader;
        public final int backgroundOpacity;
        public final float backgroundSpeed;

        public final boolean glass;
        public final float glassStrength;
        public final float glassFrost;

        public final boolean depth;
        public final int shadowStrength, elevation, edgeGlow;

        public final boolean scanlines;
        public final boolean transitionEnabled;
        public final Transition transition;
        public final float transitionTime;
        public final LogoMode logo;

        Preset(String label, String description,
               boolean background, BgShader shader, int backgroundOpacity, float backgroundSpeed,
               boolean glass, float glassStrength, float glassFrost,
               boolean depth, int shadowStrength, int elevation, int edgeGlow,
               boolean scanlines, boolean transitionEnabled, Transition transition,
               float transitionTime, LogoMode logo) {
            this.label = label;
            this.description = description;
            this.background = background;
            this.shader = shader;
            this.backgroundOpacity = backgroundOpacity;
            this.backgroundSpeed = backgroundSpeed;
            this.glass = glass;
            this.glassStrength = glassStrength;
            this.glassFrost = glassFrost;
            this.depth = depth;
            this.shadowStrength = shadowStrength;
            this.elevation = elevation;
            this.edgeGlow = edgeGlow;
            this.scanlines = scanlines;
            this.transitionEnabled = transitionEnabled;
            this.transition = transition;
            this.transitionTime = transitionTime;
            this.logo = logo;
        }
    }


    private Preset preset = Preset.GLASS;
    private boolean customFont = true;
    private boolean sounds = true;
    private boolean showMoreModules = false;
    /** The whole menu look: "Element" (default) or "Ocean". The loading screen reads it straight from the config file at start-up. */
    private String screenStyle = "Element";

    public Preset getPreset() { return preset; }

    public void setPreset(Preset preset) {
        if (preset != null)
            this.preset = preset;
    }

    public boolean isCustomFont() { return customFont; }

    public void setCustomFont(boolean customFont) { this.customFont = customFont; }

    public boolean isSounds() { return sounds; }

    public void setSounds(boolean sounds) { this.sounds = sounds; }

    public String getScreenStyle() { return screenStyle; }


    public static boolean element() { return "Element".equals(INSTANCE.screenStyle); }

    public void setScreenStyle(String style) {
        if ("Toxic".equals(style))
            style = "Element";                      // the removed Toxic style becomes the default
        if ("Ocean".equals(style) || "Element".equals(style))
            this.screenStyle = style;
    }

    public boolean isShowMoreModules() { return showMoreModules; }

    public void setShowMoreModules(boolean showMoreModules) { this.showMoreModules = showMoreModules; }


    private static Preset p() { return INSTANCE.preset; }

    public static boolean backgroundEnabled() { return p().background; }

    public static String backgroundShader() {
        try {
            arsenic.gui.themes.Theme theme =
                    arsenic.main.Arsenic.getArsenic().getThemeManager().getCurrentTheme();
            if (theme != null && theme.getBgShader() != null)
                return theme.getBgShader().fsh;
        } catch (Exception ignored) {
        }
        return p().shader.fsh;
    }

    public static float backgroundOpacity() { return p().backgroundOpacity / 100f; }

    public static float backgroundSpeed() { return p().backgroundSpeed; }

    public static boolean scanlinesEnabled() { return p().scanlines; }

    public static boolean transitionEnabled() { return p().transitionEnabled; }

    public static LogoMode logoMode() { return p().logo; }

    public static Transition transition() { return p().transition; }

    public static int transitionTimeMs() { return (int) (p().transitionTime * 1000f); }

    public static boolean fontEnabled() { return INSTANCE.customFont; }

    public static boolean soundsEnabled() { return INSTANCE.sounds; }

    public static int shadowAlpha(int base) {
        Preset preset = p();
        return preset.depth ? (int) (base * (preset.shadowStrength / 100.0)) : 0;
    }

    public static float shadowSpread(float base) {
        return (float) (base * (p().elevation / 100.0));
    }

    public static int edgeAlpha(int base) {
        Preset preset = p();
        return preset.depth ? (int) (base * (preset.edgeGlow / 100.0)) : 0;
    }

    public static float scrollEase() { return 0.15f; }

    public static boolean glassEnabled() { return p().glass; }

    public static float glassStrength() { return p().glass ? p().glassStrength : 0f; }

    public static int glassify(int argb) {
        Preset preset = p();
        if (!preset.glass)
            return argb;
        int a = (argb >> 24) & 0xFF;
        int na = MathUtils.clamp((int) (a * (preset.glassFrost / 100f)), 0, 255);
        return (na << 24) | (argb & 0x00FFFFFF);
    }


    @Override
    public String getJsonKey() { return "GuiStyle"; }

    @Override
    public JsonObject saveInfoToJson(JsonObject obj) {
        obj.addProperty("preset", preset.name());
        obj.addProperty("customFont", customFont);
        obj.addProperty("sounds", sounds);
        obj.addProperty("showMoreModules", showMoreModules);
        obj.addProperty("screenStyle", screenStyle);
        return obj;
    }

    @Override
    public void loadFromJson(JsonObject obj) {
        if (obj == null)
            return;
        try {
            if (obj.has("preset"))
                preset = Preset.valueOf(obj.get("preset").getAsString());
            if (obj.has("customFont"))
                customFont = obj.get("customFont").getAsBoolean();
            if (obj.has("sounds"))
                sounds = obj.get("sounds").getAsBoolean();
            if (obj.has("loadingScreen"))           // the old name for this setting
                setScreenStyle(obj.get("loadingScreen").getAsString());
            if (obj.has("screenStyle"))
                setScreenStyle(obj.get("screenStyle").getAsString());
            if (obj.has("showMoreModules"))
                showMoreModules = obj.get("showMoreModules").getAsBoolean();
        } catch (Exception e) {
            preset = Preset.GLASS;
        }
    }
}

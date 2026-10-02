package arsenic.gui.click;

import arsenic.utils.interfaces.ISerializable;
import com.google.gson.JsonObject;

/**
 * Everything about how the ClickGUI looks, expressed as four presets instead of a module full of
 * sliders.
 * <p>
 * This used to be a hidden {@code ClickGui} module carrying nineteen properties - shader on/off,
 * which shader, its opacity and speed, glass on/off, glass strength, glass frost, depth on/off,
 * shadow strength, elevation, edge glow, transition style, transition time, scanlines, logo, font,
 * sounds. Nobody tunes nineteen interacting visual knobs; they pick a look. So the knobs are gone
 * and each {@link Preset} fixes all of them at once, leaving three things a person might actually
 * want to change independently: which preset, whether the custom font is on, and whether the UI
 * makes noise. Colour stays with the theme manager, where it already lived.
 * <p>
 * The static accessors at the bottom are the same names the render stack already called on the old
 * module, so every call site kept working - they just read from the active preset now.
 * <p>
 * State is client-level rather than per-config: which preset you like is a property of your
 * install, not of a combat config you might swap mid-game. It rides in {@code clientConfig.json}
 * alongside the theme.
 */
public final class GuiStyle implements ISerializable {

    private static final GuiStyle INSTANCE = new GuiStyle();

    public static GuiStyle get() { return INSTANCE; }

    private GuiStyle() {}

    // ---------------------------------------------------------------
    //  Presets
    // ---------------------------------------------------------------

    /** Background shader options. Names map to files in {@code assets/minecraft/shaders}. */
    public enum BgShader {
        AURORA("aurora"), STARFIELD("starfield"), SYNTHWAVE("synthwave"),
        CHROME("liquidChrome"), FIRESTORM("fireStorm"), CAUSTICS("oceanCaustics"),
        NEBULA("nebula"), ZIPPYZAPS("zippyZaps");

        public final String fsh;

        BgShader(String fsh) { this.fsh = fsh; }
    }

    /** Open/close transition styles. Ordinals are the {@code style} uniform in the burn shaders. */
    public enum Transition {
        BURN, DISSOLVE, GLITCH, FADE
    }

    public enum LogoMode { CLASSIC, MODERN }

    /**
     * A complete look. Every field the old module exposed as a property is set here, so switching
     * preset can never leave the GUI in a half-configured state the way changing one slider at a
     * time could.
     */
    public enum Preset {

        /**
         * Flat and quick. No shader backdrop, no glass, minimal depth - reads as a normal, solid
         * interface. The one to pick if the effects get in the way of actually using the menu.
         */
        CLEAN("Clean", "Flat surfaces, no shaders",
                false, BgShader.AURORA, 0, 1f,
                false, 0f, 100f,
                true, 55, 60, 40,
                false, true, Transition.FADE, 0.25f, LogoMode.MODERN),

        /**
         * The house look: frosted panels floating over a slow shader backdrop, full depth, burn
         * transition. What the client shipped with.
         */
        GLASS("Glass", "Frosted panels over a live backdrop",
                true, BgShader.FIRESTORM, 45, 1f,
                true, 1f, 55f,
                true, 100, 100, 100,
                true, true, Transition.BURN, 0.7f, LogoMode.CLASSIC),

        /**
         * Everything at once - brighter backdrop, heavier glass, exaggerated depth, scanlines and a
         * long burn. Deliberately too much.
         */
        OVERDONE("Overdone", "Every effect, turned up",
                true, BgShader.SYNTHWAVE, 80, 1.6f,
                true, 1.8f, 40f,
                true, 170, 160, 280,
                true, true, Transition.BURN, 1.2f, LogoMode.MODERN),

        /**
         * Nothing optional runs: no backdrop shader, no glass pass, no shadow layers, no transition.
         * For weak machines, or when the GUI is open during a fight.
         */
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

    // ---------------------------------------------------------------
    //  State
    // ---------------------------------------------------------------

    private Preset preset = Preset.GLASS;
    private boolean customFont = true;
    private boolean sounds = true;

    public Preset getPreset() { return preset; }

    public void setPreset(Preset preset) {
        if (preset != null)
            this.preset = preset;
    }

    public boolean isCustomFont() { return customFont; }

    public void setCustomFont(boolean customFont) { this.customFont = customFont; }

    public boolean isSounds() { return sounds; }

    public void setSounds(boolean sounds) { this.sounds = sounds; }

    // ---------------------------------------------------------------
    //  Render-stack accessors
    //
    //  Same names and shapes the old module exposed, so nothing downstream
    //  had to learn a new API - they simply resolve against the active preset.
    //  All of them are null-safe by construction: the preset is never null.
    // ---------------------------------------------------------------

    private static Preset p() { return INSTANCE.preset; }

    public static boolean backgroundEnabled() { return p().background; }

    /**
     * The backdrop shader, taken from the active theme so colour and backdrop always agree. The
     * preset still decides whether a backdrop runs at all and how strong it is; it only supplies the
     * shader itself as a fallback if the theme manager is not ready yet.
     */
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

    /** Transition duration in milliseconds. */
    public static int transitionTimeMs() { return (int) (p().transitionTime * 1000f); }

    public static boolean fontEnabled() { return INSTANCE.customFont; }

    public static boolean soundsEnabled() { return INSTANCE.sounds; }

    /** Shadow opacity for a given base alpha; 0 when depth is off. */
    public static int shadowAlpha(int base) {
        Preset preset = p();
        return preset.depth ? (int) (base * (preset.shadowStrength / 100.0)) : 0;
    }

    /** Shadow spread for a given base distance. */
    public static float shadowSpread(float base) {
        return (float) (base * (p().elevation / 100.0));
    }

    /** Edge-rim opacity for a given base alpha; 0 when depth is off. */
    public static int edgeAlpha(int base) {
        Preset preset = p();
        return preset.depth ? (int) (base * (preset.edgeGlow / 100.0)) : 0;
    }

    public static float scrollEase() { return 0.15f; }

    public static boolean glassEnabled() { return p().glass; }

    /** Highlight/rim intensity for {@code DrawUtils.drawGlassRect}, 0..2. */
    public static float glassStrength() { return p().glass ? p().glassStrength : 0f; }

    /**
     * Makes a panel background translucent so the backdrop shows through. Returns the colour
     * untouched when the preset has no glass, so an opaque preset stays genuinely opaque.
     */
    public static int glassify(int argb) {
        Preset preset = p();
        if (!preset.glass)
            return argb;
        int a = (argb >> 24) & 0xFF;
        int na = Math.max(0, Math.min(255, (int) (a * (preset.glassFrost / 100f))));
        return (na << 24) | (argb & 0x00FFFFFF);
    }

    // ---------------------------------------------------------------
    //  Persistence (rides in clientConfig.json, next to the theme)
    // ---------------------------------------------------------------

    @Override
    public String getJsonKey() { return "GuiStyle"; }

    @Override
    public JsonObject saveInfoToJson(JsonObject obj) {
        obj.addProperty("preset", preset.name());
        obj.addProperty("customFont", customFont);
        obj.addProperty("sounds", sounds);
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
        } catch (Exception e) {
            // An unknown preset name (a rename, a downgrade) should fall back to the default look
            // rather than take the client's whole client-config down with it.
            preset = Preset.GLASS;
        }
    }
}

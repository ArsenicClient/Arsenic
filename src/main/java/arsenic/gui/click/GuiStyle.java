package arsenic.gui.click;

import arsenic.main.Arsenic;
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
        BURN, DISSOLVE, GLITCH, FADE, SCALE, NONE
    }

    private static final Transition[] MENU_TRANSITIONS = {
            Transition.BURN, Transition.SCALE, Transition.FADE,
            Transition.DISSOLVE, Transition.GLITCH, Transition.NONE
    };

    public enum LogoMode { CLASSIC, MODERN }

    public enum Preset {

        CLEAN("Performance", "No shaders, no shadows",
                false, BgShader.AURORA, 0, 1f,
                false, 0f, 100f,
                false, 0, 0, 0,
                false, false, Transition.FADE, 0.15f, LogoMode.MODERN),

        GLASS("Glass", "Frosted panels over a live backdrop",
                true, BgShader.FIRESTORM, 45, 1f,
                true, 1f, 55f,
                true, 100, 100, 100,
                true, true, Transition.BURN, 0.7f, LogoMode.CLASSIC);

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
    private boolean showBlatant = true;
    /** The whole menu look: "Element" (default) or "Ocean". The loading screen reads it straight from the config file at start-up. */
    private String screenStyle = "Element";
    /** Key that opens the ClickGUI. 0 leaves it with no key. */
    private int clickGuiKey = com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT;
    /** Whether the client's custom menus are on: its main menu, ocean backgrounds, themed buttons and lists, container buttons and screen fades. */
    private boolean customMenus = true;
    /** GUI-tab override for the open/close animation; null means use the preset. */
    private Transition menuTransition;
    /** Extra zoom on the fixed GUI canvas: 1 is default, larger enlarges the whole menu. */
    private float clickGuiScale = 1f;
    /** Light M3 scheme instead of the dark one. */
    private boolean lightMode;
    /** GUI-tab background override: null follows the theme/preset, NONE turns it off, else that shader. */
    private BgChoice bgChoice;
    /** Background shader opacity in percent (the Glass preset's default is 45). */
    private int bgOpacity = 45;

    /** The GUI tab's background picker: the theme's own shader, off, or a fixed one. */
    public enum BgChoice {
        THEME(null), NONE(null),
        AURORA(BgShader.AURORA), STARFIELD(BgShader.STARFIELD), SYNTHWAVE(BgShader.SYNTHWAVE),
        CHROME(BgShader.CHROME), FIRESTORM(BgShader.FIRESTORM), CAUSTICS(BgShader.CAUSTICS),
        NEBULA(BgShader.NEBULA), ZIPPYZAPS(BgShader.ZIPPYZAPS);

        public final BgShader shader;

        BgChoice(BgShader shader) { this.shader = shader; }

        public String label() {
            if (this == THEME) return "Theme";
            if (this == NONE) return "Off";
            String n = name().toLowerCase(java.util.Locale.ROOT);
            return Character.toUpperCase(n.charAt(0)) + n.substring(1);
        }
    }

    public boolean isLightMode() { return lightMode; }

    public void setLightMode(boolean lightMode) { this.lightMode = lightMode; }

    public static boolean lightMode() { return INSTANCE.lightMode; }

    public BgChoice getBgChoice() { return bgChoice == null ? BgChoice.THEME : bgChoice; }

    public void cycleBgChoice() {
        BgChoice[] all = BgChoice.values();
        bgChoice = all[(getBgChoice().ordinal() + 1) % all.length];
        if (bgChoice == BgChoice.THEME)
            bgChoice = null;
    }

    public int getBgOpacity() { return bgOpacity; }

    public void setBgOpacity(int pct) { bgOpacity = MathUtils.clamp(pct, 0, 100); }

    public int getClickGuiKey() { return clickGuiKey; }

    public void setClickGuiKey(int clickGuiKey) { this.clickGuiKey = clickGuiKey; }

    public boolean isCustomMenus() { return customMenus; }

    public void setCustomMenus(boolean customMenus) { this.customMenus = customMenus; }

    /** For the game's hooks: when false they leave the vanilla menus alone. */
    public static boolean customMenus() { return INSTANCE.customMenus; }

    public Preset getPreset() { return preset; }

    public void setPreset(Preset preset) {
        if (preset != null) {
            this.preset = preset;
            this.menuTransition = null;
        }
    }

    public float getClickGuiScale() { return clickGuiScale; }

    public void setClickGuiScale(float scale) {
        clickGuiScale = MathUtils.clamp(scale, 0.7f, 1.8f);
    }

    public Transition getMenuTransition() {
        if (menuTransition != null)
            return menuTransition;
        return preset.transitionEnabled ? preset.transition : Transition.NONE;
    }

    public void setMenuTransition(Transition transition) {
        this.menuTransition = transition;
    }

    public void cycleMenuTransition() {
        Transition current = getMenuTransition();
        int index = 0;
        for (int i = 0; i < MENU_TRANSITIONS.length; i++)
            if (MENU_TRANSITIONS[i] == current) {
                index = (i + 1) % MENU_TRANSITIONS.length;
                break;
            }
        menuTransition = MENU_TRANSITIONS[index];
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



    public boolean isShowBlatant() { return showBlatant; }

    public void setShowBlatant(boolean showBlatant) { this.showBlatant = showBlatant; }

    /** Only Blatant has a ClickGUI toggle; every other tier is always listed. */
    public boolean isTierShown(arsenic.module.ModuleTier tier) {
        switch (tier) {
            case BLATANT: return showBlatant;
            default: return true;
        }
    }

    /** Flip a tier toggle. Turning one off also switches off that tier's modules so none keep running unseen. */
    public void toggleTier(arsenic.module.ModuleTier tier) {
        if (tier != arsenic.module.ModuleTier.BLATANT)
            return;
        showBlatant = !showBlatant;
        if (isTierShown(tier))
            return;
        for (arsenic.module.Module m : new java.util.ArrayList<>(Arsenic.getArsenic().getModuleManager().getModules()))
            if (m.isEnabled() && !isTierShown(m.getTier()))
                m.setEnabled(false);
    }
    private static Preset p() { return INSTANCE.preset; }

    public static boolean backgroundEnabled() {
        if (INSTANCE.bgChoice == BgChoice.NONE)
            return false;
        return INSTANCE.bgChoice != null || p().background;
    }

    public static String backgroundShader() {
        if (INSTANCE.bgChoice != null && INSTANCE.bgChoice.shader != null)
            return INSTANCE.bgChoice.shader.fsh;
        try {
            arsenic.gui.themes.Theme theme =
                    arsenic.main.Arsenic.getArsenic().getThemeManager().getCurrentTheme();
            if (theme != null && theme.getBgShader() != null)
                return theme.getBgShader().fsh;
        } catch (Exception ignored) {
        }
        return p().shader.fsh;
    }

    /** Shaders are tuned for dark menus, so light mode lets them through more softly. */
    public static float backgroundOpacity() {
        float base = INSTANCE.bgOpacity / 100f;
        return INSTANCE.lightMode ? base * 0.7f : base;
    }

    public static float backgroundSpeed() { return p().backgroundSpeed; }

    public static boolean scanlinesEnabled() { return p().scanlines; }

    /** The ClickGUI always opens and closes with the burn dissolve. */
    public static boolean transitionEnabled() { return true; }

    public static LogoMode logoMode() { return p().logo; }

    public static Transition transition() { return Transition.BURN; }

    public static int transitionTimeMs() { return 700; }

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

    private static long lastFrameNanos;
    private static float frameDt = 1f / 60f;

    /** Call once at the start of each GUI frame; it times the frame for {@link #scrollEase}. */
    public static void tickFrame() {
        long now = System.nanoTime();
        frameDt = lastFrameNanos == 0L ? 1f / 60f : Math.max(0f, Math.min(0.1f, (now - lastFrameNanos) / 1e9f));
        lastFrameNanos = now;
    }

    /**
     * How far scrolling closes on its target this frame. It used to be a flat 0.15 per frame, which only
     * glides at 60 FPS and all but snaps at high frame rates; this is the same feel at 60 FPS (about a
     * 100 ms glide) at any frame rate.
     */
    public static float scrollEase() { return 1f - (float) Math.exp(-frameDt * 9.75f); }

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
        obj.addProperty("showBlatant", showBlatant);
        obj.addProperty("screenStyle", screenStyle);
        obj.addProperty("clickGuiKey", clickGuiKey);
        obj.addProperty("bindFormat", "sdl");
        obj.addProperty("customMenus", customMenus);
        obj.addProperty("menuTransition", menuTransition == null ? "" : menuTransition.name());
        obj.addProperty("clickGuiScale", clickGuiScale);
        obj.addProperty("lightMode", lightMode);
        obj.addProperty("bgChoice", bgChoice == null ? "" : bgChoice.name());
        obj.addProperty("bgOpacity", bgOpacity);
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
            if (obj.has("showBlatant"))
                showBlatant = obj.get("showBlatant").getAsBoolean();
            if (obj.has("clickGuiKey")) {
                clickGuiKey = obj.get("clickGuiKey").getAsInt();
                // configs written by the 1.8 client stored LWJGL2 key codes
                if (!obj.has("bindFormat"))
                    clickGuiKey = arsenic.utils.io.Keys.fromLwjgl2(clickGuiKey);
            }
            if (obj.has("customMainMenu"))          // the old name for this setting
                customMenus = obj.get("customMainMenu").getAsBoolean();
            if (obj.has("customMenus"))
                customMenus = obj.get("customMenus").getAsBoolean();
            if (obj.has("menuTransition")) {
                String name = obj.get("menuTransition").getAsString();
                menuTransition = name.isEmpty() ? null : Transition.valueOf(name);
            }
            if (obj.has("clickGuiScale"))
                setClickGuiScale(obj.get("clickGuiScale").getAsFloat());
            if (obj.has("lightMode"))
                lightMode = obj.get("lightMode").getAsBoolean();
            if (obj.has("bgChoice")) {
                String name = obj.get("bgChoice").getAsString();
                bgChoice = name.isEmpty() ? null : BgChoice.valueOf(name);
            }
            if (obj.has("bgOpacity"))
                setBgOpacity(obj.get("bgOpacity").getAsInt());
        } catch (Exception e) {
            preset = Preset.GLASS;
        }
    }
}

package arsenic.gui.design;

import arsenic.main.Arsenic;
import arsenic.utils.font.FontRendererExtension;

public final class M3Icons {

    public static final String SEARCH = "\uE8B6";
    public static final String CHEVRON_RIGHT = "\uE5CC";
    public static final String EXPAND_MORE = "\uE5CF";
    public static final String COMBAT = "\uF889";
    public static final String MOVEMENT = "\uE566";
    public static final String PLAYER = "\uE7FD";
    public static final String RENDER = "\uE8F4";
    public static final String CLIENT = "\uE8B8";
    public static final String GUI = "\uE429";
    public static final String SETTINGS = "\uE8B8";
    public static final String VISUAL = "\uE40A";
    public static final String ADVANCED = "\uE429";
    public static final String CONFIGS = "\uE2C7";
    public static final String ADDONS = "\uE87B";
    public static final String ARROW_BACK = "\uE5C4";
    public static final String HUD = "\uE871";
    public static final String RELOAD = "\uE5D5";
    public static final String BLATANT = "\uEA0B";
    public static final String LOCAL = "\uE2C8";
    public static final String ONLINE = "\uE2BD";
    public static final String APPS = "\uE5C3";
    public static final String WARNING = "\uE002";
    public static final String ERROR = "\uE000";
    public static final String CHECK = "\uE5CA";

    public static final String BRAND = "\uEA4B";
    public static final String LOCATION = "\uE0C8";
    public static final String WIFI = "\uE63E";
    public static final String BATTERY = "\uE1A4";
    public static final String KEYBOARD = "\uE312";
    public static final String EFFECTS = "\uE65F";
    public static final String SHIELD = "\uE9E0";
    public static final String HEART = "\uE87D";
    public static final String TIMER = "\uE425";
    public static final String RADAR = "\uF04E";
    public static final String CAMERA = "\uE04B";
    public static final String TROPHY = "\uEA23";
    public static final String CROSSHAIR = "\uE55C";
    public static final String FOCUS = "\uE3B4";
    public static final String SIGNAL = "\uE202";
    public static final String UP = "\uE5D8";
    public static final String DOWN = "\uE5DB";
    public static final String DROP = "\uE798";
    public static final String FIRE = "\uEF55";
    public static final String SPEED = "\uE9E4";
    public static final String GRAPH = "\uE1B8";
    public static final String SCHEDULE = "\uE8B5";
    public static final String HEXAGON = "\uEB39";
    public static final String MEMORY = "\uE322";
    public static final String CODE = "\uE86F";
    public static final String GROUPS = "\uF233";
    public static final String FLAG = "\uE153";
    public static final String CHECKLIST = "\uE6B1";
    public static final String SECURITY = "\uE32A";
    public static final String HEALTH = "\uE1D5";
    public static final String CUBE = "\uE9FE";

    private M3Icons() {}

    public static FontRendererExtension<?> font() {
        return Arsenic.getInstance().getFonts().Material.getFontRendererExtension();
    }

    public static void draw(String glyph, float x, float y, int color) {
        FontRendererExtension<?> fr = font();
        fr.drawString(glyph, x, y, color);
        fr.resetScale();
    }

    public static void drawCentered(String glyph, float cx, float cy, int color) {
        FontRendererExtension<?> fr = font();
        fr.drawString(glyph, cx, cy, color, fr.CENTREX, fr.CENTREY);
        fr.resetScale();
    }

    public static void drawCentered(String glyph, float cx, float cy, int color, float scale) {
        FontRendererExtension<?> fr = font();
        fr.setScale(scale);
        fr.drawString(glyph, cx, cy, color, fr.CENTREX, fr.CENTREY);
        fr.resetScale();
    }

    public static void drawLeft(String glyph, float x, float cy, int color) {
        FontRendererExtension<?> fr = font();
        fr.drawString(glyph, x, cy, color, fr.CENTREY);
        fr.resetScale();
    }

    public static void drawLeft(String glyph, float x, float cy, int color, float scale) {
        FontRendererExtension<?> fr = font();
        fr.setScale(scale);
        fr.drawString(glyph, x, cy, color, fr.CENTREY);
        fr.resetScale();
    }

    public static String group(String name) {
        switch (name == null ? "" : name.toUpperCase(java.util.Locale.ROOT)) {
            case "VISUAL":
                return VISUAL;
            case "ADVANCED":
                return ADVANCED;
            default:
                return SETTINGS;
        }
    }

    public static String category(String name) {
        switch (name == null ? "" : name.toUpperCase(java.util.Locale.ROOT)) {
            case "COMBAT":
                return COMBAT;
            case "MOVEMENT":
                return MOVEMENT;
            case "PLAYER":
                return PLAYER;
            case "RENDER":
                return RENDER;
            case "CLIENT":
                return CLIENT;
            case "GUI":
                return GUI;
            case "CONFIGS":
                return CONFIGS;
            default:
                return APPS;
        }
    }
}

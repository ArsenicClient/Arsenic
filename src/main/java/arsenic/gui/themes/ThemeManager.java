package arsenic.gui.themes;

import arsenic.main.Arsenic;
import arsenic.utils.interfaces.IConfig;
import arsenic.utils.interfaces.ISerializable;
import arsenic.utils.java.FileUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ThemeManager implements IConfig<Theme>, ISerializable {
    private Theme currentTheme;
    private final List<Theme> themeList = new ArrayList<>();
    public int initialize() {
        System.out.println("initialized theme manager");
        loadConfig();
        if(getContentByJsonKey("Classic") == null) {
            Theme classic = new Theme("Classic", 0xFFDD425E, new Color(0xFFDD425E).darker().getRGB(), 0xFFFFFE, 0xFF494949);
            classic.setLogoPath("classic");
            themeList.add(classic);
        }
        // Void — deep purple with electric violet accent
        if (getContentByJsonKey("Void") == null) {
            Theme voidTheme = new Theme("Void", 0xFF7C3AED, new Color(0xFF7C3AED).darker().getRGB(), 0xFFF0EEFF, 0xFF1A1025);
            voidTheme.setLogoPath("void");
            themeList.add(voidTheme);
        }

        // Specter — cold ice blue, dark slate background
        if (getContentByJsonKey("Specter") == null) {
            Theme specter = new Theme("Specter", 0xFF38BDF8, new Color(0xFF38BDF8).darker().getRGB(), 0xFFE8F7FF, 0xFF0D1B2A);
            specter.setLogoPath("specter");
            themeList.add(specter);
        }

        // Ember — burnt orange with a near-black charcoal bg
        if (getContentByJsonKey("Ember") == null) {
            Theme ember = new Theme("Ember", 0xFFEA580C, new Color(0xFFEA580C).darker().getRGB(), 0xFFFFF7F0, 0xFF1C1008);
            ember.setLogoPath("ember");
            themeList.add(ember);
        }

        // Jade — muted emerald green, dark forest bg
        if (getContentByJsonKey("Jade") == null) {
            Theme jade = new Theme("Jade", 0xFF10B981, new Color(0xFF10B981).darker().getRGB(), 0xFFEDFDF5, 0xFF081A12);
            jade.setLogoPath("jade");
            themeList.add(jade);
        }

        // Obsidian — a pure monochrome / near-white accent on pure black
        if (getContentByJsonKey("Obsidian") == null) {
            Theme obsidian = new Theme("Obsidian", 0xFFE2E2E2, new Color(0xFFE2E2E2).darker().getRGB(), 0xFFFFFFFF, 0xFF0A0A0A);
            obsidian.setLogoPath("obsidian");
            themeList.add(obsidian);
        }

        // Sakura — soft cherry blossom pink, warm cream background
        if (getContentByJsonKey("Sakura") == null) {
            Theme sakura = new Theme("Sakura", 0xFFF472B6, new Color(0xFFF472B6).darker().getRGB(), 0xFFFFF0F7, 0xFF2D1520);
            sakura.setLogoPath("sakura");
            themeList.add(sakura);
        }

        // Toxin — acid green on deep dark, high contrast
        if (getContentByJsonKey("Toxin") == null) {
            Theme toxin = new Theme("Toxin", 0xFF84CC16, new Color(0xFF84CC16).darker().getRGB(), 0xFFF3FFE0, 0xFF0C1200);
            toxin.setLogoPath("toxin");
            themeList.add(toxin);
        }

        currentTheme = getContentByJsonKey("Classic");
        return themeList.size();
    }

    public Theme getCurrentTheme() {
        return currentTheme;
    }

    public static int getMainColor() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor(); }
    public static int getDarkerColor() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getDarkerColor(); }
    public static int getWhite() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getWhite(); }
    public static int getBlack() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getBlack(); }
    public static int getGradientColor() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getGradientColor(); }
    public static int getClickGuiBackground() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getClickGuiBackground(); }
    public static int getClickGuiSeparator() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getClickGuiSeparator(); }
    public static int getModuleBackground() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getModuleBackground(); }
    public static int getModuleHover() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getModuleHover(); }
    public static int getScrollbarTrack() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getScrollbarTrack(); }
    public static int getScrollbarThumb() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getScrollbarThumb(); }
    public static int getButtonBackground() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getButtonBackground(); }
    public static int getButtonCircleShadow() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getButtonCircleShadow(); }
    public static int getButtonCircleHighlight() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getButtonCircleHighlight(); }
    public static int getConfigsBackground() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getConfigsBackground(); }
    public static int getConfigsCard() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getConfigsCard(); }
    public static int getConfigsCardBorder() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getConfigsCardBorder(); }
    public static int getSeparator() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getSeparator(); }
    public static int getConfigsHoverBackground() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getConfigsHoverBackground(); }
    public static int getConfigsHoverBorder() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getConfigsHoverBorder(); }
    public static int getEnumBackground() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getEnumBackground(); }
    public static int getFolderBackground() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getFolderBackground(); }
    public static int getTextPrimary() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getTextPrimary(); }
    public static int getTextSecondary() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getTextSecondary(); }
    public static int getTextMuted() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getTextMuted(); }
    public static int getError() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getError(); }
    public static int getStatus() { return Arsenic.getArsenic().getThemeManager().getCurrentTheme().getStatus(); }

    public void setCurrentTheme(Theme theme) {
        currentTheme = theme;
        Arsenic.getArsenic().getConfigManager().saveClientConfig();
    }

    @Override
    public JsonObject saveInfoToJson(JsonObject obj) {
        obj.addProperty("currentTheme", currentTheme.getJsonKey());
        return obj;
    }

    @Override
    public void loadFromJson(JsonObject obj) {
        JsonElement jsonElement = obj.get("currentTheme");
        if(jsonElement != null) {
            Theme theme = getContentByJsonKey(jsonElement.getAsString());
            if(theme != null) {
                setCurrentTheme(theme);
            }
        }
    }

    @Override
    public String getJsonKey() {
        return "themeManager";
    }

    @Override
    public File getDirectory() {
        return new File(FileUtils.getArsenicFolderDirAsFile(), "themes.json");
    }

    @Override
    public Collection<Theme> getContents() {
        return themeList;
    }
}

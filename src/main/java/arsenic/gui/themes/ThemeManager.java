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
            themeList.add(classic);
        }
        // Void — deep purple with electric violet accent
        if (getContentByJsonKey("Void") == null) {
            Theme voidTheme = new Theme("Void", 0xFF7C3AED, new Color(0xFF7C3AED).darker().getRGB(), 0xFFF0EEFF, 0xFF1A1025);
            voidTheme.setClickGuiBackground(0xDD0F081A);
            voidTheme.setConfigsBackground(0xFF0D0612);
            voidTheme.setConfigsCard(0xFF160A21);
            voidTheme.setConfigsCardBorder(0xFF2D1445);
            voidTheme.setModuleBackground(new Color(22, 10, 33, 160).getRGB());
            voidTheme.setEnumBackground(new Color(11, 5, 16, 255).getRGB());
            voidTheme.setFolderBackground(new Color(11, 5, 16, 180).getRGB());
            themeList.add(voidTheme);
        }

        // Specter — cold ice blue, dark slate background
        if (getContentByJsonKey("Specter") == null) {
            Theme specter = new Theme("Specter", 0xFF38BDF8, new Color(0xFF38BDF8).darker().getRGB(), 0xFFE8F7FF, 0xFF0D1B2A);
            specter.setClickGuiBackground(0xDD050A12);
            specter.setConfigsBackground(0xFF040810);
            specter.setConfigsCard(0xFF0C1421);
            specter.setConfigsCardBorder(0xFF1E2D45);
            specter.setModuleBackground(new Color(12, 20, 33, 160).getRGB());
            specter.setEnumBackground(new Color(6, 10, 16, 255).getRGB());
            specter.setFolderBackground(new Color(6, 10, 16, 180).getRGB());
            themeList.add(specter);
        }

        // Ember — burnt orange with a near-black charcoal bg
        if (getContentByJsonKey("Ember") == null) {
            Theme ember = new Theme("Ember", 0xFFEA580C, new Color(0xFFEA580C).darker().getRGB(), 0xFFFFF7F0, 0xFF1C1008);
            ember.setClickGuiBackground(0xDD120A05);
            ember.setConfigsBackground(0xFF0F0804);
            ember.setConfigsCard(0xFF1D110C);
            ember.setConfigsCardBorder(0xFF382218);
            ember.setModuleBackground(new Color(29, 17, 12, 160).getRGB());
            ember.setEnumBackground(new Color(14, 8, 6, 255).getRGB());
            ember.setFolderBackground(new Color(14, 8, 6, 180).getRGB());
            themeList.add(ember);
        }

        // Jade — muted emerald green, dark forest bg
        if (getContentByJsonKey("Jade") == null) {
            Theme jade = new Theme("Jade", 0xFF10B981, new Color(0xFF10B981).darker().getRGB(), 0xFFEDFDF5, 0xFF081A12);
            jade.setClickGuiBackground(0xDD05120C);
            jade.setConfigsBackground(0xFF04100A);
            jade.setConfigsCard(0xFF0C2118);
            jade.setConfigsCardBorder(0xFF183D2D);
            jade.setModuleBackground(new Color(12, 33, 24, 160).getRGB());
            jade.setEnumBackground(new Color(6, 16, 12, 255).getRGB());
            jade.setFolderBackground(new Color(6, 16, 12, 180).getRGB());
            themeList.add(jade);
        }

        // Obsidian — a pure monochrome / near-white accent on pure black
        if (getContentByJsonKey("Obsidian") == null) {
            Theme obsidian = new Theme("Obsidian", 0xFFE2E2E2, new Color(0xFFE2E2E2).darker().getRGB(), 0xFFFFFFFF, 0xFF0A0A0A);
            obsidian.setClickGuiBackground(0xDD000000);
            obsidian.setConfigsBackground(0xFF000000);
            obsidian.setConfigsCard(0xFF0D0D0D);
            obsidian.setConfigsCardBorder(0xFF222222);
            obsidian.setModuleBackground(new Color(13, 13, 13, 160).getRGB());
            obsidian.setEnumBackground(new Color(0, 0, 0, 255).getRGB());
            obsidian.setFolderBackground(new Color(0, 0, 0, 180).getRGB());
            themeList.add(obsidian);
        }

        // Sakura — soft cherry blossom pink, warm cream background
        if (getContentByJsonKey("Sakura") == null) {
            Theme sakura = new Theme("Sakura", 0xFFF472B6, new Color(0xFFF472B6).darker().getRGB(), 0xFFFFF0F7, 0xFF2D1520);
            sakura.setClickGuiBackground(0xDD1A0D15);
            sakura.setConfigsBackground(0xFF150A11);
            sakura.setConfigsCard(0xFF2D1623);
            sakura.setConfigsCardBorder(0xFF552D45);
            sakura.setModuleBackground(new Color(45, 22, 35, 160).getRGB());
            sakura.setEnumBackground(new Color(22, 11, 17, 255).getRGB());
            sakura.setFolderBackground(new Color(22, 11, 17, 180).getRGB());
            themeList.add(sakura);
        }

        // Toxin — acid green on deep dark, high contrast
        if (getContentByJsonKey("Toxin") == null) {
            Theme toxin = new Theme("Toxin", 0xFF84CC16, new Color(0xFF84CC16).darker().getRGB(), 0xFFF3FFE0, 0xFF0C1200);
            toxin.setClickGuiBackground(0xDD0A1200);
            toxin.setConfigsBackground(0xFF081000);
            toxin.setConfigsCard(0xFF15210C);
            toxin.setConfigsCardBorder(0xFF2D3D18);
            toxin.setModuleBackground(new Color(21, 33, 12, 160).getRGB());
            toxin.setEnumBackground(new Color(10, 16, 6, 255).getRGB());
            toxin.setFolderBackground(new Color(10, 16, 6, 180).getRGB());
            themeList.add(toxin);
        }

        // Cloud — Clean light theme
        if (getContentByJsonKey("Cloud") == null) {
            Theme cloud = new Theme("Cloud", 0xFF3B82F6, new Color(0xFF3B82F6).darker().getRGB(), 0xFF1F2937, 0xFFF9FAFB);
            cloud.setClickGuiBackground(0xEEFFFFFF);
            cloud.setConfigsBackground(0xFFF3F4F6);
            cloud.setConfigsCard(0xFFFFFFFF);
            cloud.setConfigsCardBorder(0xFFE5E7EB);
            cloud.setModuleBackground(new Color(243, 244, 246, 255).getRGB());
            cloud.setModuleHover(new Color(0, 0, 0, 10).getRGB());
            cloud.setEnumBackground(new Color(255, 255, 255, 255).getRGB());
            cloud.setFolderBackground(new Color(243, 244, 246, 255).getRGB());
            cloud.setTextPrimary(0xFF111827);
            cloud.setTextSecondary(0xFF4B5563);
            cloud.setTextMuted(0xFF9CA3AF);
            cloud.setClickGuiSeparator(new Color(0, 0, 0, 20).getRGB());
            cloud.setScrollbarTrack(new Color(0, 0, 0, 15).getRGB());
            cloud.setScrollbarThumb(new Color(0, 0, 0, 40).getRGB());
            cloud.setButtonBackground(new Color(209, 213, 219, 234).getRGB());
            themeList.add(cloud);
        }

        // Monochrome — High contrast black on white refined
        if (getContentByJsonKey("Mono") == null) {
            Theme mono = new Theme("Mono", 0xFF000000, 0xFF333333, 0xFF000000, 0xFFFFFFFF);
            mono.setLogoPath("classic");
            mono.setClickGuiBackground(0xEEF9F9F9); // Very light grey base
            mono.setConfigsBackground(0xFFF2F2F2);
            mono.setConfigsCard(0xFFFFFFFF);
            mono.setConfigsCardBorder(0xFFE0E0E0);
            mono.setModuleBackground(new Color(255, 255, 255, 255).getRGB());
            mono.setModuleHover(new Color(0, 0, 0, 15).getRGB());
            mono.setEnumBackground(new Color(255, 255, 255, 255).getRGB());
            mono.setFolderBackground(new Color(248, 248, 248, 255).getRGB());
            mono.setTextPrimary(0xFF000000);
            mono.setTextSecondary(0xFF444444);
            mono.setTextMuted(0xFF888888);
            mono.setClickGuiSeparator(new Color(0, 0, 0, 30).getRGB());
            mono.setScrollbarTrack(new Color(0, 0, 0, 20).getRGB());
            mono.setScrollbarThumb(0xFF333333);
            mono.setButtonBackground(new Color(220, 220, 220, 234).getRGB());
            themeList.add(mono);
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

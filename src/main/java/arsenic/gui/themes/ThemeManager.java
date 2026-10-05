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
        if (getContentByJsonKey("Ocean") == null) {
            Theme ocean = new Theme("Ocean", 0xFF3B82F6, new Color(0xFF3B82F6).darker().getRGB(),
                    0xFFEAF2FF, 0xFF000000, true, 0xFF1D4ED8);
            ocean.setClickGuiBackground(0xE6000000);
            ocean.setClickGuiSeparator(new Color(59, 130, 246, 38).getRGB());
            ocean.setConfigsBackground(0xFF000000);
            ocean.setConfigsCard(0xFF04070D);
            ocean.setConfigsCardBorder(0xFF10203F);
            ocean.setConfigsHoverBackground(0xFF070D18);
            ocean.setConfigsHoverBorder(0xFF1E3A6B);
            ocean.setSeparator(new Color(16, 32, 63).getRGB());
            ocean.setModuleBackground(new Color(3, 6, 13, 175).getRGB());
            ocean.setEnumBackground(new Color(0, 0, 0, 255).getRGB());
            ocean.setFolderBackground(new Color(0, 0, 0, 180).getRGB());
            ocean.setButtonBackground(new Color(10, 16, 30, 234).getRGB());
            ocean.setTextPrimary(0xFFEAF2FF);
            ocean.setTextSecondary(0xFF8CA3C7);
            ocean.setTextMuted(0xFF3D4F73);
            ocean.setBgShader(arsenic.gui.click.GuiStyle.BgShader.STARFIELD);
            themeList.add(ocean);
        }

        if(getContentByJsonKey("Classic") == null) {
            Theme classic = new Theme("Classic", 0xFFDD425E, new Color(0xFFDD425E).darker().getRGB(), 0xFFFFFE, 0xFF494949);
            classic.setBgShader(arsenic.gui.click.GuiStyle.BgShader.AURORA);
            themeList.add(classic);
        }
        if (getContentByJsonKey("Void") == null) {
            Theme voidTheme = new Theme("Void", 0xFF7C3AED, new Color(0xFF7C3AED).darker().getRGB(), 0xFFF0EEFF, 0xFF1A1025);
            voidTheme.setClickGuiBackground(0xDD0F081A);
            voidTheme.setConfigsBackground(0xFF0D0612);
            voidTheme.setConfigsCard(0xFF160A21);
            voidTheme.setConfigsCardBorder(0xFF2D1445);
            voidTheme.setModuleBackground(new Color(22, 10, 33, 160).getRGB());
            voidTheme.setEnumBackground(new Color(11, 5, 16, 255).getRGB());
            voidTheme.setFolderBackground(new Color(11, 5, 16, 180).getRGB());
            voidTheme.setBgShader(arsenic.gui.click.GuiStyle.BgShader.NEBULA);
            themeList.add(voidTheme);
        }

        if (getContentByJsonKey("Specter") == null) {
            Theme specter = new Theme("Specter", 0xFF38BDF8, new Color(0xFF38BDF8).darker().getRGB(), 0xFFE8F7FF, 0xFF0D1B2A);
            specter.setClickGuiBackground(0xDD050A12);
            specter.setConfigsBackground(0xFF040810);
            specter.setConfigsCard(0xFF0C1421);
            specter.setConfigsCardBorder(0xFF1E2D45);
            specter.setModuleBackground(new Color(12, 20, 33, 160).getRGB());
            specter.setEnumBackground(new Color(6, 10, 16, 255).getRGB());
            specter.setFolderBackground(new Color(6, 10, 16, 180).getRGB());
            specter.setBgShader(arsenic.gui.click.GuiStyle.BgShader.ZIPPYZAPS);
            themeList.add(specter);
        }

        if (getContentByJsonKey("Ember") == null) {
            Theme ember = new Theme("Ember", 0xFFEA580C, new Color(0xFFEA580C).darker().getRGB(), 0xFFFFF7F0, 0xFF1C1008);
            ember.setClickGuiBackground(0xDD120A05);
            ember.setConfigsBackground(0xFF0F0804);
            ember.setConfigsCard(0xFF1D110C);
            ember.setConfigsCardBorder(0xFF382218);
            ember.setModuleBackground(new Color(29, 17, 12, 160).getRGB());
            ember.setEnumBackground(new Color(14, 8, 6, 255).getRGB());
            ember.setFolderBackground(new Color(14, 8, 6, 180).getRGB());
            ember.setBgShader(arsenic.gui.click.GuiStyle.BgShader.FIRESTORM);
            themeList.add(ember);
        }

        if (getContentByJsonKey("Jade") == null) {
            Theme jade = new Theme("Jade", 0xFF10B981, new Color(0xFF10B981).darker().getRGB(), 0xFFEDFDF5, 0xFF081A12);
            jade.setClickGuiBackground(0xDD05120C);
            jade.setConfigsBackground(0xFF04100A);
            jade.setConfigsCard(0xFF0C2118);
            jade.setConfigsCardBorder(0xFF183D2D);
            jade.setModuleBackground(new Color(12, 33, 24, 160).getRGB());
            jade.setEnumBackground(new Color(6, 16, 12, 255).getRGB());
            jade.setFolderBackground(new Color(6, 16, 12, 180).getRGB());
            jade.setBgShader(arsenic.gui.click.GuiStyle.BgShader.CAUSTICS);
            themeList.add(jade);
        }

        if (getContentByJsonKey("Obsidian") == null) {
            Theme obsidian = new Theme("Obsidian", 0xFFE2E2E2, new Color(0xFFE2E2E2).darker().getRGB(), 0xFFFFFFFF, 0xFF0A0A0A);
            obsidian.setClickGuiBackground(0xDD000000);
            obsidian.setConfigsBackground(0xFF000000);
            obsidian.setConfigsCard(0xFF0D0D0D);
            obsidian.setConfigsCardBorder(0xFF222222);
            obsidian.setModuleBackground(new Color(13, 13, 13, 160).getRGB());
            obsidian.setEnumBackground(new Color(0, 0, 0, 255).getRGB());
            obsidian.setFolderBackground(new Color(0, 0, 0, 180).getRGB());
            obsidian.setBgShader(arsenic.gui.click.GuiStyle.BgShader.STARFIELD);
            themeList.add(obsidian);
        }

        if (getContentByJsonKey("Sakura") == null) {
            Theme sakura = new Theme("Sakura", 0xFFF472B6, new Color(0xFFF472B6).darker().getRGB(), 0xFFFFF0F7, 0xFF2D1520);
            sakura.setClickGuiBackground(0xDD1A0D15);
            sakura.setConfigsBackground(0xFF150A11);
            sakura.setConfigsCard(0xFF2D1623);
            sakura.setConfigsCardBorder(0xFF552D45);
            sakura.setModuleBackground(new Color(45, 22, 35, 160).getRGB());
            sakura.setEnumBackground(new Color(22, 11, 17, 255).getRGB());
            sakura.setFolderBackground(new Color(22, 11, 17, 180).getRGB());
            sakura.setBgShader(arsenic.gui.click.GuiStyle.BgShader.SYNTHWAVE);
            themeList.add(sakura);
        }

        if (getContentByJsonKey("Toxin") == null) {
            Theme toxin = new Theme("Toxin", 0xFF84CC16, new Color(0xFF84CC16).darker().getRGB(), 0xFFF3FFE0, 0xFF0C1200);
            toxin.setClickGuiBackground(0xDD0A1200);
            toxin.setConfigsBackground(0xFF081000);
            toxin.setConfigsCard(0xFF15210C);
            toxin.setConfigsCardBorder(0xFF2D3D18);
            toxin.setModuleBackground(new Color(21, 33, 12, 160).getRGB());
            toxin.setEnumBackground(new Color(10, 16, 6, 255).getRGB());
            toxin.setFolderBackground(new Color(10, 16, 6, 180).getRGB());
            toxin.setBgShader(arsenic.gui.click.GuiStyle.BgShader.ZIPPYZAPS);
            themeList.add(toxin);
        }

        currentTheme = getContentByJsonKey("Classic");
        return themeList.size();
    }

    public Theme getCurrentTheme() {
        if (currentTheme == null && !getContents().isEmpty())
            currentTheme = getContents().iterator().next();
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
            String saved = jsonElement.getAsString();
            if (saved.equals("Midnight")) saved = "Ocean";
            Theme theme = getContentByJsonKey(saved);
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

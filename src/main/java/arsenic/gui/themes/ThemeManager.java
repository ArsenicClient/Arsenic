package arsenic.gui.themes;

import arsenic.utils.interfaces.IConfig;
import arsenic.utils.java.FileUtils;
import arsenic.utils.render.RenderUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ThemeManager implements IConfig<Theme> {
    private Theme currentTheme;
    private List<Theme> themeList = new ArrayList<>();
    private final File directory = FileUtils.getArsenicFolderDirAsFile();
    private final File config = new File(directory, "theme.json");

    public int init() {
        if(!directory.exists())
            directory.mkdirs();

        if(!config.exists())
            try {
                config.createNewFile();
            } catch(Exception e) {;}//ignored

        loadConfig();
        if(getContentByJsonKey("Arsenic") == null) {
            themeList.add(new Theme("Arsenic", 0xFF2ECC71, new Color(0xFF2ECC71).darker().getRGB(), 0xFFFFFE, 0xFF4B5F55));
        }
        if(getContentByJsonKey("Lilith") == null) {
            Theme lilith = new Theme("Lilith", 0xFFDD425E, new Color(0xFFDD425E).darker().getRGB(), 0xFFFFFE, 0xFF494949);
            lilith.setLogoPath(RenderUtils.getResourcePath("/assets/arsenic/logos/lilithlogo.png"));
            themeList.add(lilith);
        }
        if(getContentByJsonKey("Test") == null) {
            Theme test = new Theme("Test", 0xFFFFFFFF, new Color(0xFFFFFFFF, true).darker().getRGB(), 0xFFFFFFFF, 0xFF494949);
            themeList.add(test);
        }
        if(currentTheme == null) {
            setCurrentTheme(getContentByJsonKey("Arsenic"));
        }
        return themeList.size();
    }

    public Theme getCurrentTheme() {
        return currentTheme;
    }

    public void setCurrentTheme(Theme theme) {
        currentTheme = theme;
        saveConfig();
    }

    @Override
    public void loadFromJson(JsonObject obj) {
        IConfig.super.loadFromJson(obj);
        JsonElement jsonElement = obj.get("currentTheme");
        if(jsonElement != null) {
            Theme theme = getContentByJsonKey(jsonElement.getAsString());
            if(theme != null) {
                setCurrentTheme(theme);
            }
        }

    }

    @Override
    public JsonObject getJson(JsonObject data) {
        data.addProperty("currentTheme", currentTheme.getJsonKey());
        return IConfig.super.getJson(data);
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

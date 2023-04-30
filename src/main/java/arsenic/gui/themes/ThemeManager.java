package arsenic.gui.themes;

import arsenic.utils.interfaces.IConfig;
import arsenic.utils.java.FileUtils;
import arsenic.utils.render.RenderUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

public class ThemeManager implements IConfig<Theme> {
    private Theme currentTheme;
    private List<Theme> themeList = new ArrayList<>();
    public int init() {
        loadConfig();
        if(getContentByJsonKey("Arsenic") == null) {
            themeList.add(new Theme("Arsenic", 0xFF2ECC71, new Color(0xFF2ECC71).darker().getRGB(), 0xFFFFFE, 0xFF4B5F55));
        }
        if(getContentByJsonKey("Lilith") == null) {
            Theme lilith = new Theme("Lilith", 0xFFDD425E, new Color(0xFFDD425E).darker().getRGB(), 0xFFFFFE, 0xFF494949);
            lilith.setLogoPath("lilithlogo.png");
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
        System.out.println("loadfromjson");
        System.out.println(obj.entrySet().size());
        obj.entrySet().forEach(entry -> {
            System.out.println(entry.getKey());
            if(!entry.getKey().equalsIgnoreCase("currentTheme")) {
                Theme content = new Theme(entry.getKey());
                content.loadFromJson(entry.getValue().getAsJsonObject());
                themeList.add(content);
            }
        });
        JsonElement jsonElement = obj.get("currentTheme");
        if(jsonElement != null) {
            System.out.println(jsonElement.getAsString());
            Theme theme = getContentByJsonKey(jsonElement.getAsString());
            if(theme != null) {
                System.out.println(theme.getJsonKey());
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

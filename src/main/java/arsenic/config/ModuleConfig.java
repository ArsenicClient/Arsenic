package arsenic.config;

import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.utils.interfaces.IConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.util.Collection;
import java.util.Map;

public class ModuleConfig implements IConfig<Module> {

    private final File config;

    public ModuleConfig(File config) {
        this.config = config;
    }

    @Override
    public File getDirectory() {
        return config;
    }

    @Override
    public Collection<Module> getContents() {
        return Arsenic.getArsenic().getModuleManager().getModules();
    }

    /** Keeps the saved settings of modules that are not loaded right now (e.g. a disabled addon). */
    @Override
    public JsonObject getJson(JsonObject data) {
        IConfig.super.getJson(data);
        try (FileReader reader = new FileReader(config)) {
            for (Map.Entry<String, JsonElement> old : new JsonParser().parse(reader).getAsJsonObject().entrySet())
                if (!data.has(old.getKey()))
                    data.add(old.getKey(), old.getValue());
        } catch (Exception ignored) {
            // no previous file, or unreadable: nothing to keep
        }
        return data;
    }

}

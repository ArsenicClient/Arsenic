package arsenic.config;

import java.io.File;

import com.google.gson.JsonObject;

import arsenic.main.Arsenic;
import arsenic.module.Module;

public class ModuleConfig extends Config {

    public ModuleConfig(File config) {
        super(config);
    }

    @Override
    public void loadFromJson(JsonObject obj) {
        obj.entrySet().forEach(entry -> {
            Module m = Arsenic.getArsenic().getModuleManager().getModuleByName(entry.getKey());
            if (m != null)
                m.loadFromJson(entry.getValue().getAsJsonObject());
        });
    }

    @Override
    public JsonObject getJson(JsonObject obj) {
        Arsenic.getArsenic().getModuleManager().getModulesSet().forEach(module -> module.addToJson(obj));
        return obj;
    }

}

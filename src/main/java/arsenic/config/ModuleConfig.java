package arsenic.config;

import java.io.File;

import com.google.gson.JsonObject;

import arsenic.main.Arsenic;

public class ModuleConfig extends Config {

    public ModuleConfig(File config) {
        super(config);
    }

    @Override
    public void loadFromJson(JsonObject obj) {
        Arsenic.getArsenic().getModuleManager().getModules().forEach(module -> module.loadFromJson(obj.get(module.getName()).getAsJsonObject()));
    }

    @Override
    public JsonObject getJson(JsonObject obj) {
        Arsenic.getArsenic().getModuleManager().getModules().forEach(module -> module.addToJson(obj));
        return obj;
    }

}

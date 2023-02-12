package arsenic.config;

import arsenic.main.Arsenic;
import arsenic.module.Module;
import com.google.gson.JsonObject;

import java.io.File;

public class ModuleConfig extends Config {

    public ModuleConfig(File config) {
        super(config);
    }

    @Override
    public void loadFromJson(JsonObject obj) {
        obj.entrySet().forEach(entry -> {
            Module m = Arsenic.getArsenic().getModuleManager().getModuleByName(entry.getKey());
            if(m != null)
                m.loadFromJson(entry.getValue().getAsJsonObject());
        });
    }

    @Override
    public JsonObject getJson(JsonObject obj) {
        Arsenic.getArsenic().getModuleManager().getModulesSet().forEach(module -> module.addToJson(obj));
        return obj;
    }

}

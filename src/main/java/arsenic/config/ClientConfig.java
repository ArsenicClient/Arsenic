package arsenic.config;

import java.io.File;

import com.google.gson.JsonObject;

import arsenic.main.Arsenic;

public class ClientConfig extends Config {

    public ClientConfig(File config) {
        super(config);
    }

    @Override
    public void loadFromJson(JsonObject obj) {
        Arsenic.getArsenic().getConfigManager().loadConfig(obj.get("currentConfig").getAsString());
    }

    @Override
    public JsonObject getJson(JsonObject obj) {
        obj.addProperty("currentConfig", Arsenic.getArsenic().getConfigManager().getCurrentConfig().getName());
        return obj;
    }

}

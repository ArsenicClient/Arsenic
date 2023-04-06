package arsenic.config;

import java.io.File;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import arsenic.main.Arsenic;

public class ClientConfig extends Config {

    public ClientConfig(File config) {
        super(config);
    }

    @Override
    public void loadFromJson(JsonObject obj) {
        JsonElement jsonElement = obj.get("currentConfig");
        if(jsonElement != null)
            Arsenic.getArsenic().getConfigManager().loadConfig(jsonElement.getAsString());
    }

    @Override
    public JsonObject getJson(JsonObject obj) {
        obj.addProperty("currentConfig", Arsenic.getArsenic().getConfigManager().getCurrentConfig().getName());
        return obj;
    }

}

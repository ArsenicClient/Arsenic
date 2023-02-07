package arsenic.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public abstract class Config {

    protected File config;

    public Config(File config) {
        if (!config.exists()) {
            try {
                config.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        this.config = config;
    }

    public void loadConfig() {
        JsonObject data = new JsonObject();
        JsonParser jsonParser = new JsonParser();
        try (FileReader reader = new FileReader(config)) {
            JsonElement obj = jsonParser.parse(reader);
            data = obj.getAsJsonObject();
        } catch (JsonSyntaxException | ClassCastException | IOException | IllegalStateException e) {
            e.printStackTrace();
        }
        loadFromJson(data);
    }

    public void saveConfig() {
        JsonObject data = getJson(new JsonObject());
        try (PrintWriter out = new PrintWriter(new FileWriter(config))) {
            out.write(data.toString());
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteConfig() {
        if(config.exists()) {
            config.delete();
        }
    }

    public String getName() {
        return config.getName().replace(".json", "");
    }

    public abstract void loadFromJson(JsonObject obj);

    public abstract JsonObject getJson(JsonObject obj);

}

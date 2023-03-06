package arsenic.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.nio.file.Files;

public abstract class Config {

    protected File configDir;

    public Config(File config) {
        if (!config.exists()) {
            try {
                config.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException("Unable to create new config file");
            }
        }
        this.configDir = config;
    }

    public void loadConfig() {
        JsonObject data = new JsonObject();
        JsonParser jsonParser = new JsonParser();
        try (FileReader reader = new FileReader(configDir)) {
            JsonElement obj = jsonParser.parse(reader);
            data = obj.getAsJsonObject();
        } catch (JsonSyntaxException | ClassCastException | IOException | IllegalStateException e) {
            e.printStackTrace();
        }
        loadFromJson(data);
    }

    public void saveConfig() {
        JsonObject data = getJson(new JsonObject());
        try (PrintWriter out = new PrintWriter(new FileWriter(configDir))) {
            out.write(data.toString());
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteConfig() {
        try {
            Files.delete(configDir.toPath());
        } catch (IOException e) {
            //ignored
        }
    }

    public String getName() { return configDir.getName().replace(".json", ""); }

    public abstract void loadFromJson(JsonObject obj);
    public abstract JsonObject getJson(JsonObject obj);

}

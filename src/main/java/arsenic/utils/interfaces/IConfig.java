package arsenic.utils.interfaces;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/** A json file made of the json objects of its contents, each stored under the content's json key. */
public interface IConfig<T extends ISerializable & IContainable> extends IContainer<T> {

    Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    File getDirectory();

    default String getName() {
        String name = getDirectory().getName();
        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    }

    default void loadConfig() {
        JsonObject data = read(getDirectory());
        loadContentsFromJson(data == null ? new JsonObject() : data);
    }

    default void saveConfig() {
        if (!write(getDirectory(), getJson(new JsonObject())))
            System.err.println("Could not save " + getDirectory());
    }

    default void deleteConfig() {
        try {
            Files.deleteIfExists(getDirectory().toPath());
        } catch (IOException e) {
            System.err.println("Could not delete " + getDirectory() + ": " + e);
        }
    }

    /** Each content loads on its own, so one broken entry does not stop the rest from loading. */
    default void loadContentsFromJson(JsonObject obj) {
        for (T content : getContents()) {
            if (content == null)
                continue;
            JsonElement element = obj.get(content.getJsonKey());
            try {
                content.loadFromJson(element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject());
            } catch (Exception e) {
                System.err.println("Could not load " + content.getJsonKey() + " from " + getName() + ": " + e);
            }
        }
    }

    /** The contents' json, plus whatever the file already held under keys no content uses now (e.g. an addon that is not loaded). */
    default JsonObject getJson(JsonObject data) {
        for (T content : getContents()) {
            if (content == null)
                continue;
            try {
                content.addToJson(data);
            } catch (Exception e) {
                System.err.println("Could not save " + content.getJsonKey() + " to " + getName() + ": " + e);
            }
        }
        JsonObject old = read(getDirectory());
        if (old != null)
            for (Map.Entry<String, JsonElement> entry : old.entrySet())
                if (!data.has(entry.getKey()))
                    data.add(entry.getKey(), entry.getValue());
        return data;
    }

    default T getContentByJsonKey(String name) {
        for (T serializable : getContents()) {
            if (serializable != null && serializable.getJsonKey().equalsIgnoreCase(name))
                return serializable;
        }
        return null;
    }

    /** @return the file's json object, or null if it is missing or is not a json object. */
    static JsonObject read(File file) {
        if (file == null || !file.isFile())
            return null;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonElement element = new JsonParser().parse(reader);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Writes to a temporary file first and moves it over the old one, so a crash mid-save cannot leave a half-written config. */
    static boolean write(File file, JsonObject data) {
        Path target = file.toPath();
        Path temp = target.resolveSibling(file.getName() + ".tmp");
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory())
                parent.mkdirs();
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                PRETTY.toJson(data, writer);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            System.err.println("Could not write " + file + ": " + e);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
            return false;
        }
    }
}

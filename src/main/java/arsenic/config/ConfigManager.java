package arsenic.config;

import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.utils.interfaces.IConfig;
import arsenic.utils.interfaces.ISerializable;
import arsenic.utils.java.FileUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The configs in the Configs folder and which one is selected. The selected config is saved whenever a setting changes,
 * so switching configs never loses anything. Operations that can fail for a reason the user should see throw
 * {@link IllegalArgumentException} with a message meant for them.
 */
public class ConfigManager implements ISerializable {

    public static final String DEFAULT = "default";
    public static final int MAX_NAME_LENGTH = 32;
    private static final String ILLEGAL_CHARS = "\\/:*?\"<>|";
    private static final String[] RESERVED = { "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9" };

    private final File configDirectory = new File(FileUtils.getArsenicFolderDirAsString() + File.separator + "Configs");
    private final TreeMap<String, ModuleConfig> configs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    /** Each module's settings as it was built, so a config can leave things out and still load fully. */
    private final Map<String, JsonObject> defaults = new HashMap<>();
    private ModuleConfig currentConfig;
    private ClientConfig clientConfig;
    /** The config clientConfig.json says was selected, until it has been loaded. */
    private String selectedName;
    /** Saves are skipped while values are being applied, so a half-loaded state is never written. */
    private boolean applying;

    public synchronized int initialize() {
        if (!configDirectory.isDirectory())
            configDirectory.mkdirs();

        for (Module module : Arsenic.getArsenic().getModuleManager().getModules())
            rememberDefaults(module);

        clientConfig = new ClientConfig(new File(FileUtils.getArsenicFolderDirAsFile(), "clientConfig.json"));
        applying = true;
        try {
            clientConfig.loadConfig();
        } finally {
            applying = false;
        }

        reloadConfigs();
        if (configs.isEmpty())
            installDefaultConfig();

        ModuleConfig pick = selectedName == null ? null : configs.get(selectedName);
        if (pick == null)
            pick = configs.get(DEFAULT);
        if (pick == null && !configs.isEmpty())
            pick = configs.firstEntry().getValue();
        if (pick != null) {
            currentConfig = pick;
            pick.loadConfig();
        }
        saveClientConfig();

        Runtime.getRuntime().addShutdownHook(new Thread(this::saveConfig, "Arsenic config save"));
        return configs.size();
    }

    /* ---------------------------------------------------------------- loading */

    /** Remembers a module's settings as it is now. Only the first call per module counts, which is right after it was built. */
    public synchronized void rememberDefaults(Module module) {
        if (defaults.containsKey(module.getJsonKey()))
            return;
        try {
            defaults.put(module.getJsonKey(), module.saveInfoToJson(new JsonObject()));
        } catch (Exception e) {
            Arsenic.getArsenic().getLogger().info("Could not read the defaults of {}: {}", module.getName(), e);
        }
    }

    /** Applies a config's json to every module, filling in anything it leaves out with the module's defaults. */
    synchronized void applyModules(JsonObject data) {
        boolean was = applying;
        applying = true;
        try {
            for (Module module : Arsenic.getArsenic().getModuleManager().getModules())
                applyModule(module, data);
        } finally {
            applying = was;
        }
    }

    /** For a module added after the config was loaded (an addon): gives it the selected config's values. */
    public synchronized void applyToModule(Module module) {
        rememberDefaults(module);
        JsonObject data = currentConfig == null ? null : IConfig.read(currentConfig.getDirectory());
        boolean was = applying;
        applying = true;
        try {
            applyModule(module, data == null ? new JsonObject() : data);
        } finally {
            applying = was;
        }
    }

    private void applyModule(Module module, JsonObject data) {
        JsonElement saved = data.get(module.getJsonKey());
        JsonObject merged = merge(defaults.get(module.getJsonKey()),
                saved != null && saved.isJsonObject() ? saved.getAsJsonObject() : null);
        if (merged == null)
            return;
        try {
            module.loadFromJson(merged);
        } catch (Exception e) {
            Arsenic.getArsenic().getLogger().info("Could not load {} from the config: {}", module.getName(), e);
        }
    }

    /** A copy of base with every value of over on top; objects are merged key by key. */
    private static JsonObject merge(JsonObject base, JsonObject over) {
        if (base == null)
            return over;
        JsonObject result = (JsonObject) copy(base);
        if (over == null)
            return result;
        for (Map.Entry<String, JsonElement> entry : over.entrySet()) {
            JsonElement mine = result.get(entry.getKey());
            if (mine != null && mine.isJsonObject() && entry.getValue().isJsonObject())
                result.add(entry.getKey(), merge(mine.getAsJsonObject(), entry.getValue().getAsJsonObject()));
            else
                result.add(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static JsonElement copy(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject result = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet())
                result.add(entry.getKey(), copy(entry.getValue()));
            return result;
        }
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement e : element.getAsJsonArray())
                result.add(copy(e));
            return result;
        }
        return element; // primitives and null are immutable
    }

    /* ---------------------------------------------------------------- the list */

    /** Re-reads the Configs folder. */
    public synchronized void reloadConfigs() {
        configs.clear();
        File[] files = configDirectory.listFiles();
        if (files != null)
            for (File file : files)
                if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
                    ModuleConfig config = new ModuleConfig(file);
                    configs.put(config.getName(), config);
                }
        // keep the selected config's object so getCurrentConfig() stays the same one
        if (currentConfig != null) {
            if (configs.containsKey(currentConfig.getName()))
                configs.put(currentConfig.getName(), currentConfig);
            else if (currentConfig.exists())
                configs.put(currentConfig.getName(), currentConfig);
        }
    }

    public synchronized List<String> getConfigList() {
        return new ArrayList<>(configs.keySet());
    }

    public synchronized Collection<ModuleConfig> getConfigs() {
        return new ArrayList<>(configs.values());
    }

    public synchronized boolean exists(String name) {
        return name != null && configs.containsKey(name);
    }

    public ModuleConfig getCurrentConfig() {
        return currentConfig;
    }

    public String getCurrentName() {
        return currentConfig == null ? "" : currentConfig.getName();
    }

    public boolean isCurrent(String name) {
        return currentConfig != null && currentConfig.getName().equalsIgnoreCase(name);
    }

    public File getConfigDirectory() {
        return configDirectory;
    }

    public ClientConfig getClientConfig() {
        return clientConfig;
    }

    /* ---------------------------------------------------------------- saving */

    /** Saves the selected config and clientConfig.json. Called after every change. */
    public synchronized void saveConfig() {
        if (applying)
            return;
        if (currentConfig != null)
            currentConfig.saveConfig();
        saveClientConfig();
    }

    public synchronized void saveClientConfig() {
        if (applying || clientConfig == null)
            return;
        clientConfig.saveConfig();
    }

    /* ---------------------------------------------------------------- operations */

    /** Switches to another config, saving the one that was selected first. @return false if there is no config with that name */
    public synchronized boolean loadConfig(String name) {
        ModuleConfig target = configs.get(name);
        if (target == null) {
            reloadConfigs();
            target = configs.get(name);
        }
        if (target == null || !target.exists())
            return false;
        saveConfig();
        currentConfig = target;
        target.loadConfig();
        saveClientConfig();
        return true;
    }

    /**
     * Saves the current settings under a name and selects that config.
     * @param overwrite whether an existing config with that name may be replaced
     * @return the name it was saved as
     */
    public synchronized String saveAs(String rawName, boolean overwrite) {
        String name = validateName(rawName);
        ModuleConfig existing = configs.get(name);
        if (existing != null && !overwrite && !isCurrent(name))
            throw new IllegalArgumentException("A config named \"" + existing.getName() + "\" already exists");

        JsonObject data = currentJson();
        ModuleConfig target = existing != null ? existing : new ModuleConfig(fileFor(name));
        if (!IConfig.write(target.getDirectory(), data))
            throw new IllegalArgumentException("Could not write " + target.getDirectory().getName());
        configs.put(target.getName(), target);
        currentConfig = target;
        saveClientConfig();
        return target.getName();
    }

    public synchronized void deleteConfig(String name) {
        ModuleConfig config = configs.get(name);
        if (config == null)
            throw new IllegalArgumentException("There is no config named \"" + name + "\"");
        if (config == currentConfig || isCurrent(name))
            throw new IllegalArgumentException("You can't delete the config you are using");
        config.deleteConfig();
        if (config.exists())
            throw new IllegalArgumentException("Could not delete " + config.getDirectory().getName());
        configs.remove(name);
    }

    /** @return the new name */
    public synchronized String renameConfig(String oldName, String rawNewName) {
        ModuleConfig config = configs.get(oldName);
        if (config == null)
            throw new IllegalArgumentException("There is no config named \"" + oldName + "\"");
        String newName = validateName(rawNewName);
        if (newName.equals(config.getName()))
            return newName;
        if (configs.containsKey(newName) && !newName.equalsIgnoreCase(config.getName()))
            throw new IllegalArgumentException("A config named \"" + newName + "\" already exists");

        boolean wasCurrent = config == currentConfig;
        if (wasCurrent)
            saveConfig();
        File target = fileFor(newName);
        try {
            if (newName.equalsIgnoreCase(config.getName())) {
                // only the case changes: on Windows that is the same file, so go through a temporary name
                File temp = new File(configDirectory, newName + ".rename.tmp");
                Files.move(config.getDirectory().toPath(), temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(config.getDirectory().toPath(), target.toPath());
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not rename: " + e.getMessage());
        }
        configs.remove(config.getName());
        ModuleConfig renamed = new ModuleConfig(target);
        configs.put(newName, renamed);
        if (wasCurrent) {
            currentConfig = renamed;
            saveClientConfig();
        }
        return newName;
    }

    /** Puts every module of the selected config back to its defaults (all off, no binds) and saves it. */
    public synchronized void resetCurrentConfig() {
        applyModules(new JsonObject());
        saveConfig();
    }

    /** The config as json text, for the clipboard. The selected one is saved first, so it is up to date. */
    public synchronized String exportConfig(String name) {
        ModuleConfig config = name == null ? currentConfig : configs.get(name);
        if (config == null)
            throw new IllegalArgumentException("There is no config named \"" + name + "\"");
        JsonObject data = config == currentConfig ? currentJson() : IConfig.read(config.getDirectory());
        if (data == null)
            throw new IllegalArgumentException(config.getName() + " could not be read");
        return IConfig.PRETTY.toJson(data);
    }

    /**
     * Adds a config from json text (clipboard or download). It must be an Arsenic config: an object naming at least one module.
     * @param replace whether a config with the same name is replaced; if not, the new one gets a free name
     * @return the name it was saved as
     */
    public synchronized String importConfig(String json, String preferredName, boolean replace) {
        JsonObject data;
        try {
            JsonElement parsed = new JsonParser().parse(json == null ? "" : json.trim());
            data = parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Exception e) {
            data = null;
        }
        if (data == null)
            throw new IllegalArgumentException("That is not a config (not valid json)");
        boolean known = false;
        for (Module module : Arsenic.getArsenic().getModuleManager().getModules())
            if (data.has(module.getJsonKey()) && data.get(module.getJsonKey()).isJsonObject()) {
                known = true;
                break;
            }
        if (!known)
            throw new IllegalArgumentException("That is not an Arsenic config (no modules in it)");

        String base = sanitizeName(preferredName);
        if (base.isEmpty())
            base = "imported";
        String name = base;
        // the same config again (e.g. downloaded twice) is not saved a second time
        ModuleConfig same = configs.get(name);
        if (same != null && data.equals(IConfig.read(same.getDirectory())))
            return same.getName();
        if (isCurrent(name) || (!replace && configs.containsKey(name)))
            name = freeName(base);

        File file = configs.containsKey(name) ? configs.get(name).getDirectory() : fileFor(name);
        if (!IConfig.write(file, data))
            throw new IllegalArgumentException("Could not write " + file.getName());
        ModuleConfig config = new ModuleConfig(file);
        configs.put(config.getName(), config);
        return config.getName();
    }

    /* ---------------------------------------------------------------- names */

    /** Removes what a file name cannot hold. May return an empty string. */
    public static String sanitizeName(String raw) {
        if (raw == null)
            return "";
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toCharArray())
            if (c >= 32 && c != 127 && ILLEGAL_CHARS.indexOf(c) < 0 && c != '§')
                sb.append(c);
        String name = sb.toString().replaceAll("\\s+", " ").trim();
        while (name.endsWith(".") || name.endsWith(" "))
            name = name.substring(0, name.length() - 1);
        if (name.toLowerCase(Locale.ROOT).endsWith(".json"))
            name = name.substring(0, name.length() - 5).trim();
        if (name.length() > MAX_NAME_LENGTH)
            name = name.substring(0, MAX_NAME_LENGTH).trim();
        for (String reserved : RESERVED)
            if (name.equalsIgnoreCase(reserved))
                name = name + "_";
        return name;
    }

    private static String validateName(String raw) {
        String name = sanitizeName(raw);
        if (name.isEmpty())
            throw new IllegalArgumentException("Enter a name for the config");
        return name;
    }

    /** base, or base 2, base 3, ... whichever is free. */
    public synchronized String freeName(String base) {
        if (!configs.containsKey(base))
            return base;
        for (int i = 2; ; i++) {
            String suffix = " " + i;
            String trimmed = base.length() + suffix.length() > MAX_NAME_LENGTH
                    ? base.substring(0, MAX_NAME_LENGTH - suffix.length()) : base;
            if (!configs.containsKey(trimmed + suffix))
                return trimmed + suffix;
        }
    }

    private File fileFor(String name) {
        return new File(configDirectory, name + ".json");
    }

    /** The current settings, plus whatever the selected file holds for modules that are not loaded. */
    private JsonObject currentJson() {
        if (currentConfig != null)
            return currentConfig.getJson(new JsonObject());
        return new ModuleConfig(fileFor(DEFAULT)).getJson(new JsonObject());
    }

    private void installDefaultConfig() {
        File file = fileFor(DEFAULT);
        try (InputStream in = getClass().getResourceAsStream("/assets/arsenic/configs/default.json")) {
            if (in != null) {
                JsonElement parsed = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8));
                if (parsed.isJsonObject() && IConfig.write(file, parsed.getAsJsonObject())) {
                    configs.put(DEFAULT, new ModuleConfig(file));
                    return;
                }
            }
        } catch (Exception e) {
            Arsenic.getArsenic().getLogger().info("Could not copy the built-in default config: {}", e.toString());
        }
        // no built-in default: save every module as it is now (its defaults)
        IConfig.write(file, new ModuleConfig(file).getJson(new JsonObject()));
        configs.put(DEFAULT, new ModuleConfig(file));
    }

    /* ---------------------------------------------------------------- clientConfig.json */

    @Override
    public void loadFromJson(JsonObject obj) {
        JsonElement name = obj.get("currentConfig");
        selectedName = name != null && name.isJsonPrimitive() ? name.getAsString() : null;
    }

    @Override
    public JsonObject saveInfoToJson(JsonObject obj) {
        String name = currentConfig != null ? currentConfig.getName() : selectedName;
        if (name != null)
            obj.addProperty("currentConfig", name);
        return obj;
    }

    @Override
    public String getJsonKey() {
        return "config";
    }
}

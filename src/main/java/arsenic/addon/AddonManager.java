package arsenic.addon;

import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleManager;
import arsenic.utils.java.FileUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.launchwrapper.Launch;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads modules from .java files in the Arsenic/addons folder. Sources are compiled on the fly (see
 * {@link AddonCompiler}), so addons never need to be obfuscated or packaged by hand.
 */
public final class AddonManager {

    private final File directory;
    private final List<Module> loaded = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    public AddonManager() {
        this(new File(FileUtils.getArsenicFolderDirAsString(), "addons"));
    }

    public AddonManager(File directory) {
        this.directory = directory;
        // FML's remapper rewrites every class it loads with an ASM that corrupts the stack map frames of the
        // compiler's larger methods (VerifyError), so keep the embedded compiler away from the transformers.
        try {
            Launch.classLoader.addTransformerExclusion("org.eclipse.jdt.");
        } catch (Throwable t) {
            org.apache.logging.log4j.LogManager.getLogger("Arsenic").warn("Could not exclude the addon compiler from class transformation", t);
        }
    }

    public File getDirectory() {
        return directory;
    }

    public List<Module> getLoadedModules() {
        return Collections.unmodifiableList(loaded);
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public enum State { ENABLED, DISABLED, AVAILABLE }

    /** One addon as the addon manager shows it. */
    public static final class Info {
        public final String name;
        public final State state;
        public final String description;
        /** The pack this addon belongs to, or null for a loose addon. */
        public final AddonCatalog.PackMeta pack;

        Info(String name, State state, String description, AddonCatalog.PackMeta pack) {
            this.name = name;
            this.state = state;
            this.description = description;
            this.pack = pack;
        }
    }

    /** One addon pack as the addon manager shows it. */
    public static final class PackInfo {
        public final AddonCatalog.PackMeta meta;
        /** False for a bundled pack that has not been put into the packs folder yet. */
        public final boolean installed;
        public final List<Info> addons;

        PackInfo(AddonCatalog.PackMeta meta, boolean installed, List<Info> addons) {
            this.meta = meta;
            this.installed = installed;
            this.addons = addons;
        }

        public int enabledCount() {
            int n = 0;
            for (Info info : addons)
                if (info.state == State.ENABLED)
                    n++;
            return n;
        }

        public boolean allEnabled() {
            return !addons.isEmpty() && enabledCount() == addons.size();
        }
    }

    private final AddonCatalog catalog = AddonCatalog.load();

    public AddonCatalog getCatalog() {
        return catalog;
    }

    private File packsDirectory() {
        return new File(directory, "packs");
    }

    private File implDirectory(String packId) {
        return new File(new File(packsDirectory(), packId), "impl");
    }

    private static File on(File dir, String name) {
        return new File(dir, name + ".java");
    }

    private static File off(File dir, String name) {
        return new File(dir, name + ".java.disabled");
    }

    private static void write(File file, String text) throws java.io.IOException {
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Keeps the addons folder in step with the bundled defaults. The first time a default addon or pack addon is seen
     * it is installed (loose addons and packs flagged autoInstall enabled, the rest as disabled files); anything the
     * user later deletes or renames is left alone. On every later start, an installed default whose bundled source
     * changed (a new client version) is updated in place, keeping its enabled or disabled state. A copy the user edited
     * is never overwritten, and a copy whose history is unknown is overwritten only after a .bak backup is written.
     * Addons new to an already installed pack are added.
     */
    public synchronized void installDefaults() {
        try {
            directory.mkdirs();
            File marker = new File(directory, ".defaults");
            Set<String> seen = new HashSet<>();
            if (marker.exists())
                seen.addAll(Files.readAllLines(marker.toPath(), StandardCharsets.UTF_8));
            File hashFile = new File(directory, ".defaults-hashes");
            Map<String, String> hashes = readHashes(hashFile);
            int seenBefore = seen.size();
            Map<String, String> hashesBefore = new HashMap<>(hashes);

            for (AddonCatalog.Entry entry : catalog.addons) {
                String source = catalog.source(entry.file);
                if (source == null)
                    continue;
                File existing = existing(directory, entry.file);
                if (existing == null) {
                    if (seen.add(entry.file)) {
                        write(off(directory, entry.file), source);
                        hashes.put("java:" + entry.file, hash(source));
                    }
                } else {
                    seen.add(entry.file);
                    refresh(existing, source, "java:" + entry.file, hashes);
                }
            }
            for (AddonCatalog.PackMeta meta : catalog.bundledPacks()) {
                boolean firstSeen = seen.add("pack:" + meta.id);
                if (firstSeen && !new File(packsDirectory(), meta.id).exists())
                    extractBundledPack(meta, meta.autoInstall);
                updatePack(meta, seen, hashes);
            }

            if (seen.size() != seenBefore)
                Files.write(marker.toPath(), seen.stream().sorted().collect(Collectors.toList()), StandardCharsets.UTF_8);
            if (!hashes.equals(hashesBefore))
                Files.write(hashFile.toPath(), hashes.entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.toList()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Arsenic.getArsenic().getLogger().error("Could not install the default addons", e);
        }
    }

    /** Brings an installed pack up to the bundled version: its pack.json, its addons, and addons the pack gained. */
    private void updatePack(AddonCatalog.PackMeta meta, Set<String> seen, Map<String, String> hashes) throws java.io.IOException {
        File packDir = new File(packsDirectory(), meta.id);
        if (!packDir.isDirectory())
            return;
        String metaSource = catalog.packMetaSource(meta.id);
        File packJson = new File(packDir, "pack.json");
        if (metaSource != null) {
            if (packJson.isFile())
                refresh(packJson, metaSource, "pack:" + meta.id + "/pack.json", hashes);
            else
                write(packJson, metaSource);
        }
        File impl = implDirectory(meta.id);
        for (String addon : meta.addons.keySet()) {
            String source = catalog.packSource(meta.id, addon);
            if (source == null)
                continue;
            String key = "pack:" + meta.id + "/" + addon;
            File existing = existing(findDirectory(impl, addon), addon);
            boolean unseen = seen.add("packaddon:" + meta.id + "/" + addon);
            if (existing != null) {
                refresh(existing, source, key, hashes);
            } else if (unseen) {
                write(meta.autoInstall ? on(impl, addon) : off(impl, addon), source);
                hashes.put(key, hash(source));
            }
        }
    }

    /**
     * Overwrites an installed default with the bundled source when they differ and it is safe to: the file must be
     * either untouched since we wrote it (its hash matches the one recorded) or of unknown origin, in which case it is
     * backed up first. A file the user edited is left alone.
     */
    private void refresh(File target, String bundled, String key, Map<String, String> hashes) {
        try {
            String disk = new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8);
            String diskHash = hash(disk), bundledHash = hash(bundled);
            String recorded = hashes.get(key);
            if (diskHash.equals(bundledHash)) {
                hashes.put(key, bundledHash);
                return;
            }
            if (recorded != null && !recorded.equals(diskHash))
                return; // edited by the user
            if (recorded == null)
                Files.write(new File(target.getParentFile(), target.getName() + ".bak").toPath(),
                        disk.getBytes(StandardCharsets.UTF_8));
            Files.write(target.toPath(), bundled.getBytes(StandardCharsets.UTF_8));
            hashes.put(key, bundledHash);
            Arsenic.getArsenic().getLogger().info("Updated default addon {}", target.getName());
        } catch (Exception e) {
            Arsenic.getArsenic().getLogger().error("Could not update default addon " + target.getName(), e);
        }
    }

    private static File existing(File dir, String name) {
        if (on(dir, name).exists())
            return on(dir, name);
        return off(dir, name).exists() ? off(dir, name) : null;
    }

    private static Map<String, String> readHashes(File file) {
        Map<String, String> map = new HashMap<>();
        try {
            if (file.isFile())
                for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                    int i = line.lastIndexOf('=');
                    if (i > 0)
                        map.put(line.substring(0, i), line.substring(i + 1));
                }
        } catch (Exception ignored) {
            // no history: every default is treated as of unknown origin
        }
        return map;
    }

    private static String hash(String text) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Copies a bundled pack into the packs folder, with its addons enabled or as disabled files. */
    private void extractBundledPack(AddonCatalog.PackMeta meta, boolean enabled) throws java.io.IOException {
        String metaSource = catalog.packMetaSource(meta.id);
        if (metaSource == null)
            return;
        write(new File(new File(packsDirectory(), meta.id), "pack.json"), metaSource);
        File impl = implDirectory(meta.id);
        impl.mkdirs();
        for (String addon : meta.addons.keySet()) {
            if (on(impl, addon).exists() || off(impl, addon).exists())
                continue;
            // Earlier versions kept pack addons loose in the addons folder: move such a file into the pack, keeping
            // whether it was enabled and any edits, so the module is not loaded twice.
            if (on(directory, addon).exists()) {
                Files.move(on(directory, addon).toPath(), on(impl, addon).toPath());
                continue;
            }
            if (off(directory, addon).exists()) {
                Files.move(off(directory, addon).toPath(), off(impl, addon).toPath());
                continue;
            }
            String source = catalog.packSource(meta.id, addon);
            if (source != null)
                write(enabled ? on(impl, addon) : off(impl, addon), source);
        }
    }

    /**
     * Packs can be dropped into the packs folder as a .zip containing pack.json and an impl folder. They are
     * unpacked into a folder of the same name (so they can be edited) and the zip is renamed to .zip.imported.
     */
    private void importPackZips() {
        File[] zips = packsDirectory().listFiles((dir, name) -> name.endsWith(".zip"));
        if (zips == null)
            return;
        for (File zipFile : zips) {
            String id = zipFile.getName().substring(0, zipFile.getName().length() - 4).replaceAll("[^A-Za-z0-9_-]", "_");
            File dest = new File(packsDirectory(), id);
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile)) {
                String prefix = null;
                for (java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zip.entries(); en.hasMoreElements(); ) {
                    String entry = en.nextElement().getName();
                    if (entry.equals("pack.json") || entry.endsWith("/pack.json")) {
                        String p = entry.substring(0, entry.length() - "pack.json".length());
                        if (prefix == null || p.length() < prefix.length())
                            prefix = p;
                    }
                }
                if (prefix == null) {
                    errors.add(zipFile.getName() + ": not an addon pack (no pack.json inside)");
                    continue;
                }
                if (dest.exists()) {
                    errors.add(zipFile.getName() + ": a pack called " + id + " is already installed");
                    continue;
                }
                String canonicalDest = dest.getCanonicalPath() + File.separator;
                for (java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zip.entries(); en.hasMoreElements(); ) {
                    java.util.zip.ZipEntry entry = en.nextElement();
                    if (entry.isDirectory() || !entry.getName().startsWith(prefix))
                        continue;
                    File out = new File(dest, entry.getName().substring(prefix.length()));
                    if (!out.getCanonicalPath().startsWith(canonicalDest))
                        continue;
                    out.getParentFile().mkdirs();
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } catch (Exception e) {
                errors.add(zipFile.getName() + ": could not import (" + e + ")");
                continue;
            }
            zipFile.renameTo(new File(zipFile.getParentFile(), zipFile.getName() + ".imported"));
        }
    }

    /** Loose addons: every .java / .java.disabled in the addons folder plus bundled ones that are not in it. */
    public synchronized List<Info> listLoose() {
        Map<String, Info> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        File[] files = directory.listFiles();
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                State state = name.endsWith(".java") ? State.ENABLED : name.endsWith(".java.disabled") ? State.DISABLED : null;
                if (state == null || name.startsWith("."))
                    continue;
                String base = name.substring(0, name.indexOf(".java"));
                AddonCatalog.Entry entry = catalog.entry(base);
                result.put(base, new Info(base, state, entry != null ? entry.description : describe(f), null));
            }
        }
        for (AddonCatalog.Entry entry : catalog.addons)
            if (!result.containsKey(entry.file) && catalog.source(entry.file) != null)
                result.put(entry.file, new Info(entry.file, State.AVAILABLE, entry.description, null));
        return new ArrayList<>(result.values());
    }

    /** Packs in the packs folder plus bundled ones that have not been put there. */
    public synchronized List<PackInfo> listPacks() {
        List<PackInfo> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        File[] dirs = packsDirectory().listFiles(File::isDirectory);
        if (dirs != null) {
            Arrays.sort(dirs, Comparator.comparing(f -> f.getName().toLowerCase()));
            for (File dir : dirs) {
                AddonCatalog.PackMeta meta = readPackMeta(dir);
                seen.add(meta.id);
                result.add(new PackInfo(meta, true, listPackAddons(meta, implDirectory(dir.getName()))));
            }
        }
        for (AddonCatalog.PackMeta meta : catalog.bundledPacks()) {
            if (seen.contains(meta.id))
                continue;
            List<Info> addons = new ArrayList<>();
            for (Map.Entry<String, String> a : meta.addons.entrySet())
                addons.add(new Info(a.getKey(), State.AVAILABLE, a.getValue(), meta));
            result.add(new PackInfo(meta, false, addons));
        }
        return result;
    }

    private AddonCatalog.PackMeta readPackMeta(File dir) {
        AddonCatalog.PackMeta meta = null;
        try {
            File json = new File(dir, "pack.json");
            if (json.isFile())
                meta = AddonCatalog.parsePackMeta(new String(Files.readAllBytes(json.toPath()), StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // fall back to the folder name below
        }
        if (meta == null) {
            meta = new AddonCatalog.PackMeta();
            meta.id = dir.getName();
            meta.name = dir.getName();
            meta.description = "Pack without a valid pack.json.";
        }
        if (meta.name == null)
            meta.name = meta.id;
        return meta;
    }

    private List<Info> listPackAddons(AddonCatalog.PackMeta meta, File impl) {
        Map<String, Info> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        try (Stream<Path> files = Files.isDirectory(impl.toPath()) ? Files.walk(impl.toPath()) : Stream.<Path>empty()) {
            for (Path p : files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                String name = p.getFileName().toString();
                State state = name.endsWith(".java") ? State.ENABLED : name.endsWith(".java.disabled") ? State.DISABLED : null;
                if (state == null)
                    continue;
                String base = name.substring(0, name.indexOf(".java"));
                String description = meta.addons.containsKey(base) ? meta.addons.get(base) : describe(p.toFile());
                result.put(base, new Info(base, state, description, meta));
            }
        } catch (java.io.IOException ignored) {
            // an unreadable impl folder just lists nothing
        }
        return new ArrayList<>(result.values());
    }

    /** Turns one addon on or off by renaming its file; a bundled loose addon missing from the folder is copied in. */
    public synchronized void setEnabled(Info info, boolean enable) throws java.io.IOException {
        if (info.pack != null) {
            File impl = implDirectory(info.pack.id);
            if (!new File(packsDirectory(), info.pack.id).exists()) {
                extractBundledPack(info.pack, false);
            }
            rename(impl, info.name, enable, null);
        } else {
            rename(directory, info.name, enable, catalog.source(info.name));
        }
    }

    /** Installing a pack enables all of its addons (unpacking a bundled pack first); uninstalling disables them. */
    public synchronized void setPackEnabled(PackInfo pack, boolean enable) throws java.io.IOException {
        if (!pack.installed)
            extractBundledPack(pack.meta, false);
        File impl = implDirectory(pack.meta.id);
        for (Info info : pack.addons)
            rename(findDirectory(impl, info.name), info.name, enable, null);
    }

    /** The folder inside impl that holds the given addon (addons may sit in sub folders). */
    private File findDirectory(File impl, String name) {
        try (Stream<Path> files = Files.isDirectory(impl.toPath()) ? Files.walk(impl.toPath()) : Stream.<Path>empty()) {
            return files.filter(p -> p.getFileName().toString().equals(name + ".java") || p.getFileName().toString().equals(name + ".java.disabled"))
                    .map(p -> p.getParent().toFile()).findFirst().orElse(impl);
        } catch (java.io.IOException e) {
            return impl;
        }
    }

    private void rename(File dir, String name, boolean enable, String sourceIfMissing) throws java.io.IOException {
        File onFile = on(dir, name), offFile = off(dir, name);
        if (enable) {
            if (onFile.exists())
                return;
            if (offFile.exists())
                Files.move(offFile.toPath(), onFile.toPath());
            else if (sourceIfMissing != null)
                write(onFile, sourceIfMissing);
        } else if (onFile.exists()) {
            Files.deleteIfExists(offFile.toPath());
            Files.move(onFile.toPath(), offFile.toPath());
        }
    }

    /** Reloads every addon and rebuilds the ClickGUI so changes show up. */
    public int reload() {
        int count = load(Arsenic.getArsenic().getModuleManager());
        Arsenic.getArsenic().getClickGuiScreen().onAddonsReloaded();
        return count;
    }

    private static String describe(File source) {
        try {
            String text = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);
            int at = text.indexOf("@ModuleInfo");
            if (at >= 0) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("description\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(text.substring(at, Math.min(text.length(), at + 600)));
                if (m.find())
                    return m.group(1).replace("\\\"", "\"").replace("\\n", " ");
            }
        } catch (Exception ignored) {
            // unreadable file: fall through
        }
        return "No description.";
    }

    /** Unloads whatever was loaded before, then compiles and registers everything in the addons folder. */
    public synchronized int load(ModuleManager modules) {
        Map<String, JsonObject> previousState = unload(modules);
        errors.clear();
        try {
            directory.mkdirs();
            importPackZips();
            Map<String, String> sources = readSources();
            if (sources.isEmpty())
                return 0;

            AddonCompiler.Result result = newCompiler().compile(sources);
            errors.addAll(result.errors);

            ClassLoader loader = new AddonClassLoader(Arsenic.class.getClassLoader(), result.classes);
            for (String className : result.classes.keySet())
                register(modules, loader, className, previousState);
        } catch (Throwable t) {
            errors.add("Addon loading failed: " + t);
            Arsenic.getArsenic().getLogger().error("Addon loading failed", t);
        }
        errors.forEach(e -> Arsenic.getArsenic().getLogger().error("[addon] {}", e));
        return loaded.size();
    }

    private Map<String, JsonObject> unload(ModuleManager modules) {
        Map<String, JsonObject> state = new HashMap<>();
        for (Module module : loaded) {
            state.put(module.getName(), module.saveInfoToJson(new JsonObject()));
            if (module.isEnabled())
                module.setEnabled(false);
            modules.unregisterExternal(module);
        }
        loaded.clear();
        return state;
    }

    private void register(ModuleManager modules, ClassLoader loader, String className, Map<String, JsonObject> previousState) {
        try {
            Class<?> clazz = loader.loadClass(className);
            if (!Module.class.isAssignableFrom(clazz) || Modifier.isAbstract(clazz.getModifiers())
                    || !clazz.isAnnotationPresent(ModuleInfo.class))
                return;

            Module module = (Module) clazz.newInstance();
            String problem = modules.registerExternal(module);
            if (problem != null) {
                errors.add(className + ": " + problem);
                return;
            }
            loaded.add(module);

            JsonObject saved = previousState.get(module.getName());
            if (saved != null)
                module.loadFromJson(saved);
            else if (Arsenic.getArsenic().getConfigManager().getCurrentConfig() != null)
                applyConfig(module);
        } catch (Throwable t) {
            errors.add(className + ": " + t);
            Arsenic.getArsenic().getLogger().error("Failed to load addon class " + className, t);
        }
    }

    /** Applies the current config's saved values to a module that was added after the config was loaded. */
    private void applyConfig(Module module) {
        File file = Arsenic.getArsenic().getConfigManager().getCurrentConfig().getDirectory();
        try (FileReader reader = new FileReader(file)) {
            JsonElement saved = new JsonParser().parse(reader).getAsJsonObject().get(module.getJsonKey());
            if (saved != null)
                module.loadFromJson(saved.getAsJsonObject());
        } catch (Exception ignored) {
            // no saved config for this module yet
        }
    }

    private Map<String, String> readSources() throws java.io.IOException {
        Map<String, String> sources = new TreeMap<>();
        Path root = directory.toPath();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).collect(Collectors.toList())) {
                if (p.getFileName().toString().equals(".java")) {
                    errors.add(root.relativize(p) + ": the file needs a name, e.g. MyAddon.java for public class MyAddon");
                    continue;
                }
                sources.put(root.relativize(p).toString().replace('\\', '/'), new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
            }
        }
        return sources;
    }

    private AddonCompiler newCompiler() throws Exception {
        if (isMcpRuntime())
            return new AddonCompiler(new LoaderClassSource(Arsenic.class.getClassLoader()), null);

        try (InputStream in = AddonManager.class.getResourceAsStream("/addon-mappings.txt")) {
            if (in == null)
                throw new IllegalStateException("addon-mappings.txt is missing from this build");
            return new AddonCompiler(new FmlClassSource(), MappingTable.load(in));
        }
    }

    /** True in a development environment, where Minecraft still carries its MCP names. */
    private static boolean isMcpRuntime() {
        try {
            Minecraft.class.getDeclaredField("thePlayer");
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }
}

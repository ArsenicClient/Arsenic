package arsenic.addon;

import com.google.gson.Gson;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The addons and addon packs that ship inside the client jar. The jar mirrors the project's src/addons folder:
 * <pre>
 * addons.json                     loose default addons and the ids of the bundled packs
 * java/Name.java                  loose default addons
 * packs/id/pack.json              a pack's metadata
 * packs/id/impl/Name.java         the addons of a pack
 * </pre>
 */
public final class AddonCatalog {

    private static final String BASE = "/assets/arsenic/addons/";

    /** A loose default addon (one that is not part of a pack). */
    public static final class Entry {
        public String file;
        public String description = "";
    }

    /** The contents of a pack's pack.json. */
    public static final class PackMeta {
        public String id;
        public String name;
        public String description = "";
        /** A texture inside the game's resources, e.g. textures/items/bed.png. */
        public String icon;
        /** Installed and enabled the first time the client runs. */
        public boolean autoInstall;
        /** Addon name (file name without .java) to its description, in display order. */
        public Map<String, String> addons = new LinkedHashMap<>();
        /**
         * Addons other addons need, by addon name: "GameDetector" is an addon of this pack, "hypixel/GameDetector" one
         * of another pack. Enabling an addon enables what it needs (installing a bundled pack if it has to), and
         * disabling an addon disables the addons that need it, since addons only compile together with what they use.
         */
        public Map<String, List<String>> requires = new LinkedHashMap<>();
    }

    public List<Entry> addons = new ArrayList<>();
    /** Ids of the packs bundled in the jar. */
    public List<String> packs = new ArrayList<>();

    public static AddonCatalog load() {
        String json = read("addons.json");
        AddonCatalog catalog = json == null ? null : new Gson().fromJson(json, AddonCatalog.class);
        return catalog != null ? catalog : new AddonCatalog();
    }

    public static PackMeta parsePackMeta(String json) {
        try {
            PackMeta meta = new Gson().fromJson(json, PackMeta.class);
            return meta != null && meta.id != null ? meta : null;
        } catch (Exception e) {
            return null;
        }
    }

    public Entry entry(String file) {
        for (Entry e : addons)
            if (e.file.equals(file))
                return e;
        return null;
    }

    /** Metadata of every bundled pack this build actually contains. */
    public List<PackMeta> bundledPacks() {
        List<PackMeta> result = new ArrayList<>();
        for (String id : packs) {
            String json = read("packs/" + id + "/pack.json");
            PackMeta meta = json == null ? null : parsePackMeta(json);
            if (meta != null)
                result.add(meta);
        }
        return result;
    }

    public PackMeta bundledPack(String id) {
        for (PackMeta meta : bundledPacks())
            if (meta.id.equals(id))
                return meta;
        return null;
    }

    /** Source of a loose bundled addon, or null when this build does not contain it. */
    public String source(String file) {
        return read("java/" + file + ".java");
    }

    /** Source of an addon of a bundled pack, or null. */
    public String packSource(String packId, String addon) {
        return read("packs/" + packId + "/impl/" + addon + ".java");
    }

    public String packMetaSource(String packId) {
        return read("packs/" + packId + "/pack.json");
    }

    /**
     * Reads a bundled file from the jar. In a development run launched straight from the IDE the Gradle resource copy
     * does not exist, so fall back to the project's src/addons folder (the run directory is inside the project).
     */
    private static String read(String name) {
        try (InputStream in = AddonCatalog.class.getResourceAsStream(BASE + name)) {
            if (in != null)
                return new String(readAll(in), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // fall through to the development lookup
        }
        for (File root : new File[]{new File("."), new File("..")}) {
            File candidate = new File(root, "src/addons/" + name);
            try {
                if (candidate.isFile())
                    return new String(Files.readAllBytes(candidate.toPath()), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                // try the next location
            }
        }
        return null;
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1)
            out.write(buf, 0, n);
        return out.toByteArray();
    }
}

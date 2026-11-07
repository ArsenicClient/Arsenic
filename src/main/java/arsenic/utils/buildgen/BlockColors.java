package arsenic.utils.buildgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Makes colors.txt (the {@link Palette} resource) from a Minecraft client jar: every block whose default model is a
 * plain cube, with the average colour of its top and side textures. Run it again after a game update:
 * {@code java arsenic.utils.buildgen.BlockColors <client.jar> <colors.txt>}.
 */
public final class BlockColors {
    private BlockColors() {
    }

    private static final List<String> CUBES = List.of("block/cube_all", "block/cube_column", "block/cube",
            "block/cube_bottom_top", "block/leaves", "block/cube_mirrored_all", "block/cube_column_horizontal",
            "block/cube_top", "block/cube_all_inner_faces", "block/cube_column_uv_locked_x");

    /** Grey textures the game tints by biome; the plains colours are close enough for a picture. */
    private static final Map<String, int[]> TINT = Map.of(
            "grass_block", new int[]{0x91, 0xbd, 0x59}, "oak_leaves", new int[]{0x77, 0xab, 0x2f},
            "jungle_leaves", new int[]{0x77, 0xab, 0x2f}, "acacia_leaves", new int[]{0x77, 0xab, 0x2f},
            "dark_oak_leaves", new int[]{0x77, 0xab, 0x2f}, "mangrove_leaves", new int[]{0x92, 0xc0, 0x50},
            "birch_leaves", new int[]{0x80, 0xa7, 0x55}, "spruce_leaves", new int[]{0x61, 0x99, 0x61});

    public static void main(String[] args) throws Exception {
        try (ZipFile jar = new ZipFile(args[0]); PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(args[1])))) {
            Map<String, String> lines = new TreeMap<>();
            String prefix = "assets/minecraft/blockstates/";
            for (Enumeration<? extends ZipEntry> en = jar.entries(); en.hasMoreElements(); ) {
                String n = en.nextElement().getName();
                if (!n.startsWith(prefix) || !n.endsWith(".json")) continue;
                String block = n.substring(prefix.length(), n.length() - 5);
                if (block.contains("infested") || block.contains("command_block") || block.startsWith("structure")
                        || block.equals("jigsaw") || block.startsWith("test_") || block.equals("barrier")) continue;
                String model = firstModel(json(jar, n));
                if (model == null) continue;
                JsonObject m = json(jar, "assets/minecraft/models/" + model.replace("minecraft:", "") + ".json");
                if (m == null || !m.has("parent") || !m.get("parent").isJsonPrimitive()) continue;
                String parent = m.get("parent").getAsString().replace("minecraft:", "");
                if (!CUBES.contains(parent) && !(parent.equals("block/block") && block.equals("grass_block"))) continue;
                List<String> tex = new ArrayList<>();
                JsonObject t = m.getAsJsonObject("textures");
                for (String k : new String[]{"all", "top", "end", "side", "up", "north"})
                    if (t != null && t.has(k)) tex.add(sprite(t.get(k)).replace("minecraft:", ""));
                if (tex.isEmpty()) continue;
                double r = 0, g = 0, b = 0, a = 0;
                int used = 0;
                for (String tx : tex) {
                    double[] c = average(jar, "assets/minecraft/textures/" + tx + ".png");
                    if (c == null) continue;
                    r += c[0];
                    g += c[1];
                    b += c[2];
                    a += c[3];
                    used++;
                }
                if (used == 0) continue;
                r /= used;
                g /= used;
                b /= used;
                a /= used;
                int[] tint = TINT.get(block);
                if (tint != null) {
                    r = r * tint[0] / 255;
                    g = g * tint[1] / 255;
                    b = b * tint[2] / 255;
                }
                lines.put(block, String.format("%s %d %d %d %d", block, (int) r, (int) g, (int) b, (int) a));
            }
            for (String l : lines.values()) out.println(l);
            System.out.println(lines.size() + " cube blocks");
        }
    }

    /** A texture reference is a sprite name, or an object with one since 26.x ({"sprite": ..., "force_translucent": true}). */
    private static String sprite(JsonElement e) {
        return e.isJsonObject() ? e.getAsJsonObject().get("sprite").getAsString() : e.getAsString();
    }

    private static String firstModel(JsonObject state) {
        if (state == null || !state.has("variants")) return null;
        for (Map.Entry<String, JsonElement> e : state.getAsJsonObject("variants").entrySet()) {
            JsonElement v = e.getValue();
            if (v.isJsonArray()) v = v.getAsJsonArray().get(0);
            return v.getAsJsonObject().get("model").getAsString();
        }
        return null;
    }

    private static JsonObject json(ZipFile jar, String name) throws Exception {
        ZipEntry e = jar.getEntry(name);
        if (e == null) return null;
        try (InputStream in = jar.getInputStream(e)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /** Mean r, g, b over the opaque-ish pixels of the first animation frame, and the mean alpha. */
    private static double[] average(ZipFile jar, String name) throws Exception {
        ZipEntry e = jar.getEntry(name);
        if (e == null) return null;
        BufferedImage img;
        try (InputStream in = jar.getInputStream(e)) {
            img = ImageIO.read(in);
        }
        if (img == null) return null;
        int w = img.getWidth(), h = Math.min(img.getHeight(), w);
        double r = 0, g = 0, b = 0, wsum = 0, a = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int p = img.getRGB(x, y);
                double al = (p >>> 24) / 255.0;
                a += al;
                r += ((p >> 16) & 255) * al;
                g += ((p >> 8) & 255) * al;
                b += (p & 255) * al;
                wsum += al;
            }
        if (wsum == 0) return new double[]{0, 0, 0, 0};
        return new double[]{r / wsum, g / wsum, b / wsum, a / (w * h) * 255};
    }
}

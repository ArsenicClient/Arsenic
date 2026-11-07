package arsenic.utils.buildgen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The blocks a generated build may use, and the colour each one is drawn with. Only plain cubes for now: shaped
 * blocks (stairs, slabs, walls) need a facing, which the builder does not place yet. Colours come from colors.txt,
 * made from the game's textures by {@link BlockColors}.
 */
public final class Palette {
    public enum Level {
        /** The 16 concretes: blocky, but every colour reads clearly. */
        SIMPLE,
        /** About 90 hand-picked blocks: colours, glass, lights, stone, wood, ores, nature. */
        DETAILED,
        /** Every plain cube in the game. */
        FULL
    }

    private static final String[] COLOURS = {"white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
            "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink"};

    private static final String[] DETAILED = {"glass", "tinted_glass", "glowstone", "sea_lantern", "shroomlight",
            "ochre_froglight", "verdant_froglight", "pearlescent_froglight", "redstone_lamp", "amethyst_block",
            "crying_obsidian", "obsidian", "purpur_block", "quartz_block", "smooth_quartz", "snow_block", "end_stone",
            "end_stone_bricks", "prismarine", "dark_prismarine", "gold_block", "diamond_block", "emerald_block",
            "lapis_block", "iron_block", "copper_block", "redstone_block", "coal_block", "netherite_block", "stone",
            "cobblestone", "stone_bricks", "smooth_stone", "deepslate_tiles", "polished_blackstone", "blackstone",
            "basalt", "tuff", "calcite", "bricks", "mud_bricks", "sandstone", "sand", "red_sand", "dirt", "grass_block",
            "moss_block", "oak_planks", "spruce_planks", "dark_oak_planks", "birch_planks", "oak_log", "spruce_log",
            "oak_leaves", "netherrack", "magma_block", "ice", "packed_ice", "blue_ice", "bone_block", "honeycomb_block",
            "hay_block", "melon", "pumpkin", "terracotta"};

    public final Level level;
    /** Allowed block names (no "minecraft:"), in a stable order. */
    public final List<String> blocks = new ArrayList<>();
    private final Map<String, int[]> colours;

    public Palette(Level level) {
        this.level = level;
        colours = load();
        List<String> want = new ArrayList<>();
        switch (level) {
            case SIMPLE -> {
                for (String c : COLOURS) want.add(c + "_concrete");
            }
            case DETAILED -> {
                for (String c : COLOURS) want.add(c + "_concrete");
                for (String c : COLOURS) want.add(c + "_stained_glass");
                for (String c : COLOURS) want.add(c + "_terracotta");
                want.addAll(List.of(DETAILED));
            }
            case FULL -> want.addAll(colours.keySet());
        }
        for (String b : want)
            if (colours.containsKey(b) && !blocks.contains(b)) blocks.add(b);
    }

    public boolean has(String block) {
        return blocks.contains(block);
    }

    /** r, g, b, a (0-255) of a block, grey if unknown. */
    public int[] colour(String block) {
        int[] c = colours.get(block);
        return c == null ? new int[]{128, 128, 128, 255} : c;
    }

    /** The allowed blocks closest in spelling to a name that is not allowed, for the error message. */
    public List<String> closest(String name, int n) {
        List<String> sorted = new ArrayList<>(blocks);
        sorted.sort((a, b) -> Integer.compare(distance(a, name), distance(b, name)));
        return sorted.subList(0, Math.min(n, sorted.size()));
    }

    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++)
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    private static Map<String, int[]> cache;

    private static synchronized Map<String, int[]> load() {
        if (cache != null) return cache;
        Map<String, int[]> m = new LinkedHashMap<>();
        try (InputStream in = Palette.class.getResourceAsStream("/buildgen/colors.txt")) {
            if (in == null) throw new IllegalStateException("buildgen/colors.txt is missing");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                String[] p = line.trim().split(" ");
                if (p.length == 5)
                    m.put(p[0], new int[]{Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4])});
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        cache = m;
        return m;
    }
}

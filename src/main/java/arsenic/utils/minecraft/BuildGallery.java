package arsenic.utils.minecraft;

import arsenic.module.impl.visual.custommainmenu.ArsenicMainMenu;
import arsenic.module.impl.world.BuildBattle;
import arsenic.utils.buildcore.Blueprint;
import arsenic.utils.buildcore.Schematic;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Builds a walkable gallery world of generated builds: {@code -Darsenic.gallery=<folder>;<folder>...} at launch makes
 * a fresh flat creative world called "build-gallery", places every .schem under the folders on a grid (one row per
 * folder, best scored first), puts a sign in front of each with its theme, score and batch, and quits, which saves it.
 * The world is then in run/saves/build-gallery. Themes come from example.json or prompt.json beside each schematic
 * (else the folder name), scores from scores.json in the folder (both optional). A folder may be given as
 * {@code <folder>|<label>} to name its section. Uses the same world setup as {@link BuildShots}.
 * <p>
 * The world also gets a datapack "gallery" for getting around: {@code /function gallery:menu} lists the sections with
 * clickable [go] and [list] links, a list page links every build, and each build has its own function
 * {@code gallery:build/<section>/<theme>} that teleports in front of it.
 */
public final class BuildGallery {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String WORLD = "build-gallery";
    /** Grid spacing: builds are at most 32 wide, so 8 blocks of path between neighbours. */
    private static final int STEP = 40;

    private record Entry(Path schem, String theme, int score, String batch, int row) {
    }

    /** A folder's row: its function-safe id, its label and where its builds landed. */
    private record Section(String id, String label, List<Entry> entries, List<int[]> at) {
    }

    private enum Stage { OPEN_WORLD, CREATING, WAIT_WORLD, PLACE, DONE }

    private static BuildGallery active;

    private final List<Entry> entries = new ArrayList<>();
    private final List<Section> sections = new ArrayList<>();
    private final int[] perRow;
    private Stage stage = Stage.OPEN_WORLD;
    /** Builds per row, so the gallery stays about a kilometre wide. */
    private static final int COLS = 25;

    private int next, wait, ground, col, line;
    private int lastRow = -1;
    private CompletableFuture<?> pending;

    private BuildGallery(String folders) throws Exception {
        String[] dirs = folders.split(";");
        perRow = new int[dirs.length];
        for (int row = 0; row < dirs.length; row++) {
            String[] spec = dirs[row].split("\\|", 2);
            Path dir = Path.of(spec[0].trim());
            JsonObject scores = Files.exists(dir.resolve("scores.json"))
                    ? JsonParser.parseString(Files.readString(dir.resolve("scores.json"))).getAsJsonObject() : new JsonObject();
            List<Entry> rowEntries = new ArrayList<>();
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path p : files.filter(f -> f.getFileName().toString().equals("final.schem")).sorted().toList()) {
                    String key = dir.relativize(p).toString().replace('\\', '/');
                    int score = scores.has(key) ? scores.get(key).getAsInt() : 0;
                    // self-generated builds sit in <theme>/best/
                    Path folder = p.getParent().getFileName().toString().equals("best") ? p.getParent().getParent() : p.getParent();
                    String theme = folder.getFileName().toString().replace('-', ' ');
                    for (String name : new String[]{"example.json", "prompt.json"}) {
                        Path ex = p.resolveSibling(name);
                        if (!Files.exists(ex)) continue;
                        JsonObject o = JsonParser.parseString(Files.readString(ex)).getAsJsonObject();
                        if (o.has("theme")) theme = o.get("theme").getAsString();
                        break;
                    }
                    rowEntries.add(new Entry(p, theme, score, dir.getFileName().toString(), row));
                }
            }
            rowEntries.sort(Comparator.comparingInt(Entry::score).reversed());
            entries.addAll(rowEntries);
            String batch = dir.getFileName().toString();
            sections.add(new Section(slug(batch), spec.length > 1 ? spec[1].trim() : batch, rowEntries, new ArrayList<>()));
            perRow[row] = rowEntries.size();
        }
    }

    public static void startAtLaunch(String folders) {
        try {
            active = new BuildGallery(folders);
            LOGGER.info("gallery: {} builds from {}", active.entries.size(), folders);
            ClientTickEvents.END_CLIENT_TICK.register(mc -> {
                if (active == null || active.stage == Stage.DONE) return;
                try {
                    active.tick(mc);
                } catch (Exception e) {
                    LOGGER.error("gallery failed", e);
                    active.stage = Stage.DONE;
                    mc.stop();
                }
            });
        } catch (Exception e) {
            LOGGER.error("gallery: cannot read {}", folders, e);
        }
    }

    private void tick(Minecraft mc) throws Exception {
        if (wait > 0) {
            wait--;
            return;
        }
        IntegratedServer server = mc.getSingleplayerServer();
        switch (stage) {
            case OPEN_WORLD -> {
                if (mc.level != null || !(mc.gui.screen() instanceof TitleScreen || mc.gui.screen() instanceof ArsenicMainMenu)) return;
                if (mc.getLevelSource().levelExists(WORLD))
                    try (var access = mc.getLevelSource().createAccess(WORLD)) {
                        access.deleteLevel();
                    }
                CreateWorldScreen.testWorld(mc, () -> {
                });
                stage = Stage.CREATING;
            }
            case CREATING -> {
                if (!(mc.gui.screen() instanceof CreateWorldScreen screen)) return;
                screen.getUiState().setName(WORLD);
                Method create = CreateWorldScreen.class.getDeclaredMethod("onCreate");
                create.setAccessible(true);
                create.invoke(screen);
                stage = Stage.WAIT_WORLD;
            }
            case WAIT_WORLD -> {
                if (mc.level == null && mc.gui.screen() instanceof ConfirmScreen confirm) {
                    java.lang.reflect.Field callback = ConfirmScreen.class.getDeclaredField("callback");
                    callback.setAccessible(true);
                    ((BooleanConsumer) callback.get(confirm)).accept(true);
                    return;
                }
                if (mc.player == null || mc.level == null || server == null) return;
                mc.options.pauseOnLostFocus = false;
                if (mc.gui.screen() != null) mc.gui.setScreen(null);
                ServerLevel level = server.getLevel(Level.OVERWORLD);
                pending = server.submit(() -> ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0));
                stage = Stage.PLACE;
                wait = 20;
            }
            case PLACE -> {
                if (!pending.isDone()) return;
                if (next == 0) {
                    for (String cmd : new String[]{"gamemode creative @a", "time set noon", "weather clear",
                            "gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false",
                            "setworldspawn 0 " + ground + " -8", "tp @a 0 " + (ground + 2) + " -8 facing 16 " + ground + " 16"})
                        command(server, cmd);
                }
                if (next >= entries.size()) {
                    stage = Stage.DONE;
                    writeDatapack(server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("gallery"), ground);
                    // load it now too, so a function that fails to parse shows up in this run's log
                    command(server, "datapack enable \"file/gallery\"");
                    command(server, "reload");
                    LOGGER.info("gallery: done, {} builds; world saved as {}", entries.size(), WORLD);
                    // quitting saves the world; give the last placements and signs a moment to land first
                    wait = 40;
                    mc.execute(() -> server.execute(() -> mc.execute(mc::stop)));
                    return;
                }
                Entry e = entries.get(next++);
                // rows of COLS builds; each folder starts a new block of rows after an empty one
                if (e.row != lastRow) {
                    if (lastRow >= 0) line += 2;
                    lastRow = e.row;
                    col = 0;
                } else if (col == COLS) {
                    line++;
                    col = 0;
                }
                Blueprint bp;
                try (InputStream in = Files.newInputStream(e.schem)) {
                    bp = Schematic.read(in).trimmed();
                }
                int bx = col++ * STEP, bz = line * STEP;
                sections.get(e.row).at().add(new int[]{bx, bz});
                int x = bx + (32 - bp.sizeX) / 2, z = bz + (32 - bp.sizeZ) / 2, y = ground;
                ServerLevel level = server.getLevel(Level.OVERWORLD);
                // placed directly rather than by command: /setblock refuses chunks that are not loaded
                pending = server.submit(() -> {
                    BuildBattle.place(level, bp, x, y, z);
                    sign(level, new BlockPos(bx + 16, y, bz - 3), e);
                });
                if (next % 25 == 0) LOGGER.info("gallery: {}/{}", next, entries.size());
            }
            case DONE -> {
            }
        }
    }

    /** A sign on a fence post in front of the plot (low z side), facing the path: theme over two lines, score, batch. */
    private static void sign(ServerLevel level, BlockPos post, Entry e) {
        String t = e.theme, l1 = t, l2 = "";
        if (t.length() > 15) {
            int cut = t.lastIndexOf(' ', 15);
            if (cut <= 0) cut = Math.min(15, t.length());
            l1 = t.substring(0, cut).trim();
            l2 = t.substring(cut).trim();
            if (l2.length() > 15) l2 = l2.substring(0, 14) + ".";
        }
        String l3 = e.score > 0 ? "score " + e.score : "unscored";
        level.setBlock(post, Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_CLIENTS);
        BlockPos at = post.above();
        level.setBlock(at, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(at) instanceof SignBlockEntity sign) {
            List<Component> lines = List.of(Component.literal(l1), Component.literal(l2), Component.literal(l3), Component.literal(e.batch));
            sign.setText(new SignText(lines, lines, DyeColor.BLACK, false), SignTextSlot.FRONT);
            sign.setChanged();
        }
    }

    /**
     * The "gallery" datapack: gallery:menu (sections with [go]/[list]), gallery:section/<id> (to its best build),
     * gallery:list/<id>/<page> (clickable builds, 15 a page) and gallery:build/<id>/<theme> (stand in front of it,
     * facing it). Packs in a world's datapacks folder are enabled the next time it loads.
     */
    private void writeDatapack(Path pack, int y) throws Exception {
        Path fn = pack.resolve("data/gallery/function");
        if (Files.exists(pack))
            try (Stream<Path> old = Files.walk(pack)) {
                for (Path f : old.sorted(Comparator.reverseOrder()).toList()) Files.delete(f);
            }
        Files.createDirectories(fn);
        PackFormat format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
        String fmt = "[" + format.major() + ", " + format.minor() + "]";
        Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\": {\"description\": \"Build gallery teleports\", \"min_format\": "
                + fmt + ", \"max_format\": " + fmt + "}}\n");
        Files.createDirectories(pack.resolve("data/minecraft/tags/function"));
        Files.writeString(pack.resolve("data/minecraft/tags/function/load.json"), "{\"values\": [\"gallery:load\"]}\n");
        Files.writeString(fn.resolve("load.mcfunction"), "tellraw @a [" + text("Build gallery: ", "gold") + ","
                + link("/function gallery:menu", "aqua", "Open the menu", "/function gallery:menu") + "]\n");

        StringBuilder menu = new StringBuilder("tellraw @s " + text("Build gallery", "gold") + "\n");
        for (Section s : sections) {
            if (s.entries().isEmpty()) continue;
            int scored = 0, sum = 0;
            for (Entry e : s.entries())
                if (e.score() > 0) {
                    scored++;
                    sum += e.score();
                }
            String stats = " " + s.entries().size() + " builds"
                    + (scored > 0 ? String.format(Locale.ROOT, ", mean %.1f", sum / (double) scored) : "");
            menu.append("tellraw @s [").append(link("[go] ", "green", "Teleport to its best build", "/function gallery:section/" + s.id()))
                    .append(",").append(link("[list] ", "aqua", "List its builds", "/function gallery:list/" + s.id() + "/1"))
                    .append(",").append(text(s.label(), "white")).append(",").append(text(stats, "gray")).append("]\n");

            List<String> fns = new ArrayList<>();
            Set<String> used = new HashSet<>();
            for (int i = 0; i < s.entries().size(); i++) {
                Entry e = s.entries().get(i);
                int[] at = s.at().get(i);
                String id = slug(e.theme()), base = id;
                for (int n = 2; !used.add(id); n++) id = base + "-" + n;
                String name = "build/" + s.id() + "/" + id;
                fns.add(name);
                Path f = fn.resolve(name + ".mcfunction");
                Files.createDirectories(f.getParent());
                // stand on the path in front of the sign, looking at the middle of the plot
                Files.writeString(f, String.format(Locale.ROOT, "tp @s %d %d %d facing %d %d %d%n",
                        at[0] + 16, y + 3, at[1] - 9, at[0] + 16, y + 8, at[1] + 16)
                        + "tellraw @s [" + text(e.theme(), "gold") + "," + text(" " + (e.score() > 0 ? "score " + e.score() : "unscored")
                        + "  " + s.label() + " #" + (i + 1), "gray") + "]\n");
            }
            Path sec = fn.resolve("section/" + s.id() + ".mcfunction");
            Files.createDirectories(sec.getParent());
            Files.writeString(sec, "function gallery:" + fns.get(0) + "\n");

            int per = 15, pages = (fns.size() + per - 1) / per;
            for (int pg = 1; pg <= pages; pg++) {
                StringBuilder list = new StringBuilder("tellraw @s " + text(s.label() + " - page " + pg + "/" + pages, "gold") + "\n");
                for (int i = (pg - 1) * per; i < Math.min(fns.size(), pg * per); i++) {
                    Entry e = s.entries().get(i);
                    list.append("tellraw @s [").append(text((i + 1) + ". ", "gray")).append(",")
                            .append(link(e.theme(), "white", "Teleport here", "/function gallery:" + fns.get(i))).append(",")
                            .append(text(e.score() > 0 ? "  " + e.score() : "", "yellow")).append("]\n");
                }
                list.append("tellraw @s [");
                if (pg > 1) list.append(link("[prev] ", "aqua", "Previous page", "/function gallery:list/" + s.id() + "/" + (pg - 1))).append(",");
                if (pg < pages) list.append(link("[next] ", "aqua", "Next page", "/function gallery:list/" + s.id() + "/" + (pg + 1))).append(",");
                list.append(link("[menu]", "green", "Back to the sections", "/function gallery:menu")).append("]\n");
                Path f = fn.resolve("list/" + s.id() + "/" + pg + ".mcfunction");
                Files.createDirectories(f.getParent());
                Files.writeString(f, list);
            }
        }
        Files.writeString(fn.resolve("menu.mcfunction"), menu);
        LOGGER.info("gallery: datapack written to {}", pack);
    }

    /** Lower case letters, digits and '-', for function names. */
    private static String slug(String s) {
        String r = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return r.isEmpty() ? "x" : r;
    }

    private static String text(String s, String color) {
        return "{\"text\": " + quote(s) + ", \"color\": \"" + color + "\"}";
    }

    private static String link(String s, String color, String hover, String command) {
        return "{\"text\": " + quote(s) + ", \"color\": \"" + color + "\", \"click_event\": {\"action\": \"run_command\", \"command\": "
                + quote(command) + "}, \"hover_event\": {\"action\": \"show_text\", \"value\": " + quote(hover) + "}}";
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void command(IntegratedServer server, String cmd) {
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd));
    }
}

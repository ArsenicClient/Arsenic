package arsenic.utils.minecraft;

import arsenic.module.impl.visual.custommainmenu.ArsenicMainMenu;
import arsenic.module.impl.world.BuildBattle;
import arsenic.utils.buildcore.Blueprint;
import arsenic.utils.buildcore.Schematic;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Screenshots generated builds in game so they can be scored by eye. Each .schem under a folder is placed instantly on
 * a flat singleplayer plot and shot from two opposite corners with the HUD hidden, the two views side by side in
 * {@code <name>-ingame.png} next to the schematic. Builds that already have one are skipped unless redo is set.
 * Started by {@code .buildshot <folder>} in a singleplayer world, or at launch with {@code -Darsenic.buildshot=<folder>}
 * (and {@code -Darsenic.buildshot.redo=true}), which opens or creates a flat world called "buildshot" and quits the game
 * when every build is shot.
 */
public final class BuildShots {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String WORLD = "buildshot";
    /** Half the width of the plot kept clear around the origin, and its height above the ground. */
    private static final int PLOT = 24, PLOT_H = 48;

    private static BuildShots active;
    private static boolean registered;

    private enum Stage { OPEN_WORLD, CREATING, WAIT_WORLD, NEXT, PLACING, SHOOT, CAPTURING, DONE }

    private final Deque<Path> queue = new ArrayDeque<>();
    private final boolean quitWhenDone;
    private Stage stage;
    private int wait, ground, angle, total;
    private Path current;
    private Blueprint bp;
    private int ox, oy, oz;
    private CompletableFuture<?> pending;
    private NativeImage first;

    private BuildShots(Path folder, boolean redo, boolean quitWhenDone) throws Exception {
        this.quitWhenDone = quitWhenDone;
        try (Stream<Path> files = Files.walk(folder)) {
            files.filter(p -> p.toString().endsWith(".schem")).sorted()
                    .filter(p -> redo || !Files.exists(shotFile(p))).forEach(queue::add);
        }
        total = queue.size();
    }

    static Path shotFile(Path schem) {
        String name = schem.getFileName().toString();
        return schem.resolveSibling(name.substring(0, name.length() - ".schem".length()) + "-ingame.png");
    }

    /** At launch: opens the buildshot world by itself and quits when done. */
    public static void startAtLaunch(String folder, boolean redo) {
        try {
            active = new BuildShots(Path.of(folder), redo, true);
            active.stage = Stage.OPEN_WORLD;
            register();
            LOGGER.info("buildshot: {} builds to shoot in {}", active.total, folder);
        } catch (Exception e) {
            LOGGER.error("buildshot: cannot read {}", folder, e);
        }
    }

    /** From a command, in the singleplayer world the player is in. Returns the chat message. */
    public static String start(File folder, boolean redo) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() == null || mc.player == null) return "buildshot only works in a singleplayer world";
        if (active != null && active.stage != Stage.DONE) return "buildshot is already running";
        try {
            active = new BuildShots(folder.toPath(), redo, false);
        } catch (Exception e) {
            return "cannot read " + folder + " (" + e.getMessage() + ")";
        }
        if (active.total == 0) return "nothing to shoot in " + folder + " (add 'redo' to shoot again)";
        active.stage = Stage.WAIT_WORLD;
        register();
        return "shooting " + active.total + " builds; the plot around 0,0 is cleared";
    }

    private static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (active == null || active.stage == Stage.DONE) return;
            try {
                active.tick(mc);
            } catch (Exception e) {
                LOGGER.error("buildshot failed", e);
                active.finish(mc);
            }
        });
    }

    private Stage loggedStage;
    private Object loggedScreen;

    private void tick(Minecraft mc) throws Exception {
        Object screenName = mc.gui.screen() == null ? null : mc.gui.screen().getClass().getSimpleName();
        if (stage != loggedStage || !java.util.Objects.equals(screenName, loggedScreen)) {
            LOGGER.info("buildshot: stage {}, screen {}", stage, screenName);
            loggedStage = stage;
            loggedScreen = screenName;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        IntegratedServer server = mc.getSingleplayerServer();
        switch (stage) {
            case OPEN_WORLD -> {
                if (mc.level != null || !(mc.gui.screen() instanceof TitleScreen || mc.gui.screen() instanceof ArsenicMainMenu)) return;
                // a fresh scratch world each launch: reopening one made in a dev run asks about backups (experimental)
                if (mc.getLevelSource().levelExists(WORLD))
                    try (var access = mc.getLevelSource().createAccess(WORLD)) {
                        access.deleteLevel();
                    }
                // the debug "test world": flat, creative, no day/night, weather or mobs
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
                    // in a dev run the world gets the experimental "tests" data pack, which asks first
                    java.lang.reflect.Field callback = ConfirmScreen.class.getDeclaredField("callback");
                    callback.setAccessible(true);
                    ((BooleanConsumer) callback.get(confirm)).accept(true);
                    return;
                }
                if (mc.player == null || mc.level == null || server == null) return;
                // a background window would pause the world and put the pause menu in every shot
                mc.options.pauseOnLostFocus = false;
                if (mc.gui.screen() != null) mc.gui.setScreen(null);
                for (String cmd : new String[]{"gamemode spectator @a", "time set noon", "weather clear", "tick freeze"})
                    command(server, cmd);
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                ServerLevel level = server.getLevel(Level.OVERWORLD);
                pending = server.submit(() -> {
                    // ground from a corner the builds never reach, then clear whatever an earlier run left
                    ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, PLOT + 8, PLOT + 8);
                    clear(level, -PLOT, ground, -PLOT, 2 * PLOT + 1, PLOT_H, 2 * PLOT + 1);
                });
                stage = Stage.NEXT;
                wait = 40;
            }
            case NEXT -> {
                if (!pending.isDone()) return;
                current = queue.poll();
                if (current == null) {
                    finish(mc);
                    return;
                }
                try (InputStream in = new FileInputStream(current.toFile())) {
                    bp = Schematic.read(in).trimmed();
                } catch (Exception e) {
                    LOGGER.warn("buildshot: cannot read {}", current, e);
                    return;
                }
                ServerLevel level = server.getLevel(Level.OVERWORLD);
                Blueprint build = bp;
                int x = ox = -bp.sizeX / 2, y = oy = ground, z = oz = -bp.sizeZ / 2;
                pending = server.submit(() -> {
                    clear(level, -PLOT, y, -PLOT, 2 * PLOT + 1, PLOT_H, 2 * PLOT + 1);
                    BuildBattle.place(level, build, x, y, z);
                });
                angle = 0;
                stage = Stage.PLACING;
            }
            case PLACING -> {
                if (!pending.isDone()) return;
                aim(server);
                stage = Stage.SHOOT;
                wait = 20;
            }
            case SHOOT -> {
                if (!mc.levelRenderer.hasRenderedAllSections()) return;
                stage = Stage.CAPTURING;
                Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> mc.execute(() -> captured(mc, image)));
            }
            case CAPTURING, DONE -> {
            }
        }
    }

    private void captured(Minecraft mc, NativeImage image) {
        if (angle == 0) {
            first = image;
            angle = 1;
            aim(mc.getSingleplayerServer());
            stage = Stage.SHOOT;
            wait = 10;
            return;
        }
        NativeImage a = first, b = image;
        first = null;
        Path out = shotFile(current);
        int done = total - queue.size();
        Util.ioPool().execute(() -> {
            // the square middle of each view, side by side
            int s = Math.min(a.getWidth(), a.getHeight());
            try (a; b; NativeImage both = new NativeImage(2 * s, s, true)) {
                a.copyRect(both, (a.getWidth() - s) / 2, (a.getHeight() - s) / 2, 0, 0, s, s, false, false);
                b.copyRect(both, (b.getWidth() - s) / 2, (b.getHeight() - s) / 2, s, 0, s, s, false, false);
                both.writeToFile(out);
                LOGGER.info("buildshot: {}/{} {}", done, total, out);
            } catch (Exception e) {
                LOGGER.warn("buildshot: cannot write {}", out, e);
            }
        });
        stage = Stage.NEXT;
    }

    /**
     * Puts the camera on one of two opposite corners, looking down at the build's centre, just far enough back that
     * every corner of the build's box fits in the square middle of the view (the part that is kept).
     */
    private void aim(IntegratedServer server) {
        double cx = ox + bp.sizeX / 2.0, cy = oy + bp.sizeY / 2.0, cz = oz + bp.sizeZ / 2.0;
        double tan = Math.tan(Math.toRadians(Minecraft.getInstance().options.fov().get()) / 2);
        double pitch = Math.toRadians(28), side = angle == 0 ? 1 : -1;
        // camera axes: back (centre to eye), right and up
        double bx = side * Math.cos(pitch) / Math.sqrt(2), by = Math.sin(pitch), bz = side * Math.cos(pitch) / Math.sqrt(2);
        double rx = side / Math.sqrt(2), rz = -side / Math.sqrt(2);
        double ux = -by * bx / Math.cos(pitch), uy = Math.cos(pitch), uz = -by * bz / Math.cos(pitch);
        double d = 4;
        for (int i = 0; i < 8; i++) {
            double px = ((i & 1) == 0 ? -1 : 1) * bp.sizeX / 2.0, py = ((i & 2) == 0 ? -1 : 1) * bp.sizeY / 2.0,
                    pz = ((i & 4) == 0 ? -1 : 1) * bp.sizeZ / 2.0;
            double depth = px * bx + py * by + pz * bz;
            double x = Math.abs(px * rx + pz * rz), y = Math.abs(px * ux + py * uy + pz * uz);
            d = Math.max(d, Math.max(x, y) * 1.08 / tan + depth);
        }
        double ex = cx + d * bx, ey = cy + d * by, ez = cz + d * bz;
        command(server, String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", ex, ey - 1.62, ez, cx, cy, cz));
    }

    private void finish(Minecraft mc) {
        stage = Stage.DONE;
        LOGGER.info("buildshot: done, {} builds", total);
        if (quitWhenDone) mc.stop();
        else PlayerUtils.addWaterMarkedMessageToChat("buildshot done: " + total + " builds");
    }

    private static void command(IntegratedServer server, String cmd) {
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd));
    }

    private static void clear(ServerLevel level, int x0, int y0, int z0, int sx, int sy, int sz) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = y0; y < y0 + sy; y++)
            for (int z = z0; z < z0 + sz; z++)
                for (int x = x0; x < x0 + sx; x++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
    }
}

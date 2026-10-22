
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.blatant.KillAura;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.Options;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import arsenic.utils.bot.McWorldView;
import arsenic.utils.botcore.BlockView;
import arsenic.utils.botcore.Goal;
import arsenic.utils.botcore.Planner;
import arsenic.utils.botcore.Step;
import arsenic.utils.botcore.Tuning;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.JTextArea;
import java.awt.GraphicsEnvironment;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fight bot for The Pit Classic that tries to keep an uberstreak alive:
 *  - /oof at a kill streak (400 by default) to bank it
 *  - places Tactical Insertion (blaze rod) some seconds after every death
 *  - wears a Self-checkout pants at a 5,000g bounty and swaps your old pants back afterwards
 *  - sprint jumps to the densest group of players around 0 0, fights with KillAura, and steers around slimes
 *  - friends everyone wearing a diamond chestplate
 *  - sends player chat messages to your desktop as notifications
 */
@ModuleInfo(name = "AutoUber", category = ModuleCategory.PLAYER, tier = ModuleTier.BLATANT)
public class AutoUber extends Module {

    public final DoubleProperty oofStreak = new DoubleProperty("Oof Streak", new DoubleValue(50, 1000, 400, 10));
    public final DoubleProperty insertionDelay = new DoubleProperty("Insertion Delay (s)", new DoubleValue(0, 30, 10, 0.5));
    public final DoubleProperty insertionHold = new DoubleProperty("Insertion Hold (s)", new DoubleValue(0.2, 2, 0.5, 0.05));
    public final DoubleProperty checkoutBounty = new DoubleProperty("Self-checkout Bounty", new DoubleValue(500, 20000, 5000, 100));
    public final DoubleProperty chaseRange = new DoubleProperty("Chase Range", new DoubleValue(2, 20, 7, 0.5));
    public final DoubleProperty arenaRadius = new DoubleProperty("Arena Radius", new DoubleValue(10, 100, 45, 1));
    public final DoubleProperty clusterRadius = new DoubleProperty("Cluster Radius", new DoubleValue(3, 20, 8, 0.5));
    public final BooleanProperty doOof = new BooleanProperty("Auto /oof", true);
    public final BooleanProperty doInsertion = new BooleanProperty("Tactical Insertion", true);
    public final BooleanProperty doCheckout = new BooleanProperty("Self-checkout", true);
    public final BooleanProperty keepBlobs = new BooleanProperty("Keep Pitblob Pants On", true);
    public final BooleanProperty doMove = new BooleanProperty("Move To Crowd", true);
    public final BooleanProperty avoidSlimes = new BooleanProperty("Avoid Slimes", true);
    public final BooleanProperty friendDiamond = new BooleanProperty("Friend Diamond Chest", true);
    public final BooleanProperty notifyChat = new BooleanProperty("Chat Window", true);
    public final BooleanProperty mentionsOnly = new BooleanProperty("Only Mentions", false);
    public final BooleanProperty pathCheck = new BooleanProperty("Path Check", true);
    public final DoubleProperty maxDetour = new DoubleProperty("Max Detour", new DoubleValue(1, 4, 1.8, 0.1));
    public final DoubleProperty prematureMin = new DoubleProperty("Premature Min Streak", new DoubleValue(1, 400, 50, 5));
    public final BooleanProperty windowOnTop = new BooleanProperty("Window Always On Top", true);
    public final BooleanProperty pitOnly = new BooleanProperty("Pit Only", true);
    public final BooleanProperty pickupMystics = new BooleanProperty("Pick Up Mystics", true);
    public final BooleanProperty notifyEvents = new BooleanProperty("Notify Death/Stall", true);
    public final DoubleProperty stallSeconds = new DoubleProperty("Streak Stall (s)", new DoubleValue(10, 300, 40, 5));

    // ---------------------------------------------------------------- stats (session = this game launch, lifetime = file on disk)

    private static final String[] STAT_KEYS = {"kills", "deaths", "oofs", "bankedStreak", "uberdrops", "premature", "prematureLost",
            "insertions", "insertionTries", "checkouts", "checkoutTries", "bestStreak", "runtimeMs"};

    private static final class Stats {
        final java.util.Map<String, Double> v = new java.util.LinkedHashMap<>();

        Stats() {
            for (String k : STAT_KEYS) v.put(k, 0.0);
        }

        double get(String k) {
            Double d = v.get(k);
            return d == null ? 0 : d;
        }

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            for (java.util.Map.Entry<String, Double> e : v.entrySet()) o.addProperty(e.getKey(), e.getValue());
            return o;
        }

        void load(JsonObject o) {
            for (String k : STAT_KEYS)
                if (o.has(k) && o.get(k).isJsonPrimitive()) v.put(k, o.get(k).getAsDouble());
        }
    }

    private static final Stats SESSION = new Stats();
    private static final Stats LIFETIME = new Stats();
    private static final long SESSION_START = System.currentTimeMillis();
    private static final Object FILE_LOCK = new Object();
    private static final List<JsonObject> PAST_SESSIONS = new ArrayList<>();
    private static boolean statsLoaded;
    private static boolean shutdownHookAdded;
    private static volatile boolean statsDirty;
    private static long lastSave;

    private static File statsFile() {
        return new File(arsenic.utils.java.FileUtils.getArsenicFolderDirAsFile(), "autouber-stats.json");
    }

    private static void loadStats() {
        synchronized (FILE_LOCK) {
            if (statsLoaded) return;
            statsLoaded = true;
            try {
                File file = statsFile();
                if (file.isFile()) {
                    JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
                    if (root.has("lifetime")) LIFETIME.load(root.getAsJsonObject("lifetime"));
                    if (root.has("sessions"))
                        for (JsonElement e : root.getAsJsonArray("sessions"))
                            if (e.getAsJsonObject().get("start").getAsLong() != SESSION_START) PAST_SESSIONS.add(e.getAsJsonObject());
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
            if (!shutdownHookAdded) {
                shutdownHookAdded = true;
                Runtime.getRuntime().addShutdownHook(new Thread(AutoUber::saveStats, "AutoUber-stats-save"));
            }
        }
    }

    private static void saveStats() {
        synchronized (FILE_LOCK) {
            if (!statsLoaded) return;
            try {
                JsonObject root = new JsonObject();
                root.add("lifetime", LIFETIME.toJson());
                JsonArray sessions = new JsonArray();
                int from = Math.max(0, PAST_SESSIONS.size() - 99);
                for (int i = from; i < PAST_SESSIONS.size(); i++) sessions.add(PAST_SESSIONS.get(i));
                JsonObject cur = SESSION.toJson();
                cur.addProperty("start", SESSION_START);
                cur.addProperty("end", System.currentTimeMillis());
                sessions.add(cur);
                root.add("sessions", sessions);
                File file = statsFile();
                file.getParentFile().mkdirs();
                Files.write(file.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(StandardCharsets.UTF_8));
                statsDirty = false;
                lastSave = System.currentTimeMillis();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    private static void stat(String key, double amount) {
        synchronized (FILE_LOCK) {
            SESSION.v.put(key, SESSION.get(key) + amount);
            LIFETIME.v.put(key, LIFETIME.get(key) + amount);
        }
        statsDirty = true;
    }

    private static void statMax(String key, double value) {
        synchronized (FILE_LOCK) {
            if (value > SESSION.get(key)) SESSION.v.put(key, value);
            if (value > LIFETIME.get(key)) LIFETIME.v.put(key, value);
        }
        statsDirty = true;
    }

    private static String duration(double ms) {
        long s = (long) (ms / 1000);
        return String.format("%dh %02dm %02ds", s / 3600, (s / 60) % 60, s % 60);
    }

    private static String num(double d) {
        return d == Math.rint(d) ? String.format("%,d", (long) d) : String.format("%,.1f", d);
    }

    private static final Pattern STREAK = Pattern.compile("Streak:\\s*([\\d,]+(?:\\.\\d+)?)");
    private static final Pattern BOUNTY = Pattern.compile("Bounty:\\s*([\\d,]+)\\s*g");
    private static final double SLIME_MARGIN = 1.0;
    private static final double STOP_DISTANCE = 2.2;
    private static final double CROWD_STOP = 3.0;
    private static final int[] OFFSETS = {0, 20, -20, 40, -40, 60, -60, 80, -80, 100, -100, 125, -125, 150, -150, 180};

    private final ConcurrentHashMap<Integer, double[]> verdicts = new ConcurrentHashMap<>();
    private final java.util.Set<Integer> probing = ConcurrentHashMap.newKeySet();

    private final ConcurrentLinkedQueue<String> pendingChat = new ConcurrentLinkedQueue<>();
    private volatile boolean respawnPacket;
    private volatile boolean teleportDeath;
    private volatile long lootUntil;
    private volatile long mysticChatAt;
    /** entity id -> time its item spawn packet arrived */
    private final ConcurrentHashMap<Integer, Long> itemSpawns = new ConcurrentHashMap<>();
    private long lastDeathAt;
    private double lastStreakSeen;
    private long lastIncreaseAt;
    private boolean stallNotified;
    private Object lastWorld;
    private long ticks;

    // scoreboard readings
    private double streak;
    private double bounty;
    private boolean inPit;

    // death / insertion
    private boolean wasAlive = true;
    private long insertionAt;
    private long insertionRetryAt;
    private int insTotal, insTicks, insSlot, insPrev;
    private boolean insertionActive;
    private int insertionTries;
    private volatile boolean insertionConfirmed;
    private int restoreSlot = -1;
    private boolean warnedNoRod;

    // oof
    private long oofCooldown;
    private long lastOofAt;

    // self-checkout
    private int coState;
    private int coSlot, coPrev, coFix, coTries;
    private long coStart, coCooldown, coLastClick, coVerifyUntil;
    private String coWanted;
    private boolean warnedNoSco;
    private boolean warnedNoBlob;

    // movement
    private double goalX, goalZ;
    private boolean haveGoal;
    private long goalRefresh;
    private double lastX, lastZ;
    private long stuckCheck, unstickUntil;
    private int unstickSign = 1;
    private boolean keysHeld;
    private boolean savedPause, pauseSaved, enabledKillAura;
    private String status = "idle";

    // chat window
    private final ArrayDeque<double[]> streakHistory = new ArrayDeque<>();
    private boolean streakBaselined;
    private long lastTickAt;
    private String lastDashboard = "";
    private JTextArea dashboard;
    private JFrame frame;
    private JTextPane pane;
    private boolean windowFailed;
    private long lastNotify;
    private String lastNotifyText = "";

    @Override
    protected void onEnable() {
        loadStats();
        lastTickAt = 0;
        streakBaselined = false;
        streakHistory.clear();
        if (notifyChat.getValue() || notifyEvents.getValue()) {
            notifyDesktop("AutoUber+", "AutoUber enabled");
            openWindow();
        }
        pendingChat.clear();
        respawnPacket = false;
        coState = 0;
        coCooldown = 0;
        coVerifyUntil = 0;
        coTries = 0;
        restoreSlot = -1;
        // try to set a Tactical Insertion straight away; the server confirms it in chat
        insertionActive = false;
        insertionConfirmed = false;
        insertionTries = 0;
        insertionAt = System.currentTimeMillis();
        insertionRetryAt = 0;
        wasAlive = true;
        haveGoal = false;
        if (mc.options != null) {
            savedPause = mc.options.pauseOnLostFocus;
            pauseSaved = true;
            mc.options.pauseOnLostFocus = false;
        }
    }

    @Override
    protected void onDisable() {
        if (mc.options != null && pauseSaved) mc.options.pauseOnLostFocus = savedPause;
        pauseSaved = false;
        releaseKeys();
        setKillAura(false);
        saveStats();
        closeWindow();
    }

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onPacket = event -> {
        if (event.getPacket() instanceof ClientboundSystemChatPacket) {
            ClientboundSystemChatPacket chat = (ClientboundSystemChatPacket) event.getPacket();
            if (chat.overlay()) return;
            String chatText = StringUtil.stripColor(chat.content().getString());
            if (chatText.contains("MYSTIC ITEM! dropped from killing")) {
                mysticChatAt = System.currentTimeMillis();
                lootUntil = mysticChatAt + 5000;
            }
            if (chatText.contains("TACTICAL INSERTION!") && chatText.contains("Spawn set")) insertionConfirmed = true;
            pendingChat.add(chatText);
        } else if (event.getPacket() instanceof ClientboundAddEntityPacket) {
            ClientboundAddEntityPacket spawn = (ClientboundAddEntityPacket) event.getPacket();
            if (spawn.getType() == net.minecraft.world.entity.EntityTypes.ITEM) {
                long t = System.currentTimeMillis();
                itemSpawns.put(spawn.getId(), t);
                itemSpawns.values().removeIf(v -> t - v > 15000);
            }
        } else if (event.getPacket() instanceof ClientboundRespawnPacket) {
            respawnPacket = true;
        } else if (event.getPacket() instanceof ClientboundPlayerPositionPacket && mc.player != null) {
            ClientboundPlayerPositionPacket tp = (ClientboundPlayerPositionPacket) event.getPacket();
            Vec3 to = tp.change().position();
            double x = to.x, y = to.y, z = to.z;
            if (tp.relatives().contains(net.minecraft.world.entity.Relative.X)) x += mc.player.getX();
            if (tp.relatives().contains(net.minecraft.world.entity.Relative.Y)) y += mc.player.getY();
            if (tp.relatives().contains(net.minecraft.world.entity.Relative.Z)) z += mc.player.getZ();
            double dx = x - mc.player.getX(), dy = y - mc.player.getY(), dz = z - mc.player.getZ();
            if (dx * dx + dy * dy + dz * dz > 25) teleportDeath = true;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        ticks++;
        if (lastWorld != mc.level) {
            lastWorld = mc.level;
            pendingChat.clear();
            haveGoal = false;
        }

        handleChat();

        long nowMs = System.currentTimeMillis();
        if (lastTickAt != 0) stat("runtimeMs", Math.min(1000, nowMs - lastTickAt));
        lastTickAt = nowMs;
        if (insertionConfirmed) {
            insertionConfirmed = false;
            insertionActive = true;
            insertionTries = 0;
            stat("insertions", 1);
            info("Tactical Insertion set for your next death");
        }
        if (statsDirty && nowMs - lastSave > 5000) saveStats();
        updateDashboard();

        if (restoreSlot >= 0) {
            mc.player.getInventory().setSelectedSlot(restoreSlot);
            restoreSlot = -1;
        }

        // death bookkeeping: remember when we died so Tactical Insertion goes down after the delay
        boolean alive = mc.player.getHealth() > 0 && !!mc.player.isAlive();
        if ((wasAlive && !alive) || respawnPacket || teleportDeath) {
            onDeath();
            respawnPacket = false;
            teleportDeath = false;
        }
        wasAlive = alive;
        if (!alive || mc.gui.screen() instanceof DeathScreen) {
            releaseKeys();
            if (mc.gui.screen() instanceof DeathScreen) mc.player.respawn();
            return;
        }
        mc.options.pauseOnLostFocus = false;
        if (mc.gui.screen() instanceof PauseScreen) mc.gui.setScreen(null);

        readScoreboard();
        trackStreak();
        if (pitOnly.getValue() && !inPit) {
            releaseKeys();
            setKillAura(false);
            status = "not in pit";
            return;
        }

        if (friendDiamond.getValue() && ticks % 20 == 0) friendDiamondPlayers();
        if (doOof.getValue()) oof();
        if (mc.gui.screen() == null) {
            if (doCheckout.getValue()) checkout();
            if (keepBlobs.getValue()) blobCheck();
            if (doInsertion.getValue()) insertion();
        }

        if (doMove.getValue()) {
            setKillAura(true);
            move();
        } else {
            releaseKeys();
        }
    };

    // ---------------------------------------------------------------- scoreboard

    private void readScoreboard() {
        Scoreboard sb = mc.level.getScoreboard();
        Objective obj = sb == null ? null : sb.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
        if (obj == null) {
            inPit = false;
            return;
        }
        inPit = StringUtil.stripColor(obj.getDisplayName().getString()).toUpperCase().contains("PIT");
        double newStreak = 0, newBounty = 0;
        for (PlayerScoreEntry score : sb.listPlayerScores(obj)) {
            if (score.isHidden()) continue;
            PlayerTeam team = sb.getPlayersTeam(score.owner());
            String line = StringUtil.stripColor(PlayerTeam.formatNameForTeam(team, score.ownerName()).getString());
            Matcher m = STREAK.matcher(line);
            if (m.find()) newStreak = parse(m.group(1));
            m = BOUNTY.matcher(line);
            if (m.find()) newBounty = parse(m.group(1));
        }
        streak = newStreak;
        bounty = newBounty;
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- death / streak tracking

    /** Death = health hit 0, a respawn packet, or a server teleport over 5 blocks. Debounced since these usually arrive together. */
    private void onDeath() {
        long now = System.currentTimeMillis();
        if (now - lastDeathAt < 3000) return;
        lastDeathAt = now;
        // the scoreboard may already show 0 by now, so use the highest streak of the last few seconds
        double peak = 0;
        for (double[] h : streakHistory)
            if (now - h[0] <= 4000) peak = Math.max(peak, h[1]);
        peak = Math.max(peak, lastStreakSeen);
        streakHistory.clear();

        insertionActive = false;
        insertionTries = 0;
        insertionConfirmed = false;
        insertionRetryAt = 0;
        insertionAt = now + (long) (insertionDelay.getValue().getInput() * 1000);
        coState = 0;
        lootUntil = 0;
        stat("deaths", 1);

        if (peak >= 400) { // uberdrops are given on death at 400+ streak
            stat("uberdrops", 1);
            info("Uberdrop! died at streak " + (int) peak + " (" + (int) SESSION.get("uberdrops") + " this session)");
        }
        boolean banked = now - lastOofAt < 15000;
        if (banked) {
            stat("bankedStreak", peak);
            info("Streak of " + (int) peak + " banked with /oof");
        } else if (peak >= prematureMin.getValue().getInput()) {
            stat("premature", 1);
            stat("prematureLost", peak);
            bigAlert("PREMATURE STREAK RESET!  Lost a " + (int) peak + " streak (" + (int) SESSION.get("premature") + " this session)");
        } else if (notifyEvents.getValue()) {
            notifyDesktop("AutoUber: you died", "Streak was " + (int) peak);
        }
        lastStreakSeen = 0;
        lastIncreaseAt = now;
        stallNotified = false;
    }

    private void trackStreak() {
        long now = System.currentTimeMillis();
        if (!streakBaselined) { // first reading: don't count a streak we joined with as kills or uberdrops
            streakBaselined = true;
            lastStreakSeen = streak;
            lastIncreaseAt = now;
            return;
        }
        streakHistory.addLast(new double[]{now, streak});
        while (!streakHistory.isEmpty() && now - streakHistory.peekFirst()[0] > 6000) streakHistory.pollFirst();

        if (streak > lastStreakSeen) {
            lastIncreaseAt = now;
            stallNotified = false;
            if (streak - lastStreakSeen >= 0.99) stat("kills", 1);
        }
        if (streak < lastStreakSeen) lastIncreaseAt = now; // dropped (oof / death): start timing again
        statMax("bestStreak", streak);
        lastStreakSeen = streak;
        if (notifyEvents.getValue() && !stallNotified && streak > 0
                && now - lastIncreaseAt > stallSeconds.getValue().getInput() * 1000) {
            stallNotified = true;
            notifyDesktop("AutoUber: streak stalled", "Streak stuck at " + (int) streak + " for "
                    + (int) stallSeconds.getValue().getInput() + "s");
        }
    }

    // ---------------------------------------------------------------- mystic drops

    // ---------------------------------------------------------------- path probe
    // The pathfinder is only asked "is there a short, plain path?" so we ignore targets behind walls. Its path is
    // never followed; movement stays our own sprint jump steering.

    private boolean canSee(double tx, double ty, double tz) {
        HitResult mop = PlayerUtils.rayTraceBlocks(mc.player.getEyePosition(1f), new Vec3(tx, ty, tz));
        return mop == null || mop.getType() != HitResult.Type.BLOCK;
    }

    /** Worth going for: in plain sight and close, or the planner finds a short path without parkour, climbing or pillaring. */
    private boolean worthwhile(Entity e, double ty) {
        if (!pathCheck.getValue()) return true;
        boolean visible = canSee(e.getX(), ty, e.getZ());
        double flat = Math.hypot(e.getX() - mc.player.getX(), e.getZ() - mc.player.getZ());
        if (visible && flat <= 3) return true;
        int key = e.getId();
        long now = System.currentTimeMillis();
        double[] v = verdicts.get(key);
        boolean fresh = v != null && now - v[1] < 3000 && Math.hypot(v[2] - e.getX(), v[3] - e.getZ()) < 3;
        if (!fresh && probing.size() < 2 && probing.add(key)) startProbe(key, e.getX(), ty, e.getZ(), flat);
        if (v == null) return visible; // no verdict yet: trust what we can see
        return v[0] > 0;
    }

    private void startProbe(final int key, final double tx, final double ty, final double tz, final double flat) {
        final int sx = Mth.floor(mc.player.getX()), sy = Mth.floor(mc.player.getY() + 1e-3),
                sz = Mth.floor(mc.player.getZ());
        final double feet = mc.player.getY();
        final BlockView view = new McWorldView(mc.level);
        final double detour = maxDetour.getValue().getInput();
        Thread t = new Thread(() -> {
            boolean ok = false;
            try {
                Tuning tun = new Tuning();
                tun.searchMillis = 150;
                tun.maxNodes = 8000;
                Planner.Caps caps = new Planner.Caps();
                caps.hop = true;
                Planner.Result r = Planner.plan(view, sx, sy, sz, feet, new Goal.NearPoint(tx, ty, tz, 2.5), tun, caps,
                        new java.util.HashSet<Long>(), null);
                ok = r != null && r.complete && r.steps.size() - 1 <= flat * detour + 4;
                if (ok)
                    for (Step s : r.steps)
                        if (s.type == Step.PARKOUR || s.type == Step.CLIMB_UP || s.type == Step.CLIMB_DOWN
                                || s.type == Step.PILLAR || s.type == Step.BRIDGE || s.type == Step.MINE_DOWN) ok = false;
            } catch (Throwable ignored) {
            }
            long now = System.currentTimeMillis();
            verdicts.put(key, new double[]{ok ? 1 : 0, now, tx, tz});
            verdicts.values().removeIf(v -> now - v[1] > 30000);
            probing.remove(key);
        }, "AutoUber-probe");
        t.setDaemon(true);
        t.start();
    }

    /** Nearest dropped gold sword / bow / leather pants. The chat broadcast only happens for drops that are not ours. */
    private ItemEntity findMystic() {
        ItemEntity best = null;
        double bestDist = 50;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ItemEntity) || !e.isAlive()) continue;
            Long spawned = itemSpawns.get(e.getId());
            if (spawned == null || Math.abs(spawned - mysticChatAt) > 1000) continue; // not part of this drop
            ItemStack s = ((ItemEntity) e).getItem();
            if (s == null) continue;
            net.minecraft.world.item.Item it = s.getItem();
            if (it != Items.GOLDEN_SWORD && it != Items.BOW && it != Items.LEATHER_LEGGINGS) continue;
            double d = e.distanceTo(mc.player);
            if (d < bestDist && worthwhile(e, e.getY() + 0.3)) {
                bestDist = d;
                best = (ItemEntity) e;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- /oof

    private void oof() {
        if (streak < oofStreak.getValue().getInput()) return;
        if (System.currentTimeMillis() < oofCooldown) return;
        oofCooldown = System.currentTimeMillis() + 10000;
        lastOofAt = System.currentTimeMillis();
        mc.player.connection.sendChat("/oof");
        stat("oofs", 1);
        info("/oof sent at streak " + (int) streak + " (#" + (int) SESSION.get("oofs") + " this session)");
    }

    // ---------------------------------------------------------------- tactical insertion
    // It has to be down before we die (the server answers "Spawn set for your next death") and has a 60 s cooldown, so
    // we keep trying every 5 s until the server confirms it, then leave it alone until the next death.

    private void insertion() {
        if (insTotal > 0) {
            // rod stays selected for the item's use time (0.5 s); one click shortly after selecting it, like a player would
            insTicks++;
            if (insertionActive || mc.player.getInventory().getItem(insSlot) == null || insTicks >= insTotal) {
                insTotal = 0;
                mc.player.getInventory().setSelectedSlot(insPrev);
                return;
            }
            mc.player.getInventory().setSelectedSlot(insSlot);
            if (insTicks == INSERTION_CLICK_DELAY) clickRod();
            return;
        }
        long now = System.currentTimeMillis();
        if (insertionActive || now < insertionAt || now < insertionRetryAt || insertionTries >= 15) return;
        if (coState != 0) return;
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s != null && s.getItem() == Items.BLAZE_ROD) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            if (!warnedNoRod) PlayerUtils.addWaterMarkedMessageToChat("No Tactical Insertion (blaze rod) in your hotbar");
            warnedNoRod = true;
            return;
        }
        warnedNoRod = false;
        insSlot = slot;
        insPrev = mc.player.getInventory().getSelectedSlot();
        insTotal = Math.max(INSERTION_CLICK_DELAY + 2, (int) Math.round(insertionHold.getValue().getInput() * 20));
        insTicks = 0;
        mc.player.getInventory().setSelectedSlot(slot);
        insertionRetryAt = now + 5000;
        insertionTries++;
        stat("insertionTries", 1);
        if (insertionTries > 1) info("Tactical Insertion not confirmed, retrying (try " + insertionTries + ")");
        if (insertionTries == 15) info("Tactical Insertion gave up for this life (15 tries)");
    }

    private static final int INSERTION_CLICK_DELAY = 2;

    private void clickRod() {
        mc.player.getInventory().setSelectedSlot(insSlot);
        PlayerUtils.swingItem();
        mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    // ---------------------------------------------------------------- self-checkout

    private boolean isCheckout(ItemStack s) {
        if (!isLeggings(s)) return false;
        for (String line : tooltip(s))
            if (StringUtil.stripColor(line).toLowerCase().contains("self-checkout")) return true;
        return false;
    }

    private boolean isLeggings(ItemStack s) {
        return s != null && arsenic.utils.minecraft.ItemUtils.getArmorSlot(s) == net.minecraft.world.entity.EquipmentSlot.LEGS;
    }

    private boolean isPitblob(ItemStack s) {
        for (String line : tooltip(s))
            if (StringUtil.stripColor(line).toLowerCase().contains("pitblob")) return true;
        return false;
    }

    /** The item's tooltip lines as plain strings, the way 1.8's getTooltip gave them. */
    private java.util.List<String> tooltip(ItemStack s) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (net.minecraft.network.chat.Component line : s.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level),
                mc.player, net.minecraft.world.item.TooltipFlag.NORMAL))
            out.add(line.getString());
        return out;
    }

    private void checkout() {
        double threshold = checkoutBounty.getValue().getInput();
        if (bounty < threshold) {
            coTries = 0;
            warnedNoSco = false;
        }
        // did the last attempt clear the bounty?
        if (coVerifyUntil > 0) {
            if (bounty < threshold) {
                coVerifyUntil = 0;
                stat("checkouts", 1);
                info("Self-checkout cleared the bounty (#" + (int) SESSION.get("checkouts") + " this session)");
            } else if (ticks >= coVerifyUntil) {
                coVerifyUntil = 0;
                info("Self-checkout did not clear the bounty - trying again");
            }
        }

        if (coState == 0) {
            if (bounty < threshold || ticks < coCooldown || coVerifyUntil > 0 || coTries >= 5 || insTotal > 0) return;
            int slot = -1;
            for (int i = 0; i < 9; i++)
                if (isCheckout(mc.player.getInventory().getItem(i))) {
                    slot = i;
                    break;
                }
            coCooldown = ticks + 100;
            if (slot < 0) {
                if (!warnedNoSco) PlayerUtils.addWaterMarkedMessageToChat("Bounty §6" + (int) bounty + "g§r but no Self-checkout pants in your hotbar");
                warnedNoSco = true;
                return;
            }
            ItemStack worn = mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
            coWanted = worn.isEmpty() || isCheckout(worn) ? null : StringUtil.stripColor(worn.getHoverName().getString());
            coPrev = mc.player.getInventory().getSelectedSlot();
            coSlot = slot;
            coFix = 0;
            coTries++;
            stat("checkoutTries", 1);
            mc.player.getInventory().setSelectedSlot(slot);
            mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            coState = 1;
            coStart = ticks;
            coLastClick = ticks;
            return;
        }

        if (coState == 1) {
            // first click done: wait for the server to swap our old pants into that slot, then click again to put them back
            mc.player.getInventory().setSelectedSlot(coSlot);
            if (ticks - coStart < 4) return;
            ItemStack s = mc.player.getInventory().getItem(coSlot);
            if (isLeggings(s) && !isCheckout(s)) {
                mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                coLastClick = ticks;
                coState = 2;
            } else if (s == null || ticks - coStart > 40) {
                coState = 2; // nothing swapped into the slot: the check below sorts the pants out
            }
            return;
        }

        // coState 2: make sure our normal (pitblob) pants are back on and the self-checkout is not what we are wearing.
        // Every click toggles the pants, so wait long enough for the server's answer before deciding anything (ping!).
        if (ticks - coLastClick < (coFix == 0 ? 10 : 20)) return;
        ItemStack worn = mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
        if (!worn.isEmpty() && !isCheckout(worn)) {
            finishCheckout(true);
            return;
        }
        int fix = pantsToWear();
        if (fix < 0 || coFix >= 4) {
            finishCheckout(false);
            return;
        }
        coFix++;
        coLastClick = ticks;
        if (fix >= 9) {
            // pants are in the main inventory: swap them into our hotbar slot first, the click comes next round
            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, fix, coSlot, net.minecraft.world.inventory.ContainerInput.SWAP, mc.player);
            info("Self-checkout: moving your pants into the hotbar (try " + coFix + ")");
            return;
        }
        info("Self-checkout: re-equipping your pants (try " + coFix + ")");
        mc.player.getInventory().setSelectedSlot(fix);
        mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    /** Slot (0-8 hotbar, 9-35 inventory) of the pants to put back on: hotbar first, pitblob pants before the ones we were wearing before any others. */
    private int pantsToWear() {
        int best = -1, bestScore = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (!isLeggings(s) || isCheckout(s)) continue;
            int score = (isPitblob(s) ? 4 : 0)
                    + (coWanted != null && coWanted.equals(StringUtil.stripColor(s.getHoverName().getString())) ? 2 : 0)
                    + (i < 9 ? 1 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private void finishCheckout(boolean ok) {
        mc.player.getInventory().setSelectedSlot(coPrev);
        coState = 0;
        coCooldown = ticks + 100; // 5 s before trying again
        coVerifyUntil = ticks + 100;
        if (ok) info("Pants back on after Self-checkout, waiting to see if the bounty cleared");
        else PlayerUtils.addWaterMarkedMessageToChat("Could not put your normal pants back on after Self-checkout - check your pants!");
    }

    // ---------------------------------------------------------------- keep the pitblob pants on

    /** Every 5 s: if what we wear on our legs is not the pitblob pants, right-click them from the hotbar. */
    private void blobCheck() {
        if (ticks % 100 != 0 || coState != 0 || insTotal > 0) return;
        ItemStack worn = mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
        if (worn != null && !isCheckout(worn) && isPitblob(worn)) {
            warnedNoBlob = false;
            return;
        }
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (isLeggings(s) && !isCheckout(s) && isPitblob(s)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            if (!warnedNoBlob) info("Not wearing pitblob pants and none in your hotbar");
            warnedNoBlob = true;
            return;
        }
        warnedNoBlob = false;
        int previous = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
        if (previous != slot) restoreSlot = previous;
        info("Putting your pitblob pants back on");
    }

    // ---------------------------------------------------------------- friends

    private void friendDiamondPlayers() {
        if (mc.getConnection() == null) return;
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            ItemStack chest = p.getItemBySlot(arsenic.utils.minecraft.ItemUtils.armorSlotFromIndex(3 - (2)));
            if (chest == null || chest.getItem() != Items.DIAMOND_CHESTPLATE) continue;
            if (mc.getConnection().getPlayerInfo(p.getUUID()) == null) continue; // NPCs
            if (Arsenic.getArsenic().getFriendManager().add(p.getName().getString()))
                PlayerUtils.addWaterMarkedMessageToChat("Friended §b" + p.getName() + "§r (diamond chestplate)");

        }
    }

    // ---------------------------------------------------------------- movement

    private void move() {
        Player enemy = nearestEnemy();
        double tx, tz, stop;
        if (enemy != null) {
            tx = enemy.getX();
            tz = enemy.getZ();
            stop = STOP_DISTANCE;
            status = "fight";
        } else if (pickupMystics.getValue() && System.currentTimeMillis() < lootUntil && findMystic() != null) {
            ItemEntity drop = findMystic();
            tx = drop.getX();
            tz = drop.getZ();
            stop = 0.4;
            status = "loot";
        } else {
            if (!haveGoal || ticks >= goalRefresh) {
                computeCrowdGoal();
                goalRefresh = ticks + 10;
            }
            tx = goalX;
            tz = goalZ;
            stop = CROWD_STOP;
            status = "crowd";
        }

        double dx = tx - mc.player.getX(), dz = tz - mc.player.getZ();
        boolean slimeInside = avoidSlimes.getValue() && insideAvoidZone(mc.player.getX(), mc.player.getZ());
        if (Math.hypot(dx, dz) <= stop && !slimeInside) {
            releaseKeys();
            return;
        }

        // stuck against something: sidestep for a moment
        if (ticks >= stuckCheck) {
            double moved = Math.hypot(mc.player.getX() - lastX, mc.player.getZ() - lastZ);
            if (stuckCheck != 0 && moved < 0.4 && keysHeld) {
                unstickUntil = ticks + 15;
                unstickSign = -unstickSign;
            }
            lastX = mc.player.getX();
            lastZ = mc.player.getZ();
            stuckCheck = ticks + 20;
        }

        float desired = RotationUtils.yawTo(dx, dz);
        if (ticks < unstickUntil) desired += 90 * unstickSign;

        Float heading = slimeInside ? pickOutward(desired) : pickHeading(desired, false);
        if (heading == null && avoidSlimes.getValue()) {
            // inside a slime zone (or boxed in by them): the zone check can never pass, so just get out the way we are facing away
            Entity slime = nearestSlime();
            float away = slime == null ? desired : RotationUtils.yawTo(mc.player.getX() - slime.getX(), mc.player.getZ() - slime.getZ());
            heading = pickHeading(away, true);
        }
        if (heading == null) {
            status = "blocked";
            releaseKeys();
            return;
        }
        hold(heading);
    }

    /** Inside a slime zone: the heading closest to the goal (the players) that does not move us closer to any slime. */
    private Float pickOutward(float desired) {
        double now = minSlimeDistance(mc.player.getX(), mc.player.getZ());
        int sign = unstickSign;
        for (int off : OFFSETS) {
            for (int s = 0; s < (off == 0 || off == 180 ? 1 : 2); s++) {
                float yaw = desired + off * (s == 0 ? sign : -sign);
                double rad = Math.toRadians(yaw);
                double x = mc.player.getX() - Math.sin(rad) * 1.6, z = mc.player.getZ() + Math.cos(rad) * 1.6;
                if (minSlimeDistance(x, z) >= now && hasGround(x, z) && headingClear(yaw, true)) return yaw;
            }
        }
        return null;
    }

    private double minSlimeDistance(double x, double z) {
        double best = 1e9;
        for (Entity e : mc.level.entitiesForRendering())
            if (e instanceof Slime) best = Math.min(best, Math.hypot(e.getX() - x, e.getZ() - z));
        return best;
    }

    private Float pickHeading(float desired, boolean ignoreZones) {
        int sign = unstickSign;
        for (int off : OFFSETS) {
            float yaw = desired + off * (off == 0 ? 1 : sign);
            if (headingClear(yaw, ignoreZones)) return yaw;
            if (off != 0 && off != 180 && headingClear(desired - off * sign, ignoreZones)) return desired - off * sign;
        }
        return null;
    }

    private boolean headingClear(float yaw, boolean ignoreZones) {
        double rad = Math.toRadians(yaw);
        double sx = -Math.sin(rad), sz = Math.cos(rad);
        for (double d = 0.8; d <= 3.2; d += 0.8) {
            double x = mc.player.getX() + sx * d, z = mc.player.getZ() + sz * d;
            if (!ignoreZones && avoidSlimes.getValue() && hitsAvoidZone(x, z)) return false;
            if (d == 1.6 || d == 3.2) if (!hasGround(x, z)) return false;
        }
        return true;
    }

    private boolean hasGround(double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        int top = Mth.floor(mc.player.getY()) - 1;
        for (int y = top; y >= top - 4 && y >= 0; y--) {
            if (mc.level.getBlockState(new BlockPos(bx, y, bz)).isSolid()) return true;
        }
        return false;
    }

    private AABB avoidBox(Entity slime) {
        return slime.getBoundingBox().inflate(SLIME_MARGIN, SLIME_MARGIN, SLIME_MARGIN);
    }

    private boolean hitsAvoidZone(double x, double z) {
        AABB me = mc.player.getBoundingBox().move(x - mc.player.getX(), 0, z - mc.player.getZ());
        for (Entity e : mc.level.entitiesForRendering())
            if (e instanceof Slime && e.distanceTo(mc.player) < 14 && avoidBox(e).intersects(me)) return true;
        return false;
    }

    private boolean insideAvoidZone(double x, double z) {
        return hitsAvoidZone(x, z);
    }

    private Entity nearestSlime() {
        Entity best = null;
        double bestDist = 1e9;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Slime)) continue;
            double d = e.distanceTo(mc.player);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private Player nearestEnemy() {
        Player best = null;
        double bestDist = chaseRange.getValue().getInput();
        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || p.getHealth() <= 0) continue;
            double d = mc.player.distanceTo(p);
            if (d <= bestDist && TargetManager.isValidTarget(p) && worthwhile(p, p.getY() + p.getBbHeight() / 2)) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /** The player with the most other players within the cluster radius (inside the arena radius around 0 0); goal is their centroid. */
    private void computeCrowdGoal() {
        double arena = arenaRadius.getValue().getInput(), arenaSq = arena * arena;
        double radius = clusterRadius.getValue().getInput(), radiusSq = radius * radius;
        List<Player> pool = new ArrayList<>();
        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || p.getHealth() <= 0) continue;
            if (mc.getConnection() != null && mc.getConnection().getPlayerInfo(p.getUUID()) == null) continue;
            if (p.getX() * p.getX() + p.getZ() * p.getZ() > arenaSq) continue;
            pool.add(p);
        }
        Player bestCentre = null;
        int bestCount = 0;
        double bestOrigin = 1e18;
        for (Player a : pool) {
            if (!worthwhile(a, a.getY() + a.getBbHeight() / 2)) continue;
            int count = 0;
            for (Player b : pool) {
                double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
                if (dx * dx + dz * dz <= radiusSq) count++;
            }
            double origin = a.getX() * a.getX() + a.getZ() * a.getZ();
            if (count > bestCount || (count == bestCount && origin < bestOrigin)) {
                bestCount = count;
                bestCentre = a;
                bestOrigin = origin;
            }
        }
        if (bestCentre == null) {
            goalX = 0;
            goalZ = 0;
        } else {
            double sx = 0, sz = 0;
            int n = 0;
            for (Player b : pool) {
                double dx = bestCentre.getX() - b.getX(), dz = bestCentre.getZ() - b.getZ();
                if (dx * dx + dz * dz <= radiusSq) {
                    sx += b.getX();
                    sz += b.getZ();
                    n++;
                }
            }
            goalX = sx / n;
            goalZ = sz / n;
        }
        haveGoal = true;
    }

    private void hold(float yaw) {
        mc.player.setYRot(yaw);
        mc.player.setXRot(0);
        Options gs = mc.options;
        gs.keyUp.setDown(true);
        gs.keySprint.setDown(true);
        gs.keyJump.setDown(true);
        keysHeld = true;
    }

    private void releaseKeys() {
        if (!keysHeld) return;
        Options gs = mc.options;
        gs.keyUp.setDown(false);
        gs.keySprint.setDown(false);
        gs.keyJump.setDown(false);
        keysHeld = false;
    }

    private void setKillAura(boolean on) {
        KillAura aura = Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class);
        if (aura == null) return;
        if (on) {
            if (!aura.isEnabled()) {
                aura.setEnabled(true);
                enabledKillAura = true;
            }
        } else if (enabledKillAura) {
            aura.setEnabled(false);
            enabledKillAura = false;
        }
    }

    // ---------------------------------------------------------------- chat window

    private void handleChat() {
        String line;
        while ((line = pendingChat.poll()) != null) {
            if (!notifyChat.getValue()) continue;
            int idx = line.indexOf(": ");
            if (idx <= 0 || mc.getConnection() == null) continue;
            String[] words = line.substring(0, idx).trim().split("\\s+");
            String name = words[words.length - 1];
            if (name.equalsIgnoreCase(mc.player.getName().getString())) continue;
            boolean known = false;
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers())
                if (info.getProfile().name().equalsIgnoreCase(name)) {
                    known = true;
                    break;
                }
            if (!known) continue;
            String msg = line.substring(idx + 2);
            if (mentionsOnly.getValue() && !msg.toLowerCase().contains(mc.player.getName().getString().toLowerCase())) continue;
            notifyDesktop(name, msg);
        }
    }

    /** Green progress line in the chat window. */
    private void info(String text) {
        notifyDesktop("AutoUber+", text);
    }

    /** Big bold red line in the chat window. */
    private void bigAlert(String text) {
        notifyDesktop("AutoUber!", text);
    }

    private void notifyDesktop(final String title, final String text) {
        long now = System.currentTimeMillis();
        String key = title + text;
        if (now - lastNotify < 300 && key.equals(lastNotifyText)) return;
        lastNotify = now;
        lastNotifyText = key;
        final boolean event = title.startsWith("AutoUber");
        final boolean mention = !event && mc.player != null
                && text.toLowerCase().contains(mc.player.getName().getString().toLowerCase());
        final String stamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        SwingUtilities.invokeLater(() -> {
            try {
                if (!ensureWindow()) return;
                StyledDocument doc = pane.getStyledDocument();
                if (title.equals("AutoUber!")) {
                    doc.insertString(doc.getLength(), "\n" + stamp + " " + text + "\n\n", styleOf(new Color(255, 60, 60), true, 22));
                } else {
                    doc.insertString(doc.getLength(), stamp + " ", styleOf(new Color(120, 120, 130), false, 14));
                    if (title.equals("AutoUber+")) {
                        doc.insertString(doc.getLength(), text + "\n", styleOf(new Color(85, 255, 85), false, 14));
                    } else if (event) {
                        doc.insertString(doc.getLength(), title.replace("AutoUber: ", "") + " - " + text + "\n", styleOf(new Color(255, 85, 85), true, 14));
                    } else {
                        doc.insertString(doc.getLength(), title + ": ", styleOf(new Color(85, 255, 255), true, 14));
                        doc.insertString(doc.getLength(), text + "\n", styleOf(mention ? new Color(255, 170, 0) : new Color(230, 230, 235), mention, 14));
                    }
                }
                if (doc.getLength() > 60000) doc.remove(0, doc.getLength() - 40000);
                pane.setCaretPosition(doc.getLength());
            } catch (Throwable e) {
                windowFailed = true;
                PlayerUtils.addWaterMarkedMessageToChat("Chat window failed: " + e);
            }
        });
    }

    private SimpleAttributeSet styleOf(Color c, boolean bold, int size) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, c);
        StyleConstants.setBold(a, bold);
        StyleConstants.setFontFamily(a, "Consolas");
        StyleConstants.setFontSize(a, size);
        return a;
    }

    /** Must run on the Swing thread. */
    private boolean ensureWindow() {
        if (GraphicsEnvironment.isHeadless() && !windowFailed)
            PlayerUtils.addWaterMarkedMessageToChat("Chat window unavailable: Java is running headless");
        if (windowFailed || GraphicsEnvironment.isHeadless()) {
            windowFailed = true;
            return false;
        }
        if (frame == null) buildWindow();
        if (!frame.isVisible()) {
            frame.setVisible(true);
            frame.toFront();
        }
        return true;
    }

    private void openWindow() {
        SwingUtilities.invokeLater(() -> {
            try {
                ensureWindow();
            } catch (Throwable e) {
                windowFailed = true;
                PlayerUtils.addWaterMarkedMessageToChat("Chat window failed: " + e);
            }
        });
    }

    /** Ordinary window (own taskbar entry) that does not grab focus when it opens: live stats on top, chat below. */
    private void buildWindow() {
        frame = new JFrame("AutoUber");
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.setAutoRequestFocus(false);
        frame.setAlwaysOnTop(windowOnTop.getValue());
        dashboard = new JTextArea();
        dashboard.setEditable(false);
        dashboard.setFont(new Font("Consolas", Font.PLAIN, 13));
        dashboard.setBackground(new Color(18, 18, 22));
        dashboard.setForeground(new Color(220, 220, 225));
        dashboard.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 8, 6, 8));
        lastDashboard = "";
        pane = pane == null ? new JTextPane() : pane;
        pane.setEditable(false);
        pane.setBackground(new Color(24, 24, 28));
        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(null);
        frame.setLayout(new BorderLayout());
        frame.add(dashboard, BorderLayout.NORTH);
        frame.add(scroll, BorderLayout.CENTER);
        frame.setSize(520, 700);
        frame.setLocationByPlatform(true);
    }

    /** Hides the window but keeps its chat log, so it is all still there when the module is switched back on. */
    private void closeWindow() {
        SwingUtilities.invokeLater(() -> {
            if (frame != null) frame.setVisible(false);
        });
    }

    private String buildDashboard() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("LIVE  streak %s (best %s)   bounty %sg   %s%n", num(streak), num(LIFETIME.get("bestStreak")), num(bounty), status));
        sb.append(String.format("      tactical: %s   self-checkout: %s%n",
                insertionActive ? "SET" : (insertionTries > 0 ? "retrying #" + insertionTries : "waiting"),
                coState != 0 ? "working" : (coVerifyUntil > 0 ? "verifying" : (bounty >= checkoutBounty.getValue().getInput() ? "ready" : "idle"))));
        sb.append(String.format("%n%-18s %14s %16s%n", "", "SESSION", "ALL TIME"));
        String[][] rows = {
                {"Kills", "kills"}, {"Deaths", "deaths"}, {"/oofs", "oofs"}, {"Streak banked", "bankedStreak"},
                {"Uberdrops", "uberdrops"}, {"Premature resets", "premature"}, {"Streak lost", "prematureLost"},
                {"Tactical set", "insertions"}, {"Tactical tries", "insertionTries"},
                {"Checkouts", "checkouts"}, {"Checkout tries", "checkoutTries"}, {"Best streak", "bestStreak"}};
        for (String[] r : rows)
            sb.append(String.format("%-18s %14s %16s%n", r[0], num(SESSION.get(r[1])), num(LIFETIME.get(r[1]))));
        sb.append(String.format("%-18s %14s %16s", "Time running", duration(SESSION.get("runtimeMs")), duration(LIFETIME.get("runtimeMs"))));
        return sb.toString();
    }

    /** Runs every tick; only touches Swing when the text actually changed. */
    private void updateDashboard() {
        if (frame == null || !frame.isVisible()) return;
        final boolean top = windowOnTop.getValue();
        if (frame.isAlwaysOnTop() != top) SwingUtilities.invokeLater(() -> {
            if (frame != null) frame.setAlwaysOnTop(top);
        });
        final String text = buildDashboard();
        if (text.equals(lastDashboard)) return;
        lastDashboard = text;
        SwingUtilities.invokeLater(() -> {
            if (dashboard != null) dashboard.setText(text);
        });
    }

    // ---------------------------------------------------------------- .uber command

    {
        registerCommand(new UberCommand());
    }

    private static void sendStats(String title, Stats s) {
        PlayerUtils.addMessageToChat("§7[§cA§7]§r §c" + title + "§r  kills " + num(s.get("kills")) + ", deaths " + num(s.get("deaths"))
                + ", /oofs " + num(s.get("oofs")) + ", banked " + num(s.get("bankedStreak")) + ", uberdrops " + num(s.get("uberdrops")));
        PlayerUtils.addMessageToChat("§7        premature resets " + num(s.get("premature")) + " (lost " + num(s.get("prematureLost"))
                + "), tactical " + num(s.get("insertions")) + "/" + num(s.get("insertionTries")) + ", checkouts " + num(s.get("checkouts"))
                + "/" + num(s.get("checkoutTries")) + ", best " + num(s.get("bestStreak")) + ", " + duration(s.get("runtimeMs")));
    }

    /** .uber shows session + all-time stats, .uber window opens the window, .uber file shows where the stats are saved. */
    @CommandInfo(name = "uber", args = {"stats/window/file"}, help = "shows AutoUber session and all-time stats", aliases = {"uberstats"})
    private class UberCommand extends Command {

        @Override
        public void execute(String[] args) {
            loadStats();
            String sub = args.length == 0 ? "stats" : args[0].toLowerCase();
            if (sub.equals("window")) {
                openWindow();
            } else if (sub.equals("file")) {
                PlayerUtils.addWaterMarkedMessageToChat("Stats file: " + statsFile().getAbsolutePath());
            } else {
                sendStats("Session", SESSION);
                sendStats("All time", LIFETIME);
            }
        }

        @Override
        protected List<String> getAutoComplete(String str, int arg, List<String> list) {
            if (arg == 0) {
                list.add("stats");
                list.add("window");
                list.add("file");
            }
            return list;
        }
    }

    @Override
    public String getHudInfo() {
        return status;
    }
}

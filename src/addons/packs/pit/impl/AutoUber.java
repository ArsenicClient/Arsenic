
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
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StringUtils;
import net.minecraft.util.Vec3;

import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.network.play.server.S0EPacketSpawnObject;
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
    public final DoubleProperty checkoutBounty = new DoubleProperty("Self-checkout Bounty", new DoubleValue(500, 20000, 5000, 100));
    public final DoubleProperty chaseRange = new DoubleProperty("Chase Range", new DoubleValue(2, 20, 7, 0.5));
    public final DoubleProperty arenaRadius = new DoubleProperty("Arena Radius", new DoubleValue(10, 100, 45, 1));
    public final DoubleProperty clusterRadius = new DoubleProperty("Cluster Radius", new DoubleValue(3, 20, 8, 0.5));
    public final BooleanProperty doOof = new BooleanProperty("Auto /oof", true);
    public final BooleanProperty doInsertion = new BooleanProperty("Tactical Insertion", true);
    public final BooleanProperty doCheckout = new BooleanProperty("Self-checkout", true);
    public final BooleanProperty doMove = new BooleanProperty("Move To Crowd", true);
    public final BooleanProperty avoidSlimes = new BooleanProperty("Avoid Slimes", true);
    public final BooleanProperty friendDiamond = new BooleanProperty("Friend Diamond Chest", true);
    public final BooleanProperty notifyChat = new BooleanProperty("Notify Chat", true);
    public final BooleanProperty mentionsOnly = new BooleanProperty("Only Mentions", false);
    public final BooleanProperty pitOnly = new BooleanProperty("Pit Only", true);
    public final BooleanProperty pickupMystics = new BooleanProperty("Pick Up Mystics", true);
    public final BooleanProperty notifyEvents = new BooleanProperty("Notify Death/Stall", true);
    public final DoubleProperty stallSeconds = new DoubleProperty("Streak Stall (s)", new DoubleValue(10, 300, 40, 5));

    private static final Pattern STREAK = Pattern.compile("Streak:\\s*([\\d,]+(?:\\.\\d+)?)");
    private static final Pattern BOUNTY = Pattern.compile("Bounty:\\s*([\\d,]+)\\s*g");
    private static final double SLIME_MARGIN = 1.0;
    private static final double STOP_DISTANCE = 2.2;
    private static final double CROWD_STOP = 3.0;
    private static final int[] OFFSETS = {0, 20, -20, 40, -40, 60, -60, 80, -80, 100, -100, 125, -125, 150, -150, 180};

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
    private boolean insertionPending;
    private long insertionAt;
    private int restoreSlot = -1;
    private boolean warnedNoRod;

    // oof
    private long oofCooldown;

    // self-checkout
    private int coState;
    private int coSlot, coPrev;
    private long coStart, coCooldown;

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

    // desktop notifications
    private TrayIcon trayIcon;
    private boolean trayFailed;
    private long lastNotify;
    private String lastNotifyText = "";

    @Override
    protected void onEnable() {
        pendingChat.clear();
        respawnPacket = false;
        coState = 0;
        restoreSlot = -1;
        insertionPending = false;
        wasAlive = true;
        haveGoal = false;
        if (mc.gameSettings != null) {
            savedPause = mc.gameSettings.pauseOnLostFocus;
            pauseSaved = true;
            mc.gameSettings.pauseOnLostFocus = false;
        }
    }

    @Override
    protected void onDisable() {
        if (mc.gameSettings != null && pauseSaved) mc.gameSettings.pauseOnLostFocus = savedPause;
        pauseSaved = false;
        releaseKeys();
        setKillAura(false);
        removeTray();
    }

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onPacket = event -> {
        if (event.getPacket() instanceof S02PacketChat) {
            S02PacketChat chat = (S02PacketChat) event.getPacket();
            if (chat.getType() == 2) return;
            String chatText = StringUtils.stripControlCodes(chat.getChatComponent().getUnformattedText());
            if (chatText.contains("MYSTIC ITEM! dropped from killing")) {
                mysticChatAt = System.currentTimeMillis();
                lootUntil = mysticChatAt + 5000;
            }
            pendingChat.add(chatText);
        } else if (event.getPacket() instanceof S0EPacketSpawnObject) {
            S0EPacketSpawnObject spawn = (S0EPacketSpawnObject) event.getPacket();
            if (spawn.getType() == 2) {
                long t = System.currentTimeMillis();
                itemSpawns.put(spawn.getEntityID(), t);
                itemSpawns.values().removeIf(v -> t - v > 15000);
            }
        } else if (event.getPacket() instanceof S07PacketRespawn) {
            respawnPacket = true;
        } else if (event.getPacket() instanceof S08PacketPlayerPosLook && mc.thePlayer != null) {
            S08PacketPlayerPosLook tp = (S08PacketPlayerPosLook) event.getPacket();
            double x = tp.getX(), y = tp.getY(), z = tp.getZ();
            if (tp.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.X)) x += mc.thePlayer.posX;
            if (tp.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.Y)) y += mc.thePlayer.posY;
            if (tp.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.Z)) z += mc.thePlayer.posZ;
            double dx = x - mc.thePlayer.posX, dy = y - mc.thePlayer.posY, dz = z - mc.thePlayer.posZ;
            if (dx * dx + dy * dy + dz * dz > 25) teleportDeath = true;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        ticks++;
        if (lastWorld != mc.theWorld) {
            lastWorld = mc.theWorld;
            pendingChat.clear();
            haveGoal = false;
        }

        handleChat();

        if (restoreSlot >= 0) {
            mc.thePlayer.inventory.currentItem = restoreSlot;
            restoreSlot = -1;
        }

        // death bookkeeping: remember when we died so Tactical Insertion goes down after the delay
        boolean alive = mc.thePlayer.getHealth() > 0 && !mc.thePlayer.isDead;
        if ((wasAlive && !alive) || respawnPacket || teleportDeath) {
            onDeath();
            respawnPacket = false;
            teleportDeath = false;
        }
        wasAlive = alive;
        if (!alive || mc.currentScreen instanceof GuiGameOver) {
            releaseKeys();
            if (mc.currentScreen instanceof GuiGameOver) mc.thePlayer.respawnPlayer();
            return;
        }
        mc.gameSettings.pauseOnLostFocus = false;
        if (mc.currentScreen instanceof GuiIngameMenu) mc.displayGuiScreen(null);

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
        if (mc.currentScreen == null) {
            if (doCheckout.getValue()) checkout();
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
        Scoreboard sb = mc.theWorld.getScoreboard();
        ScoreObjective obj = sb == null ? null : sb.getObjectiveInDisplaySlot(1);
        if (obj == null) {
            inPit = false;
            return;
        }
        inPit = StringUtils.stripControlCodes(obj.getDisplayName()).toUpperCase().contains("PIT");
        double newStreak = 0, newBounty = 0;
        for (Score score : sb.getSortedScores(obj)) {
            ScorePlayerTeam team = sb.getPlayersTeam(score.getPlayerName());
            String line = StringUtils.stripControlCodes(ScorePlayerTeam.formatPlayerName(team, score.getPlayerName()));
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
        insertionPending = doInsertion.getValue();
        insertionAt = now + (long) (insertionDelay.getValue().getInput() * 1000);
        coState = 0;
        lootUntil = 0;
        if (notifyEvents.getValue())
            notifyDesktop("AutoUber: you died", "Streak was " + (int) lastStreakSeen);
        lastStreakSeen = 0;
        lastIncreaseAt = now;
        stallNotified = false;
    }

    private void trackStreak() {
        long now = System.currentTimeMillis();
        if (streak > lastStreakSeen) {
            lastIncreaseAt = now;
            stallNotified = false;
        }
        if (streak < lastStreakSeen) lastIncreaseAt = now; // dropped (oof / death): start timing again
        lastStreakSeen = streak;
        if (notifyEvents.getValue() && !stallNotified && streak > 0
                && now - lastIncreaseAt > stallSeconds.getValue().getInput() * 1000) {
            stallNotified = true;
            notifyDesktop("AutoUber: streak stalled", "Streak stuck at " + (int) streak + " for "
                    + (int) stallSeconds.getValue().getInput() + "s");
        }
    }

    // ---------------------------------------------------------------- mystic drops

    /** Nearest dropped gold sword / bow / leather pants. The chat broadcast only happens for drops that are not ours. */
    private EntityItem findMystic() {
        EntityItem best = null;
        double bestDist = 50;
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (!(e instanceof EntityItem) || e.isDead) continue;
            Long spawned = itemSpawns.get(e.getEntityId());
            if (spawned == null || Math.abs(spawned - mysticChatAt) > 1000) continue; // not part of this drop
            ItemStack s = ((EntityItem) e).getEntityItem();
            if (s == null) continue;
            net.minecraft.item.Item it = s.getItem();
            if (it != Items.golden_sword && it != Items.bow && it != Items.leather_leggings) continue;
            double d = e.getDistanceToEntity(mc.thePlayer);
            if (d < bestDist) {
                bestDist = d;
                best = (EntityItem) e;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- /oof

    private void oof() {
        if (streak < oofStreak.getValue().getInput()) return;
        if (System.currentTimeMillis() < oofCooldown) return;
        oofCooldown = System.currentTimeMillis() + 10000;
        mc.thePlayer.sendChatMessage("/oof");
        PlayerUtils.addWaterMarkedMessageToChat("Streak §c" + (int) streak + "§r - /oof");
    }

    // ---------------------------------------------------------------- tactical insertion

    private void insertion() {
        if (!insertionPending || System.currentTimeMillis() < insertionAt) return;
        if (!mc.thePlayer.onGround || coState != 0) return;
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
            if (s != null && s.getItem() == Items.blaze_rod) {
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
        BlockPos ground = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 0.5, mc.thePlayer.posZ);
        Vec3 hit = new Vec3(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5);
        int previous = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = slot;
        mc.thePlayer.swingItem();
        mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), ground, EnumFacing.UP, hit);
        if (previous != slot) restoreSlot = previous;
        insertionPending = false;
    }

    // ---------------------------------------------------------------- self-checkout

    private boolean isCheckout(ItemStack s) {
        if (s == null || !(s.getItem() instanceof ItemArmor) || ((ItemArmor) s.getItem()).armorType != 2) return false;
        for (String line : s.getTooltip(mc.thePlayer, false))
            if (StringUtils.stripControlCodes(line).toLowerCase().contains("self-checkout")) return true;
        return false;
    }

    private void checkout() {
        if (coState == 0) {
            if (bounty < checkoutBounty.getValue().getInput() || ticks < coCooldown) return;
            int slot = -1;
            for (int i = 0; i < 9; i++)
                if (isCheckout(mc.thePlayer.inventory.getStackInSlot(i))) {
                    slot = i;
                    break;
                }
            coCooldown = ticks + 200;
            if (slot < 0) {
                PlayerUtils.addWaterMarkedMessageToChat("Bounty §6" + (int) bounty + "g§r but no Self-checkout pants in your hotbar");
                return;
            }
            coPrev = mc.thePlayer.inventory.currentItem;
            coSlot = slot;
            mc.thePlayer.inventory.currentItem = slot;
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getStackInSlot(slot));
            coState = 1;
            coStart = ticks;
            return;
        }
        // first click done: wait for the server to swap our old pants into that slot, then click again to put them back
        mc.thePlayer.inventory.currentItem = coSlot;
        if (ticks - coStart < 4) return;
        ItemStack s = mc.thePlayer.inventory.getStackInSlot(coSlot);
        boolean oldPants = s != null && s.getItem() instanceof ItemArmor && ((ItemArmor) s.getItem()).armorType == 2 && !isCheckout(s);
        if (oldPants) {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, s);
            finishCheckout(true);
        } else if (s == null || ticks - coStart > 40) {
            finishCheckout(s == null);
        }
    }

    private void finishCheckout(boolean ok) {
        mc.thePlayer.inventory.currentItem = coPrev;
        coState = 0;
        coCooldown = ticks + 200;
        if (!ok) PlayerUtils.addWaterMarkedMessageToChat("Self-checkout swap did not finish; check your pants");
    }

    // ---------------------------------------------------------------- friends

    private void friendDiamondPlayers() {
        if (mc.getNetHandler() == null) return;
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer) continue;
            ItemStack chest = p.getCurrentArmor(2);
            if (chest == null || chest.getItem() != Items.diamond_chestplate) continue;
            if (mc.getNetHandler().getPlayerInfo(p.getUniqueID()) == null) continue; // NPCs
            if (Arsenic.getArsenic().getFriendManager().add(p.getName()))
                PlayerUtils.addWaterMarkedMessageToChat("Friended §b" + p.getName() + "§r (diamond chestplate)");
        }
    }

    // ---------------------------------------------------------------- movement

    private void move() {
        EntityPlayer enemy = nearestEnemy();
        double tx, tz, stop;
        if (enemy != null) {
            tx = enemy.posX;
            tz = enemy.posZ;
            stop = STOP_DISTANCE;
            status = "fight";
        } else if (pickupMystics.getValue() && System.currentTimeMillis() < lootUntil && findMystic() != null) {
            EntityItem drop = findMystic();
            tx = drop.posX;
            tz = drop.posZ;
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

        double dx = tx - mc.thePlayer.posX, dz = tz - mc.thePlayer.posZ;
        boolean slimeInside = avoidSlimes.getValue() && insideAvoidZone(mc.thePlayer.posX, mc.thePlayer.posZ);
        if (Math.hypot(dx, dz) <= stop && !slimeInside) {
            releaseKeys();
            return;
        }

        // stuck against something: sidestep for a moment
        if (ticks >= stuckCheck) {
            double moved = Math.hypot(mc.thePlayer.posX - lastX, mc.thePlayer.posZ - lastZ);
            if (stuckCheck != 0 && moved < 0.4 && keysHeld) {
                unstickUntil = ticks + 15;
                unstickSign = -unstickSign;
            }
            lastX = mc.thePlayer.posX;
            lastZ = mc.thePlayer.posZ;
            stuckCheck = ticks + 20;
        }

        float desired = RotationUtils.yawTo(dx, dz);
        if (slimeInside) {
            Entity slime = nearestSlime();
            if (slime != null) desired = RotationUtils.yawTo(mc.thePlayer.posX - slime.posX, mc.thePlayer.posZ - slime.posZ);
        }
        if (ticks < unstickUntil) desired += 90 * unstickSign;

        Float heading = pickHeading(desired);
        if (heading == null) {
            status = "blocked";
            releaseKeys();
            return;
        }
        hold(heading);
    }

    private Float pickHeading(float desired) {
        int sign = unstickSign;
        for (int off : OFFSETS) {
            float yaw = desired + off * (off == 0 ? 1 : sign);
            if (headingClear(yaw)) return yaw;
            if (off != 0 && off != 180 && headingClear(desired - off * sign)) return desired - off * sign;
        }
        return null;
    }

    private boolean headingClear(float yaw) {
        double rad = Math.toRadians(yaw);
        double sx = -Math.sin(rad), sz = Math.cos(rad);
        for (double d = 0.8; d <= 3.2; d += 0.8) {
            double x = mc.thePlayer.posX + sx * d, z = mc.thePlayer.posZ + sz * d;
            if (avoidSlimes.getValue() && hitsAvoidZone(x, z)) return false;
            if (d == 1.6 || d == 3.2) if (!hasGround(x, z)) return false;
        }
        return true;
    }

    private boolean hasGround(double x, double z) {
        int bx = MathHelper.floor_double(x), bz = MathHelper.floor_double(z);
        int top = MathHelper.floor_double(mc.thePlayer.posY) - 1;
        for (int y = top; y >= top - 4 && y >= 0; y--) {
            if (mc.theWorld.getBlockState(new BlockPos(bx, y, bz)).getBlock().getMaterial().blocksMovement()) return true;
        }
        return false;
    }

    private AxisAlignedBB avoidBox(Entity slime) {
        return slime.getEntityBoundingBox().expand(SLIME_MARGIN, SLIME_MARGIN, SLIME_MARGIN);
    }

    private boolean hitsAvoidZone(double x, double z) {
        AxisAlignedBB me = mc.thePlayer.getEntityBoundingBox().offset(x - mc.thePlayer.posX, 0, z - mc.thePlayer.posZ);
        for (Entity e : mc.theWorld.loadedEntityList)
            if (e instanceof EntitySlime && e.getDistanceToEntity(mc.thePlayer) < 14 && avoidBox(e).intersectsWith(me)) return true;
        return false;
    }

    private boolean insideAvoidZone(double x, double z) {
        return hitsAvoidZone(x, z);
    }

    private Entity nearestSlime() {
        Entity best = null;
        double bestDist = 1e9;
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (!(e instanceof EntitySlime)) continue;
            double d = e.getDistanceToEntity(mc.thePlayer);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private EntityPlayer nearestEnemy() {
        EntityPlayer best = null;
        double bestDist = chaseRange.getValue().getInput();
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.isDead || p.getHealth() <= 0) continue;
            double d = mc.thePlayer.getDistanceToEntity(p);
            if (d <= bestDist && TargetManager.isValidTarget(p)) {
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
        List<EntityPlayer> pool = new ArrayList<>();
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.isDead || p.getHealth() <= 0) continue;
            if (mc.getNetHandler() != null && mc.getNetHandler().getPlayerInfo(p.getUniqueID()) == null) continue;
            if (p.posX * p.posX + p.posZ * p.posZ > arenaSq) continue;
            pool.add(p);
        }
        EntityPlayer bestCentre = null;
        int bestCount = 0;
        double bestOrigin = 1e18;
        for (EntityPlayer a : pool) {
            int count = 0;
            for (EntityPlayer b : pool) {
                double dx = a.posX - b.posX, dz = a.posZ - b.posZ;
                if (dx * dx + dz * dz <= radiusSq) count++;
            }
            double origin = a.posX * a.posX + a.posZ * a.posZ;
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
            for (EntityPlayer b : pool) {
                double dx = bestCentre.posX - b.posX, dz = bestCentre.posZ - b.posZ;
                if (dx * dx + dz * dz <= radiusSq) {
                    sx += b.posX;
                    sz += b.posZ;
                    n++;
                }
            }
            goalX = sx / n;
            goalZ = sz / n;
        }
        haveGoal = true;
    }

    private void hold(float yaw) {
        mc.thePlayer.rotationYaw = yaw;
        mc.thePlayer.rotationPitch = 0;
        GameSettings gs = mc.gameSettings;
        KeyBinding.setKeyBindState(gs.keyBindForward.getKeyCode(), true);
        KeyBinding.setKeyBindState(gs.keyBindSprint.getKeyCode(), true);
        KeyBinding.setKeyBindState(gs.keyBindJump.getKeyCode(), true);
        keysHeld = true;
    }

    private void releaseKeys() {
        if (!keysHeld) return;
        GameSettings gs = mc.gameSettings;
        KeyBinding.setKeyBindState(gs.keyBindForward.getKeyCode(), false);
        KeyBinding.setKeyBindState(gs.keyBindSprint.getKeyCode(), false);
        KeyBinding.setKeyBindState(gs.keyBindJump.getKeyCode(), false);
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

    // ---------------------------------------------------------------- chat -> desktop notifications

    private void handleChat() {
        String line;
        while ((line = pendingChat.poll()) != null) {
            if (!notifyChat.getValue()) continue;
            int idx = line.indexOf(": ");
            if (idx <= 0 || mc.getNetHandler() == null) continue;
            String[] words = line.substring(0, idx).trim().split("\\s+");
            String name = words[words.length - 1];
            if (name.equalsIgnoreCase(mc.thePlayer.getName())) continue;
            boolean known = false;
            for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap())
                if (info.getGameProfile().getName().equalsIgnoreCase(name)) {
                    known = true;
                    break;
                }
            if (!known) continue;
            String msg = line.substring(idx + 2);
            if (mentionsOnly.getValue() && !msg.toLowerCase().contains(mc.thePlayer.getName().toLowerCase())) continue;
            notifyDesktop(name, msg);
        }
    }

    private void notifyDesktop(final String title, final String text) {
        long now = System.currentTimeMillis();
        String key = title + text;
        if (now - lastNotify < 300 && key.equals(lastNotifyText)) return;
        lastNotify = now;
        lastNotifyText = key;
        Thread t = new Thread(() -> {
            try {
                synchronized (AutoUber.this) {
                    if (trayFailed) return;
                    if (trayIcon == null) {
                        if (!SystemTray.isSupported()) {
                            trayFailed = true;
                            return;
                        }
                        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                        java.awt.Graphics2D g = img.createGraphics();
                        g.setColor(new java.awt.Color(170, 0, 0));
                        g.fillRect(0, 0, 16, 16);
                        g.dispose();
                        trayIcon = new TrayIcon(img, "AutoUber");
                        SystemTray.getSystemTray().add(trayIcon);
                    }
                    trayIcon.displayMessage(title, text, TrayIcon.MessageType.INFO);
                }
            } catch (Throwable e) {
                trayFailed = true;
            }
        }, "AutoUber-notify");
        t.setDaemon(true);
        t.start();
    }

    private synchronized void removeTray() {
        if (trayIcon != null) {
            try {
                SystemTray.getSystemTray().remove(trayIcon);
            } catch (Throwable ignored) {
            }
            trayIcon = null;
        }
    }

    @Override
    public String getHudInfo() {
        return status;
    }
}

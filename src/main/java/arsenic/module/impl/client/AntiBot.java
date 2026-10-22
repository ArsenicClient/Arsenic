package arsenic.module.impl.client;

import arsenic.module.ModuleTier;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Filters fake players (NPCs, lobby bots, server dummies) out of targeting, ESP and nametags.
 *
 * Signals, each behind its own setting:
 * - Tab: a player who is missing from the tab list after the grace period is a bot. The grace period stops players
 *   whose tab entry arrives late from being flagged.
 * - Stationary: a player missing from the tab list who has not moved for a long time is an NPC.
 * - Name: a name with a space (Minecraft usernames cannot contain one) or an [NPC] tag is always a bot. Looser name
 *   patterns only count for players who are also missing from the tab list.
 *
 * Singleplayer and LAN have no tab list, so the tab-based checks never run there.
 */
@ModuleInfo(name = "AntiBot", category = ModuleCategory.CLIENT, hidden = true, tier = ModuleTier.QOL)
public class AntiBot extends Module {

    public static BooleanProperty nameChecks = new BooleanProperty("Name Checks", true),
            invisCheck = new BooleanProperty("Invis Checks", false),
            tabChecks = new BooleanProperty("Tab Checks", true),
            stationaryChecks = new BooleanProperty("Stationary Checks", true),
            noPushChecks = new BooleanProperty("NoPush Checks", false),
            pingCheck = new BooleanProperty("Ping Checks", false),
            twiceChecks = new BooleanProperty("Twice UUID Checks", false),
            zeroHealthChecks = new BooleanProperty("Dead Checks", false),
            ticksExistedCheck = new BooleanProperty("Ticks Existed Checks", false),
            entityIdCheck = new BooleanProperty("Entity ID Checks", false),
            alwaysClose = new BooleanProperty("Always Close Checks", false);

    public static DoubleProperty tabGrace = new DoubleProperty("Tab Grace (s)", new DoubleValue(0, 10, 2, 0.5));
    public static DoubleProperty stillTime = new DoubleProperty("Stationary Time (s)", new DoubleValue(5, 60, 15, 1));

    /** Per-entity movement history, rebuilt every tick from the players currently loaded. */
    private static final class Track {
        double x, z;
        int still, seen;

        Track(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }

    private static final Map<Integer, Track> TRACKS = new HashMap<>();

    /** Lower-cased tab names, rebuilt once per world tick instead of once per entity. */
    private static final Set<String> TAB_NAMES = new HashSet<>();
    private static long tabStamp = Long.MIN_VALUE;

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        Map<Integer, Track> next = new HashMap<>();
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            Track t = TRACKS.get(p.getId());
            if (t == null) {
                t = new Track(p.getX(), p.getZ());
            } else if (Math.abs(t.x - p.getX()) > 0.001 || Math.abs(t.z - p.getZ()) > 0.001) {
                t.x = p.getX();
                t.z = p.getZ();
                t.still = 0;
            } else {
                t.still++;
            }
            t.seen++;
            next.put(p.getId(), t);
        }
        TRACKS.clear();
        TRACKS.putAll(next);
    };

    public static boolean isBot(Entity entityPlayer) {
        return isBotCustom(entityPlayer);
    }

    public static boolean isBotCustom(Entity en) {
        if (en == mc.player || !(en instanceof Player)
                || !Arsenic.getArsenic().getModuleManager().getModuleByClass(AntiBot.class).isEnabled()) {
            return false;
        }

        Player player = (Player) en;
        Track track = TRACKS.get(player.getId());
        int seen = track == null ? 0 : track.seen;
        boolean onServer = onServer();
        boolean listed = !onServer || inTab(player);
        int graceTicks = (int) (tabGrace.getValue().getInput() * 20);

        if (zeroHealthChecks.getValue()) {
            if (player.getHealth() <= 0.0F || !en.isAlive()) {
                return true;
            }
        }

        if (tabChecks.getValue() && onServer && !listed && seen >= graceTicks) {
            return true;
        }

        if (stationaryChecks.getValue() && onServer && !listed && track != null
                && track.still >= (int) (stillTime.getValue().getInput() * 20)) {
            return true;
        }

        if (twiceChecks.getValue()) {
            if (hasDuplicateUUID(player)) {
                return true;
            }
        }

        if (invisCheck.getValue()) {
            if (en.isInvisibleTo(mc.player)) {
                return true;
            }
        }

        if (nameChecks.getValue()) {
            if (isStrongBotName(player)) return true;
            if (isWeakBotName(player) && onServer && !listed && seen >= graceTicks) return true;
        }

        if (noPushChecks.getValue()) {
            if (!en.isPushable()) {
                return true;
            }
        }

        if (pingCheck.getValue()) {
            if (mc.getConnection() != null && en.getName() != null) {
                PlayerInfo playerInfo = mc.getConnection().getPlayerInfo(en.getName().getString());
                if (playerInfo != null && playerInfo.getLatency() <= 0) {
                    return true;
                }
            }
        }

        if (ticksExistedCheck.getValue()) {
            if (en.tickCount < 20) {
                return true;
            }
        }

        if (entityIdCheck.getValue()) {
            if (en.getId() < 0 || en.getId() >= 1000000000) {
                return true;
            }
        }

        if (alwaysClose.getValue()) {
            if (en.tickCount < 5 || en.isInvisible()
                    || mc.player.distanceToSqr(en.getX(), mc.player.getY(), en.getZ()) > 100 * 100) {
                return true;
            }
        }

        return false;
    }

    private static boolean onServer() {
        return mc.level != null && !mc.isLocalServer() && mc.getConnection() != null;
    }

    public static ArrayList<Player> getPlayerList() {
        ArrayList<Player> list = new ArrayList<>();
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            return list;
        }

        Collection<PlayerInfo> playerInfoMap = mc.getConnection().getOnlinePlayers();
        if (playerInfoMap == null) {
            return list;
        }

        for (PlayerInfo networkPlayerInfo : playerInfoMap) {
            if (networkPlayerInfo == null || networkPlayerInfo.getProfile() == null) {
                continue;
            }
            Player player = mc.level.getPlayerByUUID(networkPlayerInfo.getProfile().id());
            if (player != null) {
                list.add(player);
            }
        }
        return list;
    }

    /** True if the entity's name is in the tab list. Always false in singleplayer, which has no tab list. */
    public static boolean inTab(LivingEntity en) {
        if (mc.isLocalServer() || en == null) {
            return false;
        }
        return tabNames().contains(en.getName().getString().toLowerCase(Locale.ROOT));
    }

    private static Set<String> tabNames() {
        long stamp = mc.level == null ? 0 : mc.level.getGameTime();
        if (stamp == tabStamp) return TAB_NAMES;
        tabStamp = stamp;
        TAB_NAMES.clear();
        ClientPacketListener netHandler = mc.getConnection();
        if (netHandler != null && netHandler.getOnlinePlayers() != null) {
            for (PlayerInfo info : netHandler.getOnlinePlayers()) {
                if (info != null && info.getProfile() != null && info.getProfile().name() != null) {
                    TAB_NAMES.add(info.getProfile().name().toLowerCase(Locale.ROOT));
                }
            }
        }
        return TAB_NAMES;
    }

    public static boolean hasDuplicateUUID(Player target) {
        ClientPacketListener netHandler = mc.getConnection();
        if (netHandler == null || target.getUUID() == null) {
            return false;
        }

        Collection<PlayerInfo> playerInfoList = netHandler.getOnlinePlayers();
        if (playerInfoList == null || playerInfoList.isEmpty()) {
            return false;
        }

        String targetUUID = target.getUUID().toString();
        int count = 0;
        for (PlayerInfo info : playerInfoList) {
            if (info == null || info.getProfile() == null || info.getProfile().id() == null) {
                continue;
            }
            if (info.getProfile().id().toString().equals(targetUUID)) {
                count++;
                if (count > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Names no real player can have. Always a bot. */
    static boolean isStrongBotName(Player player) {
        String name = player.getName().getString();
        if (name != null && name.contains(" ")) return true;
        String unformatted = arsenic.utils.minecraft.TextUtils.toLegacy(player.getDisplayName());
        return unformatted.length() >= 7 && unformatted.charAt(2) == '[' && unformatted.charAt(3) == 'N'
                && unformatted.charAt(6) == ']';
    }

    /** Name patterns real players can also match. Only trusted when the player is missing from the tab list. */
    static boolean isWeakBotName(Player player) {
        String unformatted = arsenic.utils.minecraft.TextUtils.toLegacy(player.getDisplayName());
        if (player.getHealth() == 20.0f) {
            return (unformatted.length() == 10 && unformatted.charAt(0) != '§')
                    || (unformatted.length() == 12 && player.isSleeping() && unformatted.charAt(0) == '§');
        }
        return player.isInvisible() && unformatted.length() >= 3 && unformatted.charAt(0) == '§' && unformatted.charAt(1) == 'c';
    }

    public static boolean isBotName(Entity en) {
        Player player = (Player) en;
        return isStrongBotName(player) || isWeakBotName(player);
    }
}

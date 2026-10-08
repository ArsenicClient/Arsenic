package arsenic.module.impl.client;

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
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

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
@ModuleInfo(name = "AntiBot", category = ModuleCategory.CLIENT, hidden = true)
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
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer) continue;
            Track t = TRACKS.get(p.getEntityId());
            if (t == null) {
                t = new Track(p.posX, p.posZ);
            } else if (Math.abs(t.x - p.posX) > 0.001 || Math.abs(t.z - p.posZ) > 0.001) {
                t.x = p.posX;
                t.z = p.posZ;
                t.still = 0;
            } else {
                t.still++;
            }
            t.seen++;
            next.put(p.getEntityId(), t);
        }
        TRACKS.clear();
        TRACKS.putAll(next);
    };

    public static boolean isBot(Entity entityPlayer) {
        return isBotCustom(entityPlayer);
    }

    public static boolean isBotCustom(Entity en) {
        if (en == mc.thePlayer || !(en instanceof EntityPlayer)
                || !Arsenic.getArsenic().getModuleManager().getModuleByClass(AntiBot.class).isEnabled()) {
            return false;
        }

        EntityPlayer player = (EntityPlayer) en;
        Track track = TRACKS.get(player.getEntityId());
        int seen = track == null ? 0 : track.seen;
        boolean onServer = onServer();
        boolean listed = !onServer || inTab(player);
        int graceTicks = (int) (tabGrace.getValue().getInput() * 20);

        if (zeroHealthChecks.getValue()) {
            if (player.getHealth() <= 0.0F || en.isDead) {
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
            if (en.isInvisibleToPlayer(mc.thePlayer)) {
                return true;
            }
        }

        if (nameChecks.getValue()) {
            if (isStrongBotName(player)) return true;
            if (isWeakBotName(player) && onServer && !listed && seen >= graceTicks) return true;
        }

        if (noPushChecks.getValue()) {
            if (!en.canBePushed()) {
                return true;
            }
        }

        if (pingCheck.getValue()) {
            if (mc.getNetHandler() != null && en.getName() != null) {
                NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(en.getName());
                if (playerInfo != null && playerInfo.getResponseTime() <= 0) {
                    return true;
                }
            }
        }

        if (ticksExistedCheck.getValue()) {
            if (en.ticksExisted < 20) {
                return true;
            }
        }

        if (entityIdCheck.getValue()) {
            if (en.getEntityId() < 0 || en.getEntityId() >= 1000000000) {
                return true;
            }
        }

        if (alwaysClose.getValue()) {
            if (en.ticksExisted < 5 || en.isInvisible()
                    || mc.thePlayer.getDistanceSq(en.posX, mc.thePlayer.posY, en.posZ) > 100 * 100) {
                return true;
            }
        }

        return false;
    }

    private static boolean onServer() {
        return mc.theWorld != null && !mc.isSingleplayer() && mc.getNetHandler() != null;
    }

    public static ArrayList<EntityPlayer> getPlayerList() {
        ArrayList<EntityPlayer> list = new ArrayList<>();
        if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.sendQueue == null) {
            return list;
        }

        Collection<NetworkPlayerInfo> playerInfoMap = mc.thePlayer.sendQueue.getPlayerInfoMap();
        if (playerInfoMap == null) {
            return list;
        }

        for (NetworkPlayerInfo networkPlayerInfo : playerInfoMap) {
            if (networkPlayerInfo == null || networkPlayerInfo.getGameProfile() == null) {
                continue;
            }
            EntityPlayer player = mc.theWorld.getPlayerEntityByName(networkPlayerInfo.getGameProfile().getName());
            if (player != null) {
                list.add(player);
            }
        }
        return list;
    }

    /** True if the entity's name is in the tab list. Always false in singleplayer, which has no tab list. */
    public static boolean inTab(EntityLivingBase en) {
        if (mc.isSingleplayer() || en == null || en.getName() == null) {
            return false;
        }
        return tabNames().contains(en.getName().toLowerCase(Locale.ROOT));
    }

    private static Set<String> tabNames() {
        long stamp = mc.theWorld == null ? 0 : mc.theWorld.getTotalWorldTime();
        if (stamp == tabStamp) return TAB_NAMES;
        tabStamp = stamp;
        TAB_NAMES.clear();
        NetHandlerPlayClient netHandler = mc.getNetHandler();
        if (netHandler != null && netHandler.getPlayerInfoMap() != null) {
            for (NetworkPlayerInfo info : netHandler.getPlayerInfoMap()) {
                if (info != null && info.getGameProfile() != null && info.getGameProfile().getName() != null) {
                    TAB_NAMES.add(info.getGameProfile().getName().toLowerCase(Locale.ROOT));
                }
            }
        }
        return TAB_NAMES;
    }

    public static boolean hasDuplicateUUID(EntityPlayer target) {
        NetHandlerPlayClient netHandler = mc.getNetHandler();
        if (netHandler == null || target.getUniqueID() == null) {
            return false;
        }

        Collection<NetworkPlayerInfo> playerInfoList = netHandler.getPlayerInfoMap();
        if (playerInfoList == null || playerInfoList.isEmpty()) {
            return false;
        }

        String targetUUID = target.getUniqueID().toString();
        int count = 0;
        for (NetworkPlayerInfo info : playerInfoList) {
            if (info == null || info.getGameProfile() == null || info.getGameProfile().getId() == null) {
                continue;
            }
            if (info.getGameProfile().getId().toString().equals(targetUUID)) {
                count++;
                if (count > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Names no real player can have. Always a bot. */
    static boolean isStrongBotName(EntityPlayer player) {
        String name = player.getName();
        if (name != null && name.contains(" ")) return true;
        String unformatted = player.getDisplayName().getUnformattedText();
        return unformatted.length() >= 7 && unformatted.charAt(2) == '[' && unformatted.charAt(3) == 'N'
                && unformatted.charAt(6) == ']';
    }

    /** Name patterns real players can also match. Only trusted when the player is missing from the tab list. */
    static boolean isWeakBotName(EntityPlayer player) {
        String unformatted = player.getDisplayName().getUnformattedText();
        if (player.getHealth() == 20.0f) {
            return (unformatted.length() == 10 && unformatted.charAt(0) != '§')
                    || (unformatted.length() == 12 && player.isPlayerSleeping() && unformatted.charAt(0) == '§');
        }
        return player.isInvisible() && unformatted.length() >= 3 && unformatted.charAt(0) == '§' && unformatted.charAt(1) == 'c';
    }

    public static boolean isBotName(Entity en) {
        EntityPlayer player = (EntityPlayer) en;
        return isStrongBotName(player) || isWeakBotName(player);
    }
}

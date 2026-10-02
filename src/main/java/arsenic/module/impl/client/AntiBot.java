package arsenic.module.impl.client;

import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@ModuleInfo(name = "AntiBot", category = ModuleCategory.CLIENT, hidden = true)
public class AntiBot extends Module {
    public static BooleanProperty nameChecks = new BooleanProperty("Name Checks", true),
            invisCheck = new BooleanProperty("Invis Checks", false),
            tabChecks = new BooleanProperty("Tab Checks", true),
            noPushChecks = new BooleanProperty("NoPush Checks", false),
            pingCheck = new BooleanProperty("Ping Checks", false),
            twiceChecks = new BooleanProperty("Twice UUID Checks", false),
            zeroHealthChecks = new BooleanProperty("Dead Checks", false),
            ticksExistedCheck = new BooleanProperty("Ticks Existed Checks", false),
            entityIdCheck = new BooleanProperty("Entity ID Checks", false),
            alwaysClose = new BooleanProperty("Always Close Checks", false);

    public static boolean isBot(Entity entityPlayer) {
        return isBotCustom(entityPlayer);
    }

    public static boolean isBotCustom(Entity en) {
        if (en == mc.player || !(en instanceof Player)
                || !Arsenic.getArsenic().getModuleManager().getModuleByClass(AntiBot.class).isEnabled()) {
            return false;
        }

        Player player = (Player) en;

        if (zeroHealthChecks.getValue()) {
            if (player.getHealth() <= 0.0F || en.isRemoved()) {
                return true;
            }
        }

        if (tabChecks.getValue()) {
            if (!inTab(player)) {
                return true;
            }
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
            if (isBotName(en)) {
                return true;
            }
        }

        if (noPushChecks.getValue()) {
            if (!en.isPushable()) {
                return true;
            }
        }

        if (pingCheck.getValue()) {
            if (mc.getConnection() != null && en.getName().getString() != null) {
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


    // UTILS

    /** Tab entries that are also loaded as entities. Anyone in tab but out of range is skipped. */
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
            Player player = arsenic.utils.minecraft.WorldUtils.getPlayerByName(networkPlayerInfo.getProfile().name());
            if (player != null) {
                list.add(player);
            }
        }
        return list;
    }

    public static boolean inTab(LivingEntity en) {
        if (mc.hasSingleplayerServer() || en == null) {
            return false;
        }

        ClientPacketListener netHandler = mc.getConnection();
        if (netHandler == null || netHandler.getOnlinePlayers() == null) {
            return false;
        }

        for (PlayerInfo info : netHandler.getOnlinePlayers()) {
            if (info != null && info.getProfile() != null && info.getProfile().name() != null
                    && info.getProfile().name().equals(en.getName().getString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when the given player's UUID appears more than once in the tab list,
     * indicating a duplicate entity (likely a bot).
     */
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

    public static boolean isBotName(Entity en) {
        final Player entityPlayer = (Player) en;
        String unformattedText = entityPlayer.getDisplayName().getString();
        if (entityPlayer.getHealth() == 20.0f) {
            if ((unformattedText.length() == 10 && unformattedText.charAt(0) != '§')
                    || (unformattedText.length() == 12 && entityPlayer.isSleeping() && unformattedText.charAt(0) == '§')
                    || (unformattedText.length() >= 7 && unformattedText.charAt(2) == '[' && unformattedText.charAt(3) == 'N' && unformattedText.charAt(6) == ']')
                    || (entityPlayer.getName().getString().contains(" "))) {
                return true;
            }
        } else if (entityPlayer.isInvisible()) {
            if (unformattedText.length() >= 3 && unformattedText.charAt(0) == '§' && unformattedText.charAt(1) == 'c') {
                return true;
            }
        }
        return false;
    }
}

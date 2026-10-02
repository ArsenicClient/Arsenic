package arsenic.module.impl.client;

import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
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
            if (!en.canBePushed()) {
                return true;
            }
        }

        if (pingCheck.getValue()) {
            if (mc.getConnection() != null && en.getName() != null) {
                NetworkPlayerInfo playerInfo = mc.getConnection().getPlayerInfo(en.getName());
                if (playerInfo != null && playerInfo.getResponseTime() <= 0) {
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
                    || mc.player.getDistanceSq(en.getX(), mc.player.getY(), en.getZ()) > 100 * 100) {
                return true;
            }
        }

        return false;
    }


    // UTILS

    /** Tab entries that are also loaded as entities. Anyone in tab but out of range is skipped. */
    public static ArrayList<Player> getPlayerList() {
        ArrayList<Player> list = new ArrayList<>();
        if (mc.player == null || mc.level == null || mc.player.sendQueue == null) {
            return list;
        }

        Collection<NetworkPlayerInfo> playerInfoMap = mc.player.sendQueue.getPlayerInfoMap();
        if (playerInfoMap == null) {
            return list;
        }

        for (NetworkPlayerInfo networkPlayerInfo : playerInfoMap) {
            if (networkPlayerInfo == null || networkPlayerInfo.getGameProfile() == null) {
                continue;
            }
            Player player = mc.level.getPlayerEntityByName(networkPlayerInfo.getGameProfile().getName());
            if (player != null) {
                list.add(player);
            }
        }
        return list;
    }

    public static boolean inTab(LivingEntity en) {
        if (mc.isSingleplayer() || en == null) {
            return false;
        }

        NetHandlerPlayClient netHandler = mc.getConnection();
        if (netHandler == null || netHandler.getPlayerInfoMap() == null) {
            return false;
        }

        for (NetworkPlayerInfo info : netHandler.getPlayerInfoMap()) {
            if (info != null && info.getGameProfile() != null && info.getGameProfile().getName() != null
                    && info.getGameProfile().getName().equals(en.getName())) {
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
        NetHandlerPlayClient netHandler = mc.getConnection();
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

    public static boolean isBotName(Entity en) {
        final Player entityPlayer = (Player) en;
        String unformattedText = entityPlayer.getDisplayName().getUnformattedText();
        if (entityPlayer.getHealth() == 20.0f) {
            if ((unformattedText.length() == 10 && unformattedText.charAt(0) != '§')
                    || (unformattedText.length() == 12 && entityPlayer.isPlayerSleeping() && unformattedText.charAt(0) == '§')
                    || (unformattedText.length() >= 7 && unformattedText.charAt(2) == '[' && unformattedText.charAt(3) == 'N' && unformattedText.charAt(6) == ']')
                    || (entityPlayer.getName().contains(" "))) {
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

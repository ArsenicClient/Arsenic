package arsenic.module.impl.client;

import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

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
        if (en == mc.thePlayer || !(en instanceof EntityPlayer)
                || !Arsenic.getArsenic().getModuleManager().getModuleByClass(AntiBot.class).isEnabled()) {
            return false;
        }

        EntityPlayer player = (EntityPlayer) en;

        if (zeroHealthChecks.getValue()) {
            if (player.getHealth() <= 0.0F || en.isDead) {
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
            if (en.isInvisibleToPlayer(mc.thePlayer)) {
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

    public static boolean inTab(EntityLivingBase en) {
        if (mc.isSingleplayer() || en == null) {
            return false;
        }

        NetHandlerPlayClient netHandler = mc.getNetHandler();
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

    public static boolean isBotName(Entity en) {
        final EntityPlayer entityPlayer = (EntityPlayer) en;
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

package arsenic.module.impl.client;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.Collection;

@ModuleInfo(name = "AntiBot", category = ModuleCategory.WORLD)
public class AntiBot extends Module {
    public static BooleanProperty nameChecks = new BooleanProperty("Name Checks", true),
            invisCheck = new BooleanProperty("Invis Checks", false),
            tabChecks = new BooleanProperty("Tab Checks", true),
            noPushChecks = new BooleanProperty("NoPush Checks", false),
            pingCheck = new BooleanProperty("Ping Checks", false),
            twiceChecks = new BooleanProperty("Twice UUID Checks", false),
            zeroHealthChecks = new BooleanProperty("Dead Checks", false),
            alwaysClose = new BooleanProperty("AlwaysClose Checks", false);

    public static boolean isBot(Entity entityPlayer) {
        // exception
        if (entityPlayer != mc.thePlayer) {
            return isBotCustom(entityPlayer);
        }

        return false;
    }

    public static boolean isBotCustom(Entity en) {
        if (en == mc.thePlayer) {
            return false;
        }

        if (twiceChecks.getValue()) {
            if (!isPlayerTwiceInGame()) {
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
            if (mc.getNetHandler() != null && en != null && en.getName() != null) {
                NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(en.getName());
                if (playerInfo != null && playerInfo.getResponseTime() < 3) {
                    return true;
                }
            }
        }


        if (zeroHealthChecks.getValue()) {
            if (((EntityLivingBase) en).getHealth() < 0.0F || en.isDead) {
                return true;
            }
        }

        if (tabChecks.getValue()) {
            if (!inTab((EntityLivingBase) en)) {
                return true;
            }
        }

        if (alwaysClose.getValue()) {
            if (en.ticksExisted < 5 || en.isInvisible() || mc.thePlayer.getDistanceSq(en.posX, mc.thePlayer.posY, en.posZ) > 100 * 100) {
                return true;
            }
        }
        return false;
    }


    // UTILS
    public static ArrayList<EntityPlayer> getPlayerList() {
        Collection<NetworkPlayerInfo> playerInfoMap = Minecraft.getMinecraft().thePlayer.sendQueue.getPlayerInfoMap();
        ArrayList<EntityPlayer> list = new ArrayList<>();
        for (NetworkPlayerInfo networkPlayerInfo : playerInfoMap) {
            list.add(Minecraft.getMinecraft().theWorld.getPlayerEntityByName(networkPlayerInfo.getGameProfile().getName()));
        }
        return list;
    }

    public static boolean inTab(EntityLivingBase en) {
        if (!Minecraft.getMinecraft().isSingleplayer()) {
            for (NetworkPlayerInfo info : Minecraft.getMinecraft().getNetHandler().getPlayerInfoMap()) {
                if (info != null && info.getGameProfile() != null && info.getGameProfile().getName().contains(en.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isPlayerTwiceInGame() {
        Collection<NetworkPlayerInfo> playerInfoList = Minecraft.getMinecraft().getNetHandler().getPlayerInfoMap();
        if (playerInfoList != null && !playerInfoList.isEmpty()) {
            if (getPlayerList().get(0) != null) {
                String targetPlayerID = getPlayerList().get(0).getGameProfile().getId().toString();
                if (targetPlayerID != null) {
                    for (NetworkPlayerInfo info : playerInfoList) {
                        String infoID = info.getGameProfile().getId().toString();
                        if (info != null && infoID != null) {
                            if (targetPlayerID.equals(infoID)) {
                                for (NetworkPlayerInfo info2 : playerInfoList) {
                                    String infoID2 = info2.getGameProfile().getId().toString();
                                    if (info2 != null && infoID2 != null) {
                                        if (targetPlayerID.equals(infoID2) && !info.equals(info2)) {
                                            return true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public static boolean isBotName(Entity en) {
        String rawName = en.getDisplayName().getUnformattedText().toLowerCase();
        String forName = en.getDisplayName().getFormattedText().toLowerCase();

        if (forName.startsWith("§r§8[npc]")) {
            return true;
        }

        for (EntityPlayer list : mc.theWorld.playerEntities) {
            if (list != mc.thePlayer && !list.isDead && list.isInvisible() && getPlayerList().contains(list) && list.getCustomNameTag().length() >= 2) {
                mc.theWorld.removeEntity(list);
                return true;
            }
        }

        if (forName.contains("]")) {
            return true;
        }
        if (forName.contains("[")) {
            return true;
        }
        if (rawName.contains("-")) {
            return true;
        }
        if (rawName.contains(":")) {
            return true;
        }
        if (rawName.contains("+")) {
            return true;
        }
        if (rawName.startsWith("cit")) {
            return true;
        }
        if (rawName.startsWith("npc")) {
            return true;
        }
        return rawName.isEmpty() || rawName.contains(" ") || forName.isEmpty();
    }
}
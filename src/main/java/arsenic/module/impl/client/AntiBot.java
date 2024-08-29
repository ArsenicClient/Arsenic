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

//TODO: recode le unique code

@ModuleInfo(name = "AntiBot", category = ModuleCategory.SETTINGS)
public class AntiBot extends Module {
    public static BooleanProperty nameChecks = new BooleanProperty("Name Checks", true),
            invisCheck = new BooleanProperty("Invis Checks", false),
            tabChecks = new BooleanProperty("Tab Checks", true),
            noPushChecks = new BooleanProperty("NoPush Checks", false),
            pingCheck = new BooleanProperty("Ping Checks", false),
            twiceChecks = new BooleanProperty("Twice UUID Checks", false),
            zeroHealthChecks = new BooleanProperty("Dead Checks", false),
            alwaysClose = new BooleanProperty("Always Close Checks", false);

    public static boolean isBot(Entity entityPlayer) {
        return isBotCustom(entityPlayer);
    }

    public static boolean isBotCustom(Entity en) {
        if (en == mc.thePlayer || !checkHurtTime((EntityPlayer) en) || !this.isEnabled()) {
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
        Collection<NetworkPlayerInfo> playerInfoMap = mc.thePlayer.sendQueue.getPlayerInfoMap();
        ArrayList<EntityPlayer> list = new ArrayList<>();
        for (NetworkPlayerInfo networkPlayerInfo : playerInfoMap) {
            list.add(mc.theWorld.getPlayerEntityByName(networkPlayerInfo.getGameProfile().getName()));
        }
        return list;
    }

    public static boolean inTab(EntityLivingBase en) {
        if (!mc.isSingleplayer()) {
            for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
                if (info != null && info.getGameProfile() != null && info.getGameProfile().getName().contains(en.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isPlayerTwiceInGame() {
        Collection<NetworkPlayerInfo> playerInfoList = mc.getNetHandler().getPlayerInfoMap();
        if (playerInfoList == null || playerInfoList.isEmpty()) {
            return false;
        }

        String targetPlayerID = getPlayerList().get(0).getGameProfile().getId().toString();
        if (targetPlayerID == null) {
            return false;
        }

        int count = 0;
        for (NetworkPlayerInfo info : playerInfoList) {
            if (info != null && targetPlayerID.equals(info.getGameProfile().getId().toString())) {
                count++;
                if (count > 1) {
                    return true;
                }
            }
        }
        return false;
    }
    public static boolean checkHurtTime(EntityPlayer entityPlayer) {
        return entityPlayer.maxHurtTime == 0;
    }

    public static boolean isBotName(Entity en) {
        final EntityPlayer entityPlayer = (EntityPlayer) en;
            String unformattedText = entityPlayer.getDisplayName().getUnformattedText();
            if (entityPlayer.getHealth() == 20.0f) {
                if ((unformattedText.length() == 10 && unformattedText.charAt(0) != '§') || (unformattedText.length() == 12 && entityPlayer.isPlayerSleeping() && unformattedText.charAt(0) == '§') || (unformattedText.length() >= 7 && unformattedText.charAt(2) == '[' && unformattedText.charAt(3) == 'N' && unformattedText.charAt(6) == ']') || (entityPlayer.getName().contains(" "))) {
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

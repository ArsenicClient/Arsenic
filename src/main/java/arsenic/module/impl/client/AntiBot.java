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

//TODO: recode le unique code

@ModuleInfo(name = "AntiBot", category = ModuleCategory.SETTINGS, hidden = true)
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
        if (en == mc.thePlayer || !checkHurtTime((EntityPlayer) en) || !Arsenic.getArsenic().getModuleManager().getModuleByClass(AntiBot.class).isEnabled()) {
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
    /** Tab entries that are also loaded as entities. Anyone in tab but out of range is skipped. */
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
            // null whenever that player is in tab but not loaded in the world
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
                    && info.getGameProfile().getName().contains(en.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when the same UUID appears more than once in the tab list.
     *
     * <p>This used to seed itself from {@code getPlayerList().get(0)}, which threw a
     * NullPointerException on nearly every call: that list is built by mapping tab entries through
     * {@code World#getPlayerEntityByName}, which returns null for anyone who is in tab but not
     * loaded as an entity - so entry 0 was usually null. Counting UUIDs directly needs no entities
     * at all, and matches what the name says.
     */
    public static boolean isPlayerTwiceInGame() {
        NetHandlerPlayClient netHandler = mc.getNetHandler();
        if (netHandler == null) {
            return false;
        }

        Collection<NetworkPlayerInfo> playerInfoList = netHandler.getPlayerInfoMap();
        if (playerInfoList == null || playerInfoList.isEmpty()) {
            return false;
        }

        Set<String> seen = new HashSet<>();
        for (NetworkPlayerInfo info : playerInfoList) {
            if (info == null || info.getGameProfile() == null || info.getGameProfile().getId() == null) {
                continue;
            }
            if (!seen.add(info.getGameProfile().getId().toString())) {
                return true;
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

package arsenic.utils.minecraft;

import arsenic.module.ModuleManager;
import arsenic.module.impl.client.AntiBot;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;

import java.util.ArrayList;
import java.util.List;

public class PlayerUtils {

    private PlayerUtils() { throw new IllegalStateException("Utility class should not be initialised");}

    private static Minecraft mc = Minecraft.getMinecraft();

    public static void addMessageToChat(String msg) {
        mc.thePlayer.addChatMessage(new ChatComponentText(msg));
    }

    public static void addWaterMarkedMessageToChat(String msg) {
        addMessageToChat("§7[§cA§7]§r " + msg);
    }
    public static boolean playerOverAir() {
        return mc.theWorld.isAirBlock(getBlockUnderPlayer());
    }

    public static BlockPos getBlockUnderPlayer() {
        double x = mc.thePlayer.posX;
        double y = mc.thePlayer.posY - 1.0D;
        double z = mc.thePlayer.posZ;
        return new BlockPos(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z));
    }

    public static Entity getClosestPlayerWithin(double distance) {
        Entity target = null;
        for(Entity entity : mc.theWorld.loadedEntityList) {
            float tempDistance = mc.thePlayer.getDistanceToEntity(entity);
            if(entity != mc.thePlayer && tempDistance <= distance) {
                target = entity;
                distance = tempDistance;
            }
        }
        return target;
    }

    public static List<Entity> getPlayersWithin(double distance) {
        List<Entity> targets = new ArrayList<>();
        for(EntityPlayer entity : mc.theWorld.playerEntities) {
            if(!((AntiBot) ModuleManager.Modules.ANTIBOT.getModule()).isARealPlayer(entity))
                continue;
            float tempDistance = mc.thePlayer.getDistanceToEntity(entity);
            if(entity != mc.thePlayer && tempDistance <= distance) {
                targets.add(entity);
            }
        }
        return targets;
    }

    public static List<Entity> getEntitysWithin(double distance) {
        List<Entity> targets = new ArrayList<>();
        for(Entity entity : mc.theWorld.loadedEntityList) {
            float tempDistance = mc.thePlayer.getDistanceToEntity(entity);
            if(entity != mc.thePlayer && tempDistance <= distance) {
                targets.add(entity);
            }
        }
        return targets;
    }

}

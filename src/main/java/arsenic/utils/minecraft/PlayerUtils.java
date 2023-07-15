package arsenic.utils.minecraft;

import arsenic.main.Arsenic;
import arsenic.module.ModuleManager;
import arsenic.module.impl.client.AntiBot;
import arsenic.utils.java.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemSword;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;

import java.util.ArrayList;
import java.util.List;

public class PlayerUtils extends UtilityClass {
    
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void addMessageToChat(String msg) {
        if(mc.thePlayer != null)
            mc.thePlayer.addChatMessage(new ChatComponentText(msg));
    }
    public static boolean isPlayerHoldingWeapon() {
        if (mc.thePlayer.getCurrentEquippedItem() == null) {
            return false;
        } else {
            Item item = mc.thePlayer.getCurrentEquippedItem().getItem();
            return item instanceof ItemSword || item instanceof ItemAxe;
        }
    }
    public static boolean isPlayerHoldingBlocks() {
        if (mc.thePlayer.getCurrentEquippedItem() == null) {
            return false;
        } else {
            Item item = mc.thePlayer.getCurrentEquippedItem().getItem();
            return item instanceof ItemBlock;
        }
    }
    public static void addWaterMarkedMessageToChat(Object object) {
        addMessageToChat("§7[§cA§7]§r " + object.toString());
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

    public static EntityPlayer getClosestPlayerWithin(double distance) {
        EntityPlayer target = null;
        for(EntityPlayer entity : mc.theWorld.playerEntities) {
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
            if(!(Arsenic.getArsenic().getModuleManager().getModuleByClass(AntiBot.class).isARealPlayer(entity)))
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

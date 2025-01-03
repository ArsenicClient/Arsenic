package arsenic.utils.minecraft;

import arsenic.utils.java.UtilityClass;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.*;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PlayerUtils extends UtilityClass {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void addMessageToChat(String msg) {
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(msg));
        }
    }

    public static boolean isPlayerHoldingWeapon() {
        if (mc.thePlayer.getCurrentEquippedItem() == null)
            return false;
        Item item = mc.thePlayer.getCurrentEquippedItem().getItem();
        return item instanceof ItemSword || item instanceof ItemAxe;
    }

    public static boolean isPlayerHoldingBlocks() {
        if (mc.thePlayer.getCurrentEquippedItem() == null)
            return false;
        Item item = mc.thePlayer.getCurrentEquippedItem().getItem();
        return item instanceof ItemBlock;
    }
    public static boolean isPlayerHoldingSword() {
        return (mc.thePlayer.getCurrentEquippedItem() != null)
                && (mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemSword);
    }
    public static void addWaterMarkedMessageToChat(Object object) {
        addMessageToChat("§7[§cA§7]§r " + object.toString());
    }

    public static boolean playerOverAir() {
        return mc.theWorld.isAirBlock(getBlockUnderPlayer());
    }
    public static boolean playerIsEdging(Entity entity) {
        return mc.theWorld.getCollidingBoundingBoxes(entity, entity.getEntityBoundingBox().offset(entity.motionX / 3.0D, -1.0D, entity.motionZ / 3.0D)).isEmpty();
    }

    public static BlockPos getBlockUnderPlayer() {
        double x = mc.thePlayer.posX;
        double y = mc.thePlayer.posY - 1.0D;
        double z = mc.thePlayer.posZ;
        return new BlockPos(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z));
    }

    public static void click() {
        mc.thePlayer.swingItem();
        switch (mc.objectMouseOver.typeOfHit) {
            case ENTITY:
                mc.playerController.attackEntity(mc.thePlayer, mc.objectMouseOver.entityHit);
                break;
            case BLOCK:
                BlockPos blockpos = mc.objectMouseOver.getBlockPos();

                if (mc.theWorld.getBlockState(blockpos).getBlock().getMaterial() != Material.air) {
                    mc.playerController.clickBlock(blockpos, mc.objectMouseOver.sideHit);
                    break;
                }
            case MISS:
            default:
        }
    }
    public static EntityPlayer getClosestPlayerWithin(double distance) {
        EntityPlayer target = null;
        for (EntityPlayer entity : mc.theWorld.playerEntities) {
            float tempDistance = mc.thePlayer.getDistanceToEntity(entity);
            if (entity != mc.thePlayer && tempDistance <= distance) {
                target = entity;
                distance = tempDistance;
            }
        }
        return target;
    }
    public static boolean isPlayerWearingArmour(EntityPlayer en) {
        for (int armorPiece = 0; armorPiece < 4; armorPiece++)
            if (en.getCurrentArmor(armorPiece) == null)
                return true;
        return false;
    }
    public static boolean withinFov(Entity entity, float fov) {
        float f = fov * 0.5f;
        float angle = RotationUtils.fovToEntity(entity);
        float yaw = mc.thePlayer.rotationYaw;
        float angleDiff = ((yaw - angle) % 360 + 540) % 360 - 180;
        return angleDiff > -f && angleDiff < f;
    }

    public static List<EntityPlayer> getPlayersWithin(double distance) {
        List<EntityPlayer> targets = new ArrayList<>();
        for (EntityPlayer entity : mc.theWorld.playerEntities) {
            float tempDistance = mc.thePlayer.getDistanceToEntity(entity);
            if (entity != mc.thePlayer && tempDistance <= distance) {
                targets.add(entity);
            }
        }
        return targets;
    }

    public static List<Entity> getEntitysWithin(double distance) {
        List<Entity> targets = new ArrayList<>();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            float tempDistance = mc.thePlayer.getDistanceToEntity(entity);
            if (entity != mc.thePlayer && tempDistance <= distance) {
                targets.add(entity);
            }
        }
        return targets;
    }

    public static boolean isPlayerNotLoaded() {
        return !(mc.thePlayer == null && mc.theWorld == null);
    }

    public static boolean isEntityTeamSameAsPlayer(EntityLivingBase target) {
        try {
            Entity teamMate = target;
            if (mc.thePlayer.isOnSameTeam(target) || mc.thePlayer.getDisplayName().getUnformattedText().startsWith(teamMate.getDisplayName().getUnformattedText().substring(0, 2))) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }
    public static int getTool(Block block) {
        float n = 1.0f;
        int n2 = -1;
        for (int i = 0; i < InventoryPlayer.getHotbarSize(); ++i) {
            final ItemStack getStackInSlot = mc.thePlayer.inventory.getStackInSlot(i);
            if (getStackInSlot != null) {
                final float a = getEfficiency(getStackInSlot, block);
                if (a > n) {
                    n = a;
                    n2 = i;
                }
            }
        }
        return n2;
    }
    public static float getEfficiency(final ItemStack itemStack, final Block block) {
        float getStrVsBlock = itemStack.getStrVsBlock(block);
        if (getStrVsBlock > 1.0f) {
            final int getEnchantmentLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, itemStack);
            if (getEnchantmentLevel > 0) {
                getStrVsBlock += getEnchantmentLevel * getEnchantmentLevel + 1;
            }
        }
        return getStrVsBlock;
    }
}

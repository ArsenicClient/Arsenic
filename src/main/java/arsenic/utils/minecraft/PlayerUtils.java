package arsenic.utils.minecraft;

import arsenic.main.Arsenic;
import arsenic.utils.java.UtilityClass;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class PlayerUtils extends UtilityClass {

    public static void addMessageToChat(String msg) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(msg));
        }
    }

    public static boolean isPlayerHoldingWeapon() {
        return ItemUtils.isWeapon(mc.player.getMainHandItem());
    }

    public static boolean isPlayerHoldingBlocks() {
        return ItemUtils.isBlock(mc.player.getMainHandItem());
    }

    public static boolean isPlayerHoldingSword() {
        return ItemUtils.isSword(mc.player.getMainHandItem());
    }

    public static void addWaterMarkedMessageToChat(Object object) {
        addMessageToChat("§7[§cA§7]§r " + object.toString());
    }

    public static void addWaterMarkedMessageToChat(Object... object) {
        StringBuilder builder = new StringBuilder();
        for (Object o : object) {
            builder.append(o.toString()).append(" | ");
        }
        addMessageToChat("§7[§cA§7]§r " + builder);
    }

    public static boolean playerOverAir() {
        return mc.level.isEmptyBlock(getBlockUnderPlayer());
    }

    public static boolean playerIsEdging(Entity entity) {
        Vec3 motion = entity.getDeltaMovement();
        return mc.level.noCollision(entity, entity.getBoundingBox().move(motion.x / 3.0D, -1.0D, motion.z / 3.0D));
    }

    public static BlockPos getBlockUnderPlayer() {
        return BlockPos.containing(mc.player.getX(), mc.player.getY() - 1.0D, mc.player.getZ());
    }

    /**
     * Swings the main hand and tells the server, like 1.8's swingItem. The animation packet is
     * called a punch now and is sent separately from the swing.
     */
    public static void swingItem() {
        mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), false);
        mc.player.connection.send(net.minecraft.network.protocol.game.ServerboundPunchPacket.INSTANCE);
    }

    /** A vanilla left click on whatever the crosshair is on. */
    public static void click() {
        swingItem();
        if (mc.hitResult instanceof EntityHitResult entityHit) {
            mc.gameMode.attack(mc.player, entityHit.getEntity());
        } else if (mc.hitResult instanceof BlockHitResult blockHit && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            if (!mc.level.getBlockState(pos).isAir())
                mc.gameMode.startDestroyBlock(pos, blockHit.getDirection());
        }
    }

    public static Player getClosestPlayerWithin(double distance) {
        Player target = null;
        for (Player entity : mc.level.players()) {
            float tempDistance = mc.player.distanceTo(entity);
            if (entity != mc.player && tempDistance <= distance) {
                target = entity;
                distance = tempDistance;
            }
        }
        return target;
    }

    /** Despite the name this has always returned true when a piece is MISSING; kept as-is. */
    public static boolean isPlayerWearingArmour(Player en) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            if (en.getItemBySlot(slot).isEmpty())
                return true;
        return false;
    }

    public static boolean withinFov(Entity entity, float fov) {
        float f = fov * 0.5f;
        float angle = RotationUtils.fovToEntity(entity);
        // Use silent aim yaw so FOV is centered on where we're actually aiming
        float yaw = Arsenic.getArsenic().getSilentRotationManager().yaw;
        float angleDiff = ((yaw - angle) % 360 + 540) % 360 - 180;
        return angleDiff > -f && angleDiff < f;
    }

    public static List<Player> getPlayersWithin(double distance) {
        List<Player> targets = new ArrayList<>();
        for (Player entity : mc.level.players()) {
            float tempDistance = mc.player.distanceTo(entity);
            if (entity != mc.player && tempDistance <= distance) {
                targets.add(entity);
            }
        }
        return targets;
    }

    public static List<Entity> getEntitysWithin(double distance) {
        List<Entity> targets = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            float tempDistance = mc.player.distanceTo(entity);
            if (entity != mc.player && tempDistance <= distance) {
                targets.add(entity);
            }
        }
        return targets;
    }

    /**
     * 1.8's World#rayTraceBlocks: the first block between two points, against outline shapes.
     * Returns a MISS result (never null) when nothing is in the way.
     */
    public static net.minecraft.world.phys.BlockHitResult rayTraceBlocks(Vec3 from, Vec3 to) {
        return mc.level.clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
    }

    /** True once there is a player and a level to act on. */
    public static boolean isPlayerLoaded() {
        return mc.player != null && mc.level != null;
    }

    public static boolean isEntityTeamSameAsPlayer(LivingEntity target) {
        try {
            if (mc.player.isAlliedTo(target)) {
                return true;
            }
            String ours = mc.player.getDisplayName().getString();
            String theirs = target.getDisplayName().getString();
            if (theirs.length() >= 2 && ours.startsWith(theirs.substring(0, 2))) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static int getTool(BlockState block) {
        float n = 1.0f;
        int n2 = -1;
        for (int i = 0; i < Inventory.getSelectionSize(); ++i) {
            final ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                final float a = getEfficiency(stack, block);
                if (a > n) {
                    n = a;
                    n2 = i;
                }
            }
        }
        return n2;
    }

    public static float getEfficiency(final ItemStack itemStack, final BlockState block) {
        float speed = itemStack.getDestroySpeed(block);
        if (speed > 1.0f) {
            final int level = ItemUtils.enchantLevel(Enchantments.EFFICIENCY, itemStack);
            if (level > 0) {
                speed += level * level + 1;
            }
        }
        return speed;
    }
}

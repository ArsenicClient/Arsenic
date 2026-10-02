package arsenic.utils.minecraft;

import arsenic.event.impl.EventMovementInput;
import arsenic.main.Arsenic;
import arsenic.module.impl.world.Scaffold;
import arsenic.utils.java.UtilityClass;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ScaffoldUtil extends UtilityClass {

    public static Block block(final double x, final double y, final double z) {
        return mc.level.getBlockState(BlockPos.containing(x, y, z)).getBlock();
    }

    public static boolean willFallNextTick() {
        return willFallNextTick(1.0);
    }

    /**
     * Where the player's box will be next tick if they keep pressing what they are pressing now,
     * moving along the silent yaw - the 1.8 movement maths, which still holds for flat ground.
     */
    public static AABB getPredictedBoundingBox(double precision) {
        LocalPlayer player = mc.player;
        SilentRotationManager silentRotationManager = Arsenic.getArsenic().getSilentRotationManager();

        Vec3 motion = player.getDeltaMovement();
        double motionX = motion.x;
        double motionZ = motion.z;

        float moveForward = 0;
        float moveStrafe = 0;
        Options options = mc.options;

        if (options.keyUp.isDown()) {
            ++moveForward;
        }
        if (options.keyDown.isDown()) {
            --moveForward;
        }
        if (options.keyLeft.isDown()) {
            ++moveStrafe;
        }
        if (options.keyRight.isDown()) {
            --moveStrafe;
        }

        EventMovementInput event = new EventMovementInput(moveForward, moveStrafe, options.keyJump.isDown());
        Arsenic.getArsenic().getEventManager().post(event);
        if (event.isCancelled()) {
            moveStrafe = 0.0F;
            moveForward = 0.0F;
        } else {
            moveForward = event.getSpeed();
            moveStrafe = event.getStrafe();
        }

        motionX *= 0.98;
        motionZ *= 0.98;
        if (Math.abs(motionX) < 0.005) {
            motionX = 0.0F;
        }
        if (Math.abs(motionZ) < 0.005) {
            motionZ = 0.0F;
        }

        moveStrafe *= 0.98F;
        moveForward *= 0.98F;

        float f4 = 0.91F;
        if (player.onGround()) {
            BlockPos below = BlockPos.containing(player.getX(), player.getBoundingBox().minY - 1, player.getZ());
            f4 = player.level().getBlockState(below).getBlock().getFriction() * 0.91F;
        }

        float f = 0.16277136F / (f4 * f4 * f4);
        float f5 = player.getSpeed() * f;

        f = moveStrafe * moveStrafe + moveForward * moveForward;
        if (f >= 1.0E-4F) {
            f = Mth.sqrt(f);
            if (f < 1.0F) {
                f = 1.0F;
            }
            f = f5 / f;
            moveStrafe *= f;
            moveForward *= f;
            float f1 = Mth.sin(silentRotationManager.yaw * (float) Math.PI / 180.0F);
            float f2 = Mth.cos(silentRotationManager.yaw * (float) Math.PI / 180.0F);
            motionX += (moveStrafe * f2 - moveForward * f1);
            motionZ += (moveForward * f2 + moveStrafe * f1);
        }

        return player.getBoundingBox().move(motionX * precision, 0, motionZ * precision);
    }

    public static boolean willFallNextTick(double precision) {
        AABB predictedBB = getPredictedBoundingBox(precision).move(0, -0.5, 0);
        return mc.level.noCollision(mc.player, predictedBB);
    }

    public static Vec3 getNewVector(Scaffold.BlockData lastblockdata) {
        if (lastblockdata == null) {
            return null;
        }
        return getNewVector(lastblockdata.getPosition(), lastblockdata.getFacing());
    }

    public static Vec3 getNewVector(BlockPos pos, Direction facing) {
        Vec3 vec3 = Vec3.atLowerCornerOf(pos);
        double amount1 = 0.45 + Math.random() * 0.1;
        double amount2 = 0.45 + Math.random() * 0.1;
        return switch (facing) {
            case UP -> vec3.add(amount1, 1, amount2);
            case DOWN -> vec3.add(amount1, 0, amount2);
            case EAST -> vec3.add(1, amount1, amount2);
            case WEST -> vec3.add(0, amount1, amount2);
            case NORTH -> vec3.add(amount1, amount2, 0);
            case SOUTH -> vec3.add(amount1, amount2, 1);
        };
    }

    public static int getBlockSlot() {
        for (int i = 0; i < 9; i++) {
            final ItemStack itemStack = mc.player.getInventory().getItem(i);
            if (!itemStack.isEmpty() && itemStack.getItem() instanceof BlockItem itemBlock && itemStack.getCount() > 1) {
                if (isBlockValid(itemBlock.getBlock())) {
                    return i;
                }
            }
        }
        return mc.player.getInventory().getSelectedSlot();
    }

    private static boolean isBlockValid(final Block block) {
        boolean fullBlock = block.defaultBlockState().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return (fullBlock || block == Blocks.GLASS) &&
                block != Blocks.SAND &&
                block != Blocks.GRAVEL &&
                block != Blocks.DISPENSER &&
                block != Blocks.COMMAND_BLOCK &&
                block != Blocks.NOTE_BLOCK &&
                block != Blocks.FURNACE &&
                block != Blocks.CRAFTING_TABLE &&
                block != Blocks.TNT &&
                block != Blocks.DROPPER &&
                block != Blocks.BEACON;
    }
}

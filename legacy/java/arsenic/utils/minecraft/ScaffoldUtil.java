package arsenic.utils.minecraft;

import arsenic.event.impl.EventMovementInput;
import arsenic.main.Arsenic;
import arsenic.module.impl.world.Scaffold;
import arsenic.utils.botcore.Box;
import arsenic.utils.java.JavaUtils;
import arsenic.utils.java.UtilityClass;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.scaffoldcore.ScaffoldCore;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.material.Material;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;

public class ScaffoldUtil extends UtilityClass {

    public static Block block(final double x, final double y, final double z) {
        return mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock();
    }

    public static boolean willFallNextTick() {
        return willFallNextTick(1.0);
    }

    public static final McScaffoldWorld WORLD = new McScaffoldWorld();

    public static ScaffoldCore.Input coreInput(boolean movement) {
        EntityPlayerSP player = mc.thePlayer;
        SilentRotationManager silentRotationManager = Arsenic.getArsenic().getSilentRotationManager();
        ScaffoldCore.Input in = new ScaffoldCore.Input();
        in.posX = player.posX;
        in.posY = player.posY;
        in.posZ = player.posZ;
        in.motionX = player.motionX;
        in.motionY = player.motionY;
        in.motionZ = player.motionZ;
        in.onGround = player.onGround;
        in.eyeHeight = player.getEyeHeight();
        in.cameraYaw = player.rotationYaw;
        in.silentYaw = silentRotationManager.yaw;
        in.silentPitch = silentRotationManager.pitch;
        in.gcd = RotationUtils.getGCD();

        GameSettings gameSettings = mc.gameSettings;
        in.keyForward = gameSettings.keyBindForward.isKeyDown();
        in.keyBack = gameSettings.keyBindBack.isKeyDown();
        in.keyLeft = gameSettings.keyBindLeft.isKeyDown();
        in.keyRight = gameSettings.keyBindRight.isKeyDown();
        in.keyJump = gameSettings.keyBindJump.isKeyDown();

        if (movement) {
            float moveForward = 0;
            float moveStrafe = 0;
            if (in.keyForward) ++moveForward;
            if (in.keyBack) --moveForward;
            if (in.keyLeft) ++moveStrafe;
            if (in.keyRight) --moveStrafe;

            EventMovementInput event = new EventMovementInput(moveForward, moveStrafe, in.keyJump);
            Arsenic.getArsenic().getEventManager().post(event);
            if (event.isCancelled()) {
                moveStrafe = 0.0F;
                moveForward = 0.0F;
            } else {
                moveForward = event.getSpeed();
                moveStrafe = event.getStrafe();
            }
            in.moveForward = moveForward;
            in.moveStrafe = moveStrafe;
            if (Math.abs(Math.abs(moveForward) - 0.3F) < 1e-4F || Math.abs(Math.abs(moveStrafe) - 0.3F) < 1e-4F) {
                in.moveScale = 0.3F;
            }
        }

        in.aiMoveSpeed = player.getAIMoveSpeed();
        if (player.onGround) {
            in.slipperiness = player.worldObj.getBlockState(new BlockPos(MathHelper.floor_double(player.posX), MathHelper.floor_double(player.getEntityBoundingBox().minY) - 1, MathHelper.floor_double(player.posZ))).getBlock().slipperiness;
        }
        return in;
    }

    public static AxisAlignedBB getPredictedBoundingBox(double precision) {
        Box b = ScaffoldCore.predictedBox(coreInput(true), precision);
        return new AxisAlignedBB(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }

    public static boolean willFallNextTick(double precision) {
        return ScaffoldCore.willFallNextTick(coreInput(true), WORLD, precision);
    }

    public static Vec3 getNewVector(Scaffold.BlockData lastblockdata) {
        if (lastblockdata == null) {
            return null;
        }
        BlockPos pos = lastblockdata.getPosition();
        EnumFacing facing = lastblockdata.getFacing();
        return getNewVector(pos, facing);
    }

    /** A point on the given face of the block, jittered around the centre of the face. */
    public static Vec3 getNewVector(BlockPos pos, EnumFacing facing) {
        double a = JavaUtils.getRandom(0.45, 0.55);
        double b = JavaUtils.getRandom(0.45, 0.55);
        Vec3 base = new Vec3(pos);
        switch (facing) {
            case UP:    return base.addVector(a, 1, b);
            case DOWN:  return base.addVector(a, 0, b);
            case EAST:  return base.addVector(1, a, b);
            case WEST:  return base.addVector(0, a, b);
            case NORTH: return base.addVector(a, b, 0);
            case SOUTH: return base.addVector(a, b, 1);
            default:    return base;
        }
    }

    public static int getBlockSlot() {
        int fallback = -1;
        for (int i = 0; i < 9; i++) {
            final ItemStack itemStack = mc.thePlayer.inventory.mainInventory[i];
            if (!isUsable(itemStack)) continue;
            if (((ItemBlock) itemStack.getItem()).getBlock().isFullBlock()) return i;
            if (fallback < 0) fallback = i;
        }
        return fallback >= 0 ? fallback : mc.thePlayer.inventory.currentItem;
    }

    public static boolean isUsable(ItemStack stack) {
        return stack != null && stack.stackSize > 0 && stack.getItem() instanceof ItemBlock
                && isBlockValid(((ItemBlock) stack.getItem()).getBlock());
    }

    private static boolean isBlockValid(final Block block) {
        return block.getMaterial().isSolid()
                && !(block instanceof BlockFalling)
                && !block.hasTileEntity()
                && block != Blocks.tnt
                && block != Blocks.command_block
                && block != Blocks.noteblock
                && block != Blocks.crafting_table
                && block != Blocks.beacon;
    }
}

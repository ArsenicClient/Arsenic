package arsenic.utils.minecraft;

import arsenic.event.impl.EventMovementInput;
import arsenic.injection.accessor.IMixinMovementInputFromOptions;
import arsenic.main.Arsenic;
import arsenic.module.impl.world.Scaffold;
import arsenic.utils.java.UtilityClass;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
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

    public static Block blockRelativeToPlayer(final double offsetX, final double offsetY, final double offsetZ) {
        return mc.theWorld.getBlockState(new BlockPos(mc.thePlayer).add(offsetX, offsetY, offsetZ)).getBlock();
    }

    public static Scaffold.BlockData getBlockData() {
        final BlockPos belowBlockPos = new BlockPos(mc.thePlayer.posX, (int) mc.thePlayer.posY - 1, mc.thePlayer.posZ);
        if (mc.theWorld.getBlockState(belowBlockPos).getBlock() instanceof BlockAir) {
            for (int x = 0; x < 4; x++) {
                for (int z = 0; z < 4; z++) {
                    for (int i = 1; i > -3; i -= 2) {
                        final BlockPos blockPos = belowBlockPos.add(x * i, 0, z * i);
                        if (mc.theWorld.getBlockState(blockPos).getBlock() instanceof BlockAir) {
                            for (EnumFacing direction : EnumFacing.values()) {
                                final BlockPos block = blockPos.offset(direction);
                                final Material material = mc.theWorld.getBlockState(block).getBlock().getMaterial();
                                if (material.isSolid() && !material.isLiquid()) {
                                    return new Scaffold.BlockData(block, direction.getOpposite());
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public static boolean willFallNextTick() {
        return willFallNextTick(1.0);
    }


    public static AxisAlignedBB getPredictedBoundingBox(double precision) {
        EntityPlayerSP player = mc.thePlayer;
        SilentRotationManager silentRotationManager = Arsenic.getArsenic().getSilentRotationManager();

        double motionX = player.motionX;
        double motionZ = player.motionZ;

        float moveForward = 0;
        float moveStrafe = 0;
        GameSettings gameSettings = ((IMixinMovementInputFromOptions) player.movementInput).getGameSettings();

        if (gameSettings.keyBindForward.isKeyDown()) {
            ++moveForward;
        }

        if (gameSettings.keyBindBack.isKeyDown()) {
            --moveForward;
        }

        if (gameSettings.keyBindLeft.isKeyDown()) {
            ++moveStrafe;
        }

        if (gameSettings.keyBindRight.isKeyDown()) {
            --moveStrafe;
        }

        EventMovementInput event = new EventMovementInput(moveForward, moveStrafe, gameSettings.keyBindJump.isKeyDown());
        Arsenic.getArsenic().getEventManager().post(event);
        if(event.isCancelled()) {
            moveStrafe = 0.0F;
            moveForward = 0.0F;
        } else {
            moveForward = event.getSpeed();
            moveStrafe = event.getStrafe();
        }

        motionX *= 0.98;
        motionZ *= 0.98;

        if (Math.abs(motionX) < 0.005) {
            motionX = (double)0.0F;
        }

        if (Math.abs(motionZ) < 0.005) {
            motionZ = (double)0.0F;
        }

        moveStrafe *= 0.98F;
        moveForward *= 0.98F;

        float f4 = 0.91F;
        if (player.onGround) {
            f4 = player.worldObj.getBlockState(new BlockPos(MathHelper.floor_double(player.posX), MathHelper.floor_double(player.getEntityBoundingBox().minY) - 1, MathHelper.floor_double(player.posZ))).getBlock().slipperiness * 0.91F;
        }

        float f = 0.16277136F / (f4 * f4 * f4);
        float f5 = player.getAIMoveSpeed() * f;

        {
            f = moveStrafe * moveStrafe + moveForward * moveForward;
            if (f >= 1.0E-4F) {
                f = MathHelper.sqrt_float(f);
                if (f < 1.0F) {
                    f = 1.0F;
                }

                f = f5 / f;
                moveStrafe *= f;
                moveForward *= f;
                float f1 = MathHelper.sin(silentRotationManager.yaw * (float) Math.PI / 180.0F);
                float f2 = MathHelper.cos(silentRotationManager.yaw * (float) Math.PI / 180.0F);
                motionX += (double) (moveStrafe * f2 - moveForward * f1);
                motionZ += (double) (moveForward * f2 + moveStrafe * f1);
            }
        }

        return player.getEntityBoundingBox().offset(motionX * precision, 0, motionZ * precision);
    }

    public static boolean willFallNextTick(double precision) {
        AxisAlignedBB predictedBB = getPredictedBoundingBox(precision).offset(0, -0.05, 0);
        return mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, predictedBB).isEmpty();
    }

    public static Vec3 getNewVector(Scaffold.BlockData lastblockdata) {
        if (lastblockdata == null) {
            return null;
        }
        BlockPos pos = lastblockdata.getPosition();
        EnumFacing facing = lastblockdata.getFacing();
        Vec3 vec3 = new Vec3(pos);

        double amount1 = 0.45 + Math.random() * 0.1;
        double amount2 = 0.45 + Math.random() * 0.1;

        if (facing == EnumFacing.UP) {
            vec3 = vec3.addVector(amount1, 1, amount2);
        } else if (facing == EnumFacing.DOWN) {
            vec3 = vec3.addVector(amount1, 0, amount2);
        } else if (facing == EnumFacing.EAST) {
            vec3 = vec3.addVector(1, amount1, amount2);
        } else if (facing == EnumFacing.WEST) {
            vec3 = vec3.addVector(0, amount1, amount2);
        } else if (facing == EnumFacing.NORTH) {
            vec3 = vec3.addVector(amount1, amount2, 0);
        } else if (facing == EnumFacing.SOUTH) {
            vec3 = vec3.addVector(amount1, amount2, 1);
        }

        return vec3;
    }

    public static int getBlockSlot() {
        for (int i = 0; i < 9; i++) {
            final ItemStack itemStack = mc.thePlayer.inventory.mainInventory[i];
            if (itemStack != null && itemStack.getItem() instanceof ItemBlock && itemStack.stackSize > 0) {
                final ItemBlock itemBlock = (ItemBlock) itemStack.getItem();

                if (isBlockValid(itemBlock.getBlock())) {
                    return i;
                }
            }
        }

        return mc.thePlayer.inventory.currentItem;
    }

    private static boolean isBlockValid(final Block block) {
        return (block.isFullBlock() || block == Blocks.glass) &&
                block != Blocks.sand &&
                block != Blocks.gravel &&
                block != Blocks.dispenser &&
                block != Blocks.command_block &&
                block != Blocks.noteblock &&
                block != Blocks.furnace &&
                block != Blocks.crafting_table &&
                block != Blocks.tnt &&
                block != Blocks.dropper &&
                block != Blocks.beacon;
    }
}
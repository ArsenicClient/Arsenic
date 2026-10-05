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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Options;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public class ScaffoldUtil extends UtilityClass {

    public static Block block(final double x, final double y, final double z) {
        return mc.level.getBlockState(BlockPos.containing(x, y, z)).getBlock();
    }

    public static boolean willFallNextTick() {
        return willFallNextTick(1.0);
    }

    public static final McScaffoldWorld WORLD = new McScaffoldWorld();

    public static ScaffoldCore.Input coreInput(boolean movement) {
        LocalPlayer player = mc.player;
        SilentRotationManager silentRotationManager = Arsenic.getArsenic().getSilentRotationManager();
        ScaffoldCore.Input in = new ScaffoldCore.Input();
        in.posX = player.getX();
        in.posY = player.getY();
        in.posZ = player.getZ();
        Vec3 motion = player.getDeltaMovement();
        in.motionX = motion.x;
        in.motionY = motion.y;
        in.motionZ = motion.z;
        in.onGround = player.onGround();
        in.eyeHeight = player.getEyeHeight();
        in.cameraYaw = player.getYRot();
        in.silentYaw = silentRotationManager.yaw;
        in.silentPitch = silentRotationManager.pitch;
        in.gcd = RotationUtils.getGCD();

        Options gameSettings = mc.options;
        in.keyForward = gameSettings.keyUp.isDown();
        in.keyBack = gameSettings.keyDown.isDown();
        in.keyLeft = gameSettings.keyLeft.isDown();
        in.keyRight = gameSettings.keyRight.isDown();
        in.keyJump = gameSettings.keyJump.isDown();

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

        in.aiMoveSpeed = player.getSpeed();
        if (player.onGround()) {
            in.slipperiness = player.level().getBlockState(new BlockPos(Mth.floor(player.getX()), Mth.floor(player.getBoundingBox().minY) - 1, Mth.floor(player.getZ()))).getBlock().getFriction();
        }
        return in;
    }

    public static AABB getPredictedBoundingBox(double precision) {
        Box b = ScaffoldCore.predictedBox(coreInput(true), precision);
        return new AABB(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }

    public static boolean willFallNextTick(double precision) {
        return ScaffoldCore.willFallNextTick(coreInput(true), WORLD, precision);
    }

    public static Vec3 getNewVector(Scaffold.BlockData lastblockdata) {
        if (lastblockdata == null) {
            return null;
        }
        BlockPos pos = lastblockdata.getPosition();
        Direction facing = lastblockdata.getFacing();
        return getNewVector(pos, facing);
    }

    /** A point on the given face of the block, jittered around the centre of the face. */
    public static Vec3 getNewVector(BlockPos pos, Direction facing) {
        double a = JavaUtils.getRandom(0.45, 0.55);
        double b = JavaUtils.getRandom(0.45, 0.55);
        Vec3 base = Vec3.atLowerCornerOf(pos);
        switch (facing) {
            case UP:    return base.add(a, 1, b);
            case DOWN:  return base.add(a, 0, b);
            case EAST:  return base.add(1, a, b);
            case WEST:  return base.add(0, a, b);
            case NORTH: return base.add(a, b, 0);
            case SOUTH: return base.add(a, b, 1);
            default:    return base;
        }
    }

    /**
     * Whether a block can go on {@code side} of {@code pos} - 1.8's ItemBlock#canPlaceBlockOnSide,
     * which only ever asked whether the target space could be replaced.
     */
    public static boolean canPlaceOnSide(BlockPos pos, Direction side) {
        BlockPos target = pos.relative(side);
        return mc.level.isInWorldBounds(target) && mc.level.getBlockState(target).canBeReplaced();
    }

    /** Right-clicks {@code side} of {@code pos} with the held item at {@code hitVec}, like a player placing a block. */
    public static boolean placeBlock(BlockPos pos, Direction side, Vec3 hitVec) {
        return mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hitVec, side, pos, false)).consumesAction();
    }

    public static int getBlockSlot() {
        int fallback = -1;
        for (int i = 0; i < 9; i++) {
            final ItemStack itemStack = mc.player.getInventory().getItem(i);
            if (!isUsable(itemStack)) continue;
            if (((BlockItem) itemStack.getItem()).getBlock().defaultBlockState().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) return i;
            if (fallback < 0) fallback = i;
        }
        return fallback >= 0 ? fallback : mc.player.getInventory().getSelectedSlot();
    }

    public static boolean isUsable(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem
                && isBlockValid(((BlockItem) stack.getItem()).getBlock());
    }

    private static boolean isBlockValid(final Block block) {
        return block.defaultBlockState().isSolid()
                && !(block instanceof FallingBlock)
                && !(block instanceof EntityBlock)
                && block != Blocks.TNT
                && block != Blocks.COMMAND_BLOCK
                && block != Blocks.NOTE_BLOCK
                && block != Blocks.CRAFTING_TABLE
                && block != Blocks.BEACON;
    }
}

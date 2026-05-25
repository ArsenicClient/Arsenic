package arsenic.module.impl.world;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.event.impl.EventUpdate;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.MoveUtil;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.minecraft.ScaffoldUtil;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import ibxm.Player;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;

import static arsenic.utils.rotations.RotationUtils.clamp;
import static arsenic.utils.rotations.RotationUtils.patchGCD;

@ModuleInfo(name = "Scaffold", category = ModuleCategory.PLAYER)
public class Scaffold extends Module {

    //scaffold variables
    private BlockData blockData;
    private BlockData lastBlockData;
    private float[] rots;


    @Override
    protected void onEnable() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        blockData = null;
        lastBlockData = null;
        super.onEnable();
    }


    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        if (ScaffoldUtil.getBlockData() != null && willFallNextTick()) {
            lastBlockData = ScaffoldUtil.getBlockData();
            rots = RotationUtils.getRotations(ScaffoldUtil.getBlockData().getPosition());
        }

        if (lastBlockData == null) {
            rots = new float[]{mc.thePlayer.rotationYaw + 180, 75};
        }

        event.setYaw(rots[0]);
        event.setPitch(rots[1]);
        event.setSpeed(360f);
        if (mc.thePlayer.inventory.getCurrentItem() == null || !(mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemBlock)) {
            mc.thePlayer.inventory.currentItem = ScaffoldUtil.getBlockSlot();
        } else {
            place();
        }
    };

    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        double offsetX = -0.1 * Math.sin(Math.toRadians(MoveUtil.getDirection()));
        double offsetZ = -0.1 * Math.cos(Math.toRadians(MoveUtil.getDirection()));

        BlockPos renderPos = blockData != null ? blockData.getPosition() : new BlockPos(
                mc.thePlayer.posX + offsetX,
                mc.thePlayer.posY - 1,
                mc.thePlayer.posZ + offsetZ
        );

        RenderUtils.renderBlock(renderPos, Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor(), true, false);
    };

    private void place() {
        blockData = ScaffoldUtil.getBlockData();
        if (blockData == null) {
            return;
        }
        MovingObjectPosition objectOver = mc.objectMouseOver;
        BlockPos blockpos = mc.objectMouseOver.getBlockPos();
        if (objectOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || mc.theWorld.getBlockState(blockpos).getBlock().getMaterial() == Material.air) {
            return;
        }

        mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(),
                blockData.position, blockData.facing, ScaffoldUtil.getNewVector(blockData)
        );

        mc.thePlayer.swingItem();
    }

    public boolean willFallNextTick() {
        EntityPlayerSP player = mc.thePlayer;
        if (!player.onGround)
            return true;

        double nextX = player.posX + player.motionX * 1.2;
        double nextZ = player.posZ + player.motionZ * 1.2;
        double nextY = player.posY + player.motionY * 1.2;

        double halfWidth = 0.29;

        double[][] checkPoints = {
                {nextX,             nextZ},
                {nextX + halfWidth, nextZ + halfWidth},
                {nextX - halfWidth, nextZ + halfWidth},
                {nextX + halfWidth, nextZ - halfWidth},
                {nextX - halfWidth, nextZ - halfWidth},
        };

        for (double[] point : checkPoints) {
            BlockPos groundPos = new BlockPos(
                    MathHelper.floor_double(point[0]),
                    MathHelper.floor_double(nextY) - 1, // block directly beneath feet
                    MathHelper.floor_double(point[1])
            );

            Block block = mc.theWorld.getBlockState(groundPos).getBlock();

            // If any corner has a solid block beneath it, player won't fall
            if (block.getMaterial() != Material.air) {
                return false;
            }
        }

        return true;
    }

    public static float[] getRotations(final BlockPos blockPos) {
        final double x = blockPos.getX() + 0.45 - mc.thePlayer.posX;
        final double y = blockPos.getY() + 0.45 - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        final double z = blockPos.getZ() + 0.45 - mc.thePlayer.posZ;
        float[] targetRots = new float[]{mc.thePlayer.rotationYaw + MathHelper.wrapAngleTo180_float((float) (Math.atan2(z, x) * 57.295780181884766) - 90.0f - mc.thePlayer.rotationYaw), clamp(mc.thePlayer.rotationPitch + MathHelper.wrapAngleTo180_float((float) (-(Math.atan2(y, MathHelper.sqrt_double(x * x + z * z)) * 57.295780181884766)) - mc.thePlayer.rotationPitch))};

        float currentYaw = Arsenic.getArsenic().getSilentRotationManager().yaw;
        float currentPitch = Arsenic.getArsenic().getSilentRotationManager().pitch;

        float[] lastRots = new float[]{currentYaw, currentPitch};
        float[] fixedRots = patchGCD(lastRots, targetRots);
        return fixedRots;
    }


    public static class BlockData {
        private BlockPos position;

        private EnumFacing facing;

        public BlockData(final BlockPos position, final EnumFacing facing) {
            this.position = position;
            this.facing = facing;
        }

        public EnumFacing getFacing() {
            return facing;
        }

        public BlockPos getPosition() {
            return position;
        }
    }
}
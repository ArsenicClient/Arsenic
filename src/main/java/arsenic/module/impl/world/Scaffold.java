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
import net.minecraft.block.material.Material;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;

@ModuleInfo(name = "Scaffold", category = ModuleCategory.PLAYER)
public class Scaffold extends Module {

    public BooleanProperty sideCast = new BooleanProperty("sideHit cast", true);

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
        if (ScaffoldUtil.getBlockData() != null) {
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
        if(objectOver.sideHit != blockData.getFacing() && sideCast.getValue()) {
            return;
        }

        mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(),
                blockData.position, blockData.facing, ScaffoldUtil.getNewVector(blockData)
        );

        mc.thePlayer.swingItem();
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
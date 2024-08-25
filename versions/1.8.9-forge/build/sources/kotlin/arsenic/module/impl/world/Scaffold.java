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
import arsenic.utils.minecraft.ScaffoldUtil;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
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
    public BooleanProperty sprint = new BooleanProperty("Sprint", false);
    public DoubleProperty slowDown = new DoubleProperty("Motion", new DoubleValue(0, 100, 100, 1));
    public BooleanProperty moveFix = new BooleanProperty("MoveFix", false);
    public BooleanProperty hypixckel = new BooleanProperty("Hypixel Keep-Y", false);
    public BooleanProperty raycast = new BooleanProperty("Raycast", false);

    //scaffold variables
    private BlockData blockData;
    private BlockData lastBlockData;
    private float[] rots;

    // Bypass variables
    public static int scaffoldYCoord;

    // Hypixel KeepY
    private boolean hypixelStartSprint;
    private int blocksPlaced;
    private boolean checkGround;
    private boolean wasEnabled;


    @Override
    protected void onEnable() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        mc.thePlayer.setSprinting(false);
        hypixelStartSprint = false;
        checkGround = false;
        wasEnabled = false;
        blockData = null;
        lastBlockData = null;
        scaffoldYCoord = 0;
        blocksPlaced = 0;
        super.onEnable();
    }

    @EventLink
    public final Listener<EventTick> eventTickListener = event -> {
        if (mc.thePlayer.inventory.getCurrentItem() == null || !(mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemBlock)) {
            mc.thePlayer.inventory.currentItem = ScaffoldUtil.getBlockSlot();
            return;
        }
        place();
    };

    @EventLink
    public final Listener<EventUpdate.Pre> eventUpdateListener = event -> {
        if (mc.thePlayer.inventory.getCurrentItem() == null || !(mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemBlock)) {
            return;
        }
        if (mc.thePlayer.onGround) {
            mc.thePlayer.motionX *= (slowDown.getValue().getInput() / 100);
            mc.thePlayer.motionZ *= (slowDown.getValue().getInput() / 100);
        }

        if (hypixckel.getValue()) {
            if (!Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
                if (mc.thePlayer.onGround) {
                    scaffoldYCoord = (int) mc.thePlayer.posY - 1;
                }
            } else {
                scaffoldYCoord = (int) mc.thePlayer.posY - 1;
            }
        } else {
            scaffoldYCoord = (int) mc.thePlayer.posY - 1;
        }

        if (mc.gameSettings.keyBindJump.isKeyDown()) {
            setSprint();
            if (hypixckel.getValue()) {
                int inAirTicks = Arsenic.getInstance().getServerInfo().offGroundTicks;
                if (MoveUtil.isMoving()) {
                    if (mc.thePlayer.onGround) {
                        mc.thePlayer.motionY = 0.4196;
                        checkGround = true;
                    }
                    if (checkGround) {
                        if (inAirTicks == 3) mc.thePlayer.motionY = 0;
                        if (inAirTicks == 4) mc.thePlayer.motionY = 0;
                        if (inAirTicks == 5) mc.thePlayer.motionY = 0.4191;
                        if (inAirTicks == 6) mc.thePlayer.motionY = 0.3275;
                        if (inAirTicks == 11) mc.thePlayer.motionY = -0.5;
                        wasEnabled = true;
                    }
                }
            }
            hypixelStartSprint = false;
            blocksPlaced = 0;
            return;
        } else if (wasEnabled) {
            wasEnabled = false;
            checkGround = false;
            MoveUtil.stop();
        }

        if (hypixckel.getValue() && MoveUtil.isMoving() && blocksPlaced > 2) {
            if (mc.thePlayer.onGround) {
                mc.thePlayer.jump();
            }
            if (!mc.thePlayer.onGround && Arsenic.getInstance().getServerInfo().offGroundTicks == 4) {
                MovingObjectPosition objectOver = mc.objectMouseOver;
                BlockPos blockpos = mc.objectMouseOver.getBlockPos();
                ItemStack itemstack = mc.thePlayer.inventory.getCurrentItem();

                if (objectOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || mc.theWorld.getBlockState(blockpos).getBlock().getMaterial() == Material.air) {
                    return;
                }

                if (itemstack != null && !(itemstack.getItem() instanceof ItemBlock)) {
                    return;
                }

                mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, itemstack, blockpos, objectOver.sideHit, objectOver.hitVec);

                mc.thePlayer.swingItem();

                hypixelStartSprint = true;
            }
        }
        setSprint();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        if (ScaffoldUtil.getBlockData() != null) {
            lastBlockData = ScaffoldUtil.getBlockData();
            rots = RotationUtils.getRotations(ScaffoldUtil.getBlockData().getPosition());
        }

        if (lastBlockData == null || mc.gameSettings.keyBindJump.isKeyDown()) {
            rots = new float[]{mc.thePlayer.rotationYaw + 180, 75};
        }
        event.setJumpFix(moveFix.getValue());
        event.setDoMovementFix(moveFix.getValue());
        event.setYaw(rots[0]);
        event.setPitch(rots[1]);
        event.setSpeed(180f);
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
        if (raycast.getValue()) {
            MovingObjectPosition objectOver = mc.objectMouseOver;
            BlockPos blockpos = mc.objectMouseOver.getBlockPos();

            if (objectOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || mc.theWorld.getBlockState(blockpos).getBlock().getMaterial() == Material.air) {
                return;
            }
        }
        mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(),
                blockData.position, blockData.facing, ScaffoldUtil.getNewVector(blockData)
        );

        mc.thePlayer.swingItem();
        blocksPlaced++;
    }

    private void setSprint() {
        if (hypixckel.getValue()) {
            if (mc.gameSettings.keyBindJump.isKeyDown()) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
                mc.thePlayer.setSprinting(true);
            } else {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), hypixelStartSprint);
                mc.thePlayer.setSprinting(hypixelStartSprint);

            }
        } else {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), sprint.getValue());
            mc.thePlayer.setSprinting(sprint.getValue());
        }
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
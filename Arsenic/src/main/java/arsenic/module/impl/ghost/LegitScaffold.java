package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.MoveUtil;
import arsenic.utils.minecraft.ScaffoldUtil;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.block.BlockAir;
import net.minecraft.block.material.Material;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

@ModuleInfo(name = "LegitScaffold", category = ModuleCategory.GHOST, dev = true)
public class LegitScaffold extends Module {

    public final EnumProperty<ScaffoldMode> mode = new EnumProperty<>("Mode", ScaffoldMode.BOTH);
    public final DoubleProperty rotationSpeed = new DoubleProperty("Speed", new DoubleValue(1, 180, 180, 5));
    public final BooleanProperty autoSwitch = new BooleanProperty("Auto Switch", true);
    public final BooleanProperty render = new BooleanProperty("Render", true);

    private BlockPos targetBlock;
    private BlockPos lastTargetBlock;
    private EnumFacing targetFacing;
    private int placeCooldown;

    @Override
    protected void onEnable() {
        targetBlock = null;
        lastTargetBlock = null;
        targetFacing = null;
        placeCooldown = 0;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> eventTickListener = event -> {
        ScaffoldMode m = mode.getValue();
        if (m == ScaffoldMode.EAGLE || m == ScaffoldMode.BOTH) eagle();
        if (m == ScaffoldMode.PLACE || m == ScaffoldMode.BOTH) place();
        if (placeCooldown > 0) placeCooldown--;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> rotationListener = event -> {
        if (!MoveUtil.isMoving()) return;

        float yaw = MoveUtil.getDirection() + 180;
        float pitch = 80;

        float speed = (float) rotationSpeed.getValue().getInput();;

        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setSpeed(speed);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> renderListener = event -> {
        if (!render.getValue() || targetBlock == null) return;
        int color = client.getThemeManager().getCurrentTheme().getMainColor();
        RenderUtils.renderBlock(targetBlock, color, true, false);
    };

    private void eagle() {
        if (!MoveUtil.isMoving()) {
            setShift(false);
            return;
        }

        BlockPos below = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1, mc.thePlayer.posZ);
        if (mc.theWorld.getBlockState(below).getBlock().getMaterial().isSolid()) {
            setShift(false);
            return;
        }

        double speed = Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
        double lookahead = Math.max(0.5, 1.0 + speed * 2.0);
        double predictedX = mc.thePlayer.posX + mc.thePlayer.motionX * lookahead;
        double predictedZ = mc.thePlayer.posZ + mc.thePlayer.motionZ * lookahead;

        BlockPos ahead = new BlockPos(predictedX, mc.thePlayer.posY - 1, predictedZ);
        setShift(mc.theWorld.getBlockState(ahead).getBlock() instanceof BlockAir);
    }

    private void place() {
        if (!MoveUtil.isMoving()) return;

        if (!(mc.thePlayer.inventory.getCurrentItem() != null && mc.thePlayer.inventory.getCurrentItem().getItem() instanceof ItemBlock)) {
            if (autoSwitch.getValue()) {
                int slot = ScaffoldUtil.getBlockSlot();
                if (slot != -1 && slot != mc.thePlayer.inventory.currentItem) {
                    mc.thePlayer.inventory.currentItem = slot;
                }
            }
            targetBlock = null;
            targetFacing = null;
            return;
        }

        BlockPos below = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1, mc.thePlayer.posZ);
        if (mc.theWorld.getBlockState(below).getBlock().getMaterial().isSolid()) {
            targetBlock = null;
            targetFacing = null;
            return;
        }

        if (placeCooldown > 0) return;

        BlockData data = findBlockData(below);
        if (data != null) {
            targetBlock = data.pos;
            lastTargetBlock = data.pos;
            targetFacing = data.face;

            mc.playerController.onPlayerRightClick(
                    mc.thePlayer, mc.theWorld,
                    mc.thePlayer.inventory.getCurrentItem(),
                    data.neighbor, data.face,
                    new Vec3(data.neighbor).addVector(0.5, 0.5, 0.5)
            );
            mc.thePlayer.swingItem();
            placeCooldown = 1;
        } else if (lastTargetBlock != null) {
            BlockData retry = findBlockData(below);
            if (retry == null) {
                targetBlock = null;
                targetFacing = null;
            }
        }
    }

    private BlockData findBlockData(BlockPos below) {
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                for (int i = 1; i > -3; i -= 2) {
                    BlockPos check = below.add(x * i, 0, z * i);
                    if (mc.theWorld.getBlockState(check).getBlock() instanceof BlockAir) {
                        for (EnumFacing dir : EnumFacing.values()) {
                            BlockPos neighbor = check.offset(dir);
                            Material mat = mc.theWorld.getBlockState(neighbor).getBlock().getMaterial();
                            if (mat.isSolid() && !mat.isLiquid()) {
                                return new BlockData(check, dir.getOpposite(), neighbor);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected void onDisable() {
        setShift(false);
        targetBlock = null;
        lastTargetBlock = null;
        targetFacing = null;
        placeCooldown = 0;
    }

    private void setShift(boolean sh) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), sh);
    }

    private static class BlockData {
        final BlockPos pos;
        final EnumFacing face;
        final BlockPos neighbor;

        BlockData(BlockPos pos, EnumFacing face, BlockPos neighbor) {
            this.pos = pos;
            this.face = face;
            this.neighbor = neighbor;
        }
    }

    public enum ScaffoldMode {
        EAGLE, PLACE, BOTH
    }
}

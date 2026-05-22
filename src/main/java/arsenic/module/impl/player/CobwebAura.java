package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.event.impl.EventUpdate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.timer.MSTimer;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

@ModuleInfo(name = "CobwebAura", category = ModuleCategory.PLAYER)
public class CobwebAura extends Module {

    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(3, 10, 5, 0.5));
    public final DoubleProperty delay = new DoubleProperty("Delay (ms)", new DoubleValue(100, 1000, 200, 50));
    public final BooleanProperty placeAtFeet = new BooleanProperty("Place at feet", true);
    public final BooleanProperty rotate = new BooleanProperty("Rotate", true);

    private final MSTimer timer = new MSTimer();
    private boolean shouldRotate;
    private float[] rotations;

    @Override
    protected void onEnable() {
        shouldRotate = false;
        rotations = null;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (!timer.hasTimeElapsed((long) delay.getValue().getInput())) return;

        int cobwebSlot = getCobwebSlot();
        if (cobwebSlot == -1) return;

        EntityPlayer target = findTarget();
        if (target == null) return;

        BlockPos targetPos = new BlockPos(target);
        BlockPos placePos = placeAtFeet.getValue() ? targetPos : targetPos.down();

        if (!mc.theWorld.isAirBlock(placePos)) return;

        BlockPos neighbor = findNeighbor(placePos);
        if (neighbor == null) return;

        EnumFacing side = getFacing(placePos, neighbor);

        if (rotate.getValue()) {
            rotations = getRotations(neighbor, side);
            shouldRotate = true;
        }

        int oldSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = cobwebSlot;

        mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld,
                mc.thePlayer.getHeldItem(), neighbor, side,
                new Vec3(neighbor.getX() + 0.5, neighbor.getY() + 0.5, neighbor.getZ() + 0.5));
        mc.thePlayer.swingItem();

        mc.thePlayer.inventory.currentItem = oldSlot;
        shouldRotate = false;
        timer.reset();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventUpdate.Pre> onUpdate = event -> {
        if (shouldRotate && rotations != null) {
            event.setYaw(rotations[0]);
            event.setPitch(rotations[1]);
        }
    };

    private EntityPlayer findTarget() {
        EntityPlayer closest = null;
        double closestDist = range.getValue().getInput();
        for (EntityPlayer ep : mc.theWorld.playerEntities) {
            if (ep == mc.thePlayer || ep.isDead) continue;
            double dist = mc.thePlayer.getDistanceToEntity(ep);
            if (dist <= closestDist) {
                closestDist = dist;
                closest = ep;
            }
        }
        return closest;
    }

    private int getCobwebSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemBlock) {
                Block block = ((ItemBlock) stack.getItem()).getBlock();
                if (block == Blocks.web) return i;
            }
        }
        return -1;
    }

    private BlockPos findNeighbor(BlockPos pos) {
        BlockPos[] checks = {pos.down(), pos.up(), pos.north(), pos.south(), pos.east(), pos.west()};
        for (BlockPos check : checks) {
            if (!mc.theWorld.isAirBlock(check)) return check;
        }
        return null;
    }

    private EnumFacing getFacing(BlockPos placePos, BlockPos neighbor) {
        for (EnumFacing facing : EnumFacing.values()) {
            if (placePos.offset(facing).equals(neighbor)) return facing.getOpposite();
        }
        return EnumFacing.UP;
    }

    private float[] getRotations(BlockPos pos, EnumFacing facing) {
        double x = pos.getX() + 0.5 - mc.thePlayer.posX + (double) facing.getFrontOffsetX() / 2.0;
        double z = pos.getZ() + 0.5 - mc.thePlayer.posZ + (double) facing.getFrontOffsetZ() / 2.0;
        double y = pos.getY() + 0.5 - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight()) + (double) facing.getFrontOffsetY() / 2.0;
        double dist = MathHelper.sqrt_double(x * x + z * z);
        float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) (-(Math.atan2(y, dist) * 180.0 / Math.PI));
        return new float[]{
                mc.thePlayer.rotationYaw + MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw),
                mc.thePlayer.rotationPitch + MathHelper.wrapAngleTo180_float(pitch - mc.thePlayer.rotationPitch)
        };
    }
}

package arsenic.module.impl.player;

import arsenic.module.property.impl.SliderScale;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.injection.accessor.IMixinEntity;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.ButtonProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Mouse;

@ModuleInfo(name = "Nuker", category = ModuleCategory.PLAYER, tier = ModuleTier.EXTRA)
public class Nuker extends Module {

    public final EnumProperty<TargetBlock> targetBlock = new EnumProperty<>("Block", TargetBlock.Wheat);

    @PropertyInfo(reliesOn = "Block", value = "Custom")
    public final DoubleProperty blockId = new DoubleProperty("Block ID", new DoubleValue(1, 255, 59, 1));

    @PropertyInfo(reliesOn = "Block", value = "Custom")
    public final ButtonProperty pickBlock = new ButtonProperty("Pick Block", "Use looked-at", this::pickLookedAtBlock);

    public final BooleanProperty onlyGrown = new BooleanProperty("Only Grown", true);
    public final BooleanProperty holdClick = new BooleanProperty("Hold Click", true);
    public final BooleanProperty rotate = new BooleanProperty("Rotate", true);
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(1, 6, 4.5, 0.1));
    @PropertyInfo(reliesOn = "Rotate", value = "true")
    public final DoubleProperty rotSpeed = new DoubleProperty("Rotation Speed", new DoubleValue(1, 360, 90, 1), SliderScale.LOG);

    private static final float ON_TARGET_DEG = 0.01f;

    private BlockPos target;
    private Vec3 aimPoint;
    private BlockPos breakPos;
    private EnumFacing breakFace;

    public enum TargetBlock {
        Wheat, Custom
    }

    @Override
    protected void onDisable() {
        target = null;
        aimPoint = null;
        breakPos = null;
        if (mc.playerController != null)
            mc.playerController.resetBlockRemoving();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (!active()) {
            if (target != null)
                mc.playerController.resetBlockRemoving();
            target = null;
            aimPoint = null;
            return;
        }

        SilentRotationManager srm = Arsenic.getArsenic().getSilentRotationManager();
        if (target == null || !isTarget(target) || findAim(target, srm.yaw, srm.pitch) == null) {
            pickTarget(srm.yaw, srm.pitch);
        } else {
            aimPoint = findAim(target, srm.yaw, srm.pitch);
        }
        if (target == null)
            return;

        event.setBlockUserInput(true);
        if (!rotate.getValue())
            return;

        float[] rots = rotationsTo(aimPoint);
        float turn = angleBetween(rots[0], rots[1], srm.yaw, srm.pitch);
        if (turn > ON_TARGET_DEG) {
            event.setYaw(rots[0]);
            event.setPitch(rots[1]);
            event.setSpeed((float) rotSpeed.getValue().getInput());
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onRotationPost = event -> {
        breakPos = null;
        if (target == null)
            return;
        MovingObjectPosition hit;
        if (rotate.getValue()) {
            hit = rayTrace(event.getYaw(), event.getPitch());
        } else {
            Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
            hit = mc.theWorld.rayTraceBlocks(eyes, extend(eyes, aimPoint), false, false, true);
        }
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && target.equals(hit.getBlockPos())) {
            breakPos = target;
            breakFace = hit.sideHit;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (breakPos == null || !active() || !isTarget(breakPos))
            return;
        mc.playerController.onPlayerDamageBlock(breakPos, breakFace);
        mc.thePlayer.swingItem();
    };

    private boolean active() {
        return mc.currentScreen == null && (!holdClick.getValue() || Mouse.isButtonDown(0));
    }

    private void pickTarget(float yaw, float pitch) {
        target = null;
        aimPoint = null;
        float bestTurn = Float.MAX_VALUE;
        int r = (int) Math.ceil(range.getValue().getInput());
        BlockPos origin = new BlockPos(mc.thePlayer.getPositionEyes(1f));
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (!isTarget(pos))
                        continue;
                    Vec3 point = findAim(pos, yaw, pitch);
                    if (point == null)
                        continue;
                    float[] rots = rotationsTo(point);
                    float turn = angleBetween(rots[0], rots[1], yaw, pitch);
                    if (turn < bestTurn) {
                        bestTurn = turn;
                        target = pos;
                        aimPoint = point;
                    }
                }
            }
        }
    }

    private Vec3 findAim(BlockPos pos, float yaw, float pitch) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        block.setBlockBoundsBasedOnState(mc.theWorld, pos);
        AxisAlignedBB box = block.getSelectedBoundingBox(mc.theWorld, pos).contract(0.05, 0.05, 0.05);
        Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
        Vec3 look = ((IMixinEntity) mc.thePlayer).invokeGetVectorForRotation(pitch, yaw);
        double reach = range.getValue().getInput();

        Vec3 best = null;
        float bestTurn = Float.MAX_VALUE;
        for (double t = 0; t <= reach; t += 0.25) {
            Vec3 p = eyes.addVector(look.xCoord * t, look.yCoord * t, look.zCoord * t);
            Vec3 q = new Vec3(MathHelper.clamp_double(p.xCoord, box.minX, box.maxX),
                    MathHelper.clamp_double(p.yCoord, box.minY, box.maxY),
                    MathHelper.clamp_double(p.zCoord, box.minZ, box.maxZ));
            float[] rots = rotationsTo(q);
            float turn = angleBetween(rots[0], rots[1], yaw, pitch);
            if (turn < bestTurn) {
                bestTurn = turn;
                best = q;
            }
        }
        if (best != null && canSee(pos, eyes, best))
            return best;
        Vec3 centre = new Vec3((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2);
        return canSee(pos, eyes, centre) ? centre : null;
    }

    private boolean canSee(BlockPos pos, Vec3 eyes, Vec3 point) {
        if (eyes.distanceTo(point) > range.getValue().getInput())
            return false;
        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyes, extend(eyes, point), false, false, true);
        return mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && pos.equals(mop.getBlockPos());
    }

    private static Vec3 extend(Vec3 eyes, Vec3 point) {
        Vec3 dir = point.subtract(eyes).normalize();
        return point.addVector(dir.xCoord * 0.1, dir.yCoord * 0.1, dir.zCoord * 0.1);
    }

    private MovingObjectPosition rayTrace(float yaw, float pitch) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
        Vec3 look = ((IMixinEntity) mc.thePlayer).invokeGetVectorForRotation(pitch, yaw);
        double reach = range.getValue().getInput();
        Vec3 end = eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        return mc.theWorld.rayTraceBlocks(eyes, end, false, false, true);
    }

    private boolean isTarget(BlockPos pos) {
        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        if (block.getMaterial() == Material.air)
            return false;
        Block wanted = targetBlock.getValue() == TargetBlock.Wheat
                ? Blocks.wheat
                : Block.getBlockById((int) blockId.getValue().getInput());
        if (block != wanted)
            return false;
        if (onlyGrown.getValue() && block instanceof BlockCrops)
            return state.getValue(BlockCrops.AGE) >= 7;
        return true;
    }

    private void pickLookedAtBlock() {
        if (mc.thePlayer == null || mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)
            return;
        Block block = mc.theWorld.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock();
        blockId.getValue().setInput(Block.getIdFromBlock(block));
        targetBlock.setValue(TargetBlock.Custom);
    }

    private float[] rotationsTo(Vec3 point) {
        return RotationUtils.rotationsTo(mc.thePlayer.getPositionEyes(1f), point);
    }

    private static float angleBetween(float yawA, float pitchA, float yawB, float pitchB) {
        float dYaw = RotationUtils.getYawDifference(yawA, yawB);
        float dPitch = pitchA - pitchB;
        return (float) Math.sqrt(dYaw * dYaw + dPitch * dPitch);
    }
}

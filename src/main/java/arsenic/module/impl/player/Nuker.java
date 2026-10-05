package arsenic.module.impl.player;

import arsenic.module.property.impl.SliderScale;
import arsenic.asm.RequiresPlayer;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import arsenic.utils.io.Keys;

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
    private Direction breakFace;

    public enum TargetBlock {
        Wheat, Custom
    }

    @Override
    protected void onDisable() {
        target = null;
        aimPoint = null;
        breakPos = null;
        if (mc.gameMode != null)
            mc.gameMode.stopDestroyBlock();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (!active()) {
            if (target != null)
                mc.gameMode.stopDestroyBlock();
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
        BlockHitResult hit;
        if (rotate.getValue()) {
            hit = rayTrace(event.getYaw(), event.getPitch());
        } else {
            Vec3 eyes = mc.player.getEyePosition(1f);
            hit = PlayerUtils.rayTraceBlocks(eyes, extend(eyes, aimPoint));
        }
        if (hit.getType() == HitResult.Type.BLOCK
                && target.equals(hit.getBlockPos())) {
            breakPos = target;
            breakFace = hit.getDirection();
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (breakPos == null || !active() || !isTarget(breakPos))
            return;
        mc.gameMode.continueDestroyBlock(breakPos, breakFace);
        PlayerUtils.swingItem();
    };

    private boolean active() {
        return mc.gui.screen() == null && (!holdClick.getValue() || Keys.isMouseDown(0));
    }

    private void pickTarget(float yaw, float pitch) {
        target = null;
        aimPoint = null;
        float bestTurn = Float.MAX_VALUE;
        int r = (int) Math.ceil(range.getValue().getInput());
        BlockPos origin = BlockPos.containing(mc.player.getEyePosition(1f));
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = origin.offset(x, y, z);
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
        VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
        AABB box = (shape.isEmpty() ? new AABB(BlockPos.ZERO) : shape.bounds()).move(pos).deflate(0.05);
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 look = net.minecraft.world.entity.Entity.calculateViewVector(pitch, yaw);
        double reach = range.getValue().getInput();

        Vec3 best = null;
        float bestTurn = Float.MAX_VALUE;
        for (double t = 0; t <= reach; t += 0.25) {
            Vec3 p = eyes.add(look.x * t, look.y * t, look.z * t);
            Vec3 q = new Vec3(Mth.clamp(p.x, box.minX, box.maxX),
                    Mth.clamp(p.y, box.minY, box.maxY),
                    Mth.clamp(p.z, box.minZ, box.maxZ));
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
        BlockHitResult mop = PlayerUtils.rayTraceBlocks(eyes, extend(eyes, point));
        return mop.getType() == HitResult.Type.BLOCK && pos.equals(mop.getBlockPos());
    }

    private static Vec3 extend(Vec3 eyes, Vec3 point) {
        Vec3 dir = point.subtract(eyes).normalize();
        return point.add(dir.x * 0.1, dir.y * 0.1, dir.z * 0.1);
    }

    private BlockHitResult rayTrace(float yaw, float pitch) {
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 look = net.minecraft.world.entity.Entity.calculateViewVector(pitch, yaw);
        double reach = range.getValue().getInput();
        Vec3 end = eyes.add(look.x * reach, look.y * reach, look.z * reach);
        return PlayerUtils.rayTraceBlocks(eyes, end);
    }

    private boolean isTarget(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        Block block = state.getBlock();
        if (block.defaultBlockState().isAir())
            return false;
        Block wanted = targetBlock.getValue() == TargetBlock.Wheat
                ? Blocks.WHEAT
                : BuiltInRegistries.BLOCK.byId((int) blockId.getValue().getInput());
        if (block != wanted)
            return false;
        if (onlyGrown.getValue() && block instanceof CropBlock crop)
            return crop.isMaxAge(state);
        return true;
    }

    private void pickLookedAtBlock() {
        if (mc.player == null || mc.hitResult == null
                || mc.hitResult.getType() != HitResult.Type.BLOCK)
            return;
        Block block = mc.level.getBlockState(((BlockHitResult) mc.hitResult).getBlockPos()).getBlock();
        blockId.getValue().setInput(BuiltInRegistries.BLOCK.getId(block));
        targetBlock.setValue(TargetBlock.Custom);
    }

    private float[] rotationsTo(Vec3 point) {
        return RotationUtils.rotationsTo(mc.player.getEyePosition(1f), point);
    }

    private static float angleBetween(float yawA, float pitchA, float yawB, float pitchB) {
        float dYaw = RotationUtils.getYawDifference(yawA, yawB);
        float dPitch = pitchA - pitchB;
        return (float) Math.sqrt(dYaw * dYaw + dPitch * dPitch);
    }
}

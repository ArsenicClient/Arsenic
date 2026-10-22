import net.minecraft.world.entity.Entity;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.SliderScale;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.injection.accessor.IMixinMultiPlayerGameMode;
import arsenic.utils.minecraft.BedwarsTracker;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.render.ScaledResolution;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;

/**
 * BedWars bed breaker. Finds the nearest enemy bed, aims at it and mines it; if something is in the
 * way it mines whatever the line to the bed hits first instead. Your own bed (from BedwarsTracker) is skipped.
 */
@ModuleInfo(name = "Breaker", category = ModuleCategory.PLAYER, tier = ModuleTier.BLATANT)
public class Breaker extends Module {

    public final EnumProperty<BreakMode> breakMode = new EnumProperty<>("Break Mode", BreakMode.Legit);
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(3, 4.5, 4.5, 0.1));
    @ShowWhen(property = "Break Mode", is = "Legit")
    public final DoubleProperty rotSpeed = new DoubleProperty("Rotation Speed", new DoubleValue(1, 360, 120, 1), SliderScale.LOG);

    public enum BreakMode {
        /** Mines the bed, or whatever blocks the line of sight to it, at vanilla pace. */
        Legit,
        /** Through walls (reach only, no line of sight): mines a block next to a covered bed first, then the bed. No hit delay, snap aim. More obvious, but faster. */
        Hypixel
    }

    private static final float ON_TARGET_DEG = 0.01f;
    private static final double INSET = 0.12;

    private BlockPos target;
    private Vec3 aimPoint;
    private BlockPos breakPos;
    private Direction breakFace;
    private int previousSlot = -1;
    private final double[] jitter = new double[3];

    @Override
    protected void onEnable() {
        target = null;
        aimPoint = null;
        breakPos = null;
    }

    @Override
    protected void onDisable() {
        stopBreaking();
        target = null;
        aimPoint = null;
        breakPos = null;
    }

    private void stopBreaking() {
        if (mc.gameMode != null)
            mc.gameMode.stopDestroyBlock();
        restoreSlot();
    }

    private void restoreSlot() {
        if (previousSlot != -1 && mc.player != null)
            mc.player.getInventory().setSelectedSlot(previousSlot);
        previousSlot = -1;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (mc.gui.screen() != null) {
            clearTarget();
            return;
        }
        pickTarget();
        if (target == null)
            return;

        event.setBlockUserInput(true);
        SilentRotationManager srm = Arsenic.getArsenic().getSilentRotationManager();
        float[] rots = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), aimPoint);
        if (angleBetween(rots[0], rots[1], srm.yaw, srm.pitch) > ON_TARGET_DEG) {
            event.setYaw(rots[0]);
            event.setPitch(rots[1]);
            event.setSpeed(hypixel() ? 360f : (float) rotSpeed.getValue().getInput());
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onRotationPost = event -> {
        breakPos = null;
        if (target == null)
            return;
        Vec3 eyes = mc.player.getEyePosition(1f);
        if (hypixel()) {
            // the dig goes straight at the bed, whatever is in between
            breakPos = target;
            breakFace = Direction.getApproximateNearest((float) (eyes.x - (target.getX() + 0.5)),
                    (float) (eyes.y - (target.getY() + 0.5)), (float) (eyes.z - (target.getZ() + 0.5)));
            return;
        }
        Vec3 look = Entity.calculateViewVector(event.getPitch(), event.getYaw());
        double reach = range.getValue().getInput();
        BlockHitResult hit = PlayerUtils.rayTraceBlocks(eyes,
                eyes.add(look.x * reach, look.y * reach, look.z * reach));
        if (hit != null && hit.getType() == HitResult.Type.BLOCK
                && target.equals(hit.getBlockPos())) {
            breakPos = target;
            breakFace = hit.getDirection();
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (breakPos == null || mc.gui.screen() != null) {
            if (target == null)
                restoreSlot();
            return;
        }
        int slot = PlayerUtils.getTool(mc.level.getBlockState(breakPos));
        if (slot != -1 && slot != mc.player.getInventory().getSelectedSlot()) {
            if (previousSlot == -1)
                previousSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(slot);
        }
        if (hypixel())
            ((IMixinMultiPlayerGameMode) mc.gameMode).setBlockHitDelay(0);
        mc.gameMode.continueDestroyBlock(breakPos, breakFace);
        PlayerUtils.swingItem();
    };

    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (target == null)
            return;
        RenderUtils.renderBlock(target, Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor(),
                true, false);
    };

    /** Mining progress under the crosshair while a block is being broken. */
    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        if (breakPos == null || mc.gameMode == null)
            return;
        float progress = Math.min(1f, ((IMixinMultiPlayerGameMode) mc.gameMode).getCurBlockDamageMP());
        if (progress <= 0f)
            return;
        ScaledResolution sr = new ScaledResolution(mc);
        float w = 60f, h = 4f;
        float x = sr.getScaledWidth() / 2f - w / 2f;
        float y = sr.getScaledHeight() / 2f + 14f;
        DrawUtils.drawRoundedRect(x, y, x + w, y + h, h / 2f, 0x90000000);
        DrawUtils.drawRoundedRect(x, y, x + Math.max(h, w * progress), y + h, h / 2f,
                Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor() | 0xFF000000);
    };

    private void clearTarget() {
        if (target != null)
            stopBreaking();
        target = null;
        aimPoint = null;
        breakPos = null;
    }

    private void pickTarget() {
        BlockPos previous = target;
        target = null;
        aimPoint = null;

        BlockPos bed = nearestBed(range.getValue().getInput() + 1);
        if (bed != null && hypixel()) {
            // through walls: no line of sight needed, only reach. A covered bed is opened first by
            // mining the block next to it that is closest to us, then the bed itself.
            BlockPos pick = hypixelPick(bed);
            if (pick != null) {
                Vec3 centre = jitteredCentre(pick);
                if (mc.player.getEyePosition(1f).distanceTo(centre) <= range.getValue().getInput()) {
                    target = pick;
                    aimPoint = centre;
                }
            }
        } else if (bed != null) {
            Plan plan = planLegit(bed);
            if (plan != null) {
                target = plan.first;
                aimPoint = plan.aim;
            }
        }
        // Don't abandon a block part-way through for an equivalent one: the other half of the bed, another
        // covering block, or a momentary loss of the aim point. Switching restarts the mining progress.
        if (previous != null && !previous.equals(target) && keepPrevious(previous, target)) {
            target = previous;
            aimPoint = hypixel() ? jitteredCentre(previous) : visiblePoint(previous);
        }
        if (target != null && !target.equals(previous)) {
            for (int i = 0; i < 3; i++)
                jitter[i] = (Math.random() - 0.5) * 0.6;
        }
        if (previous != null && !previous.equals(target))
            mc.gameMode.stopDestroyBlock();
        if (target == null)
            restoreSlot();
    }

    /** The bed itself if one of its halves touches air (above or beside), otherwise the quickest-to-mine block touching it (one block is the minimum). */
    private BlockPos hypixelPick(BlockPos bed) {
        java.util.List<BlockPos> halves = new java.util.ArrayList<>();
        halves.add(bed);
        for (Direction side : Direction.Plane.HORIZONTAL)
            if (mc.level.getBlockState(bed.relative(side)).getBlock() instanceof BedBlock)
                halves.add(bed.relative(side));

        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        float bestTime = Float.MAX_VALUE;
        for (BlockPos half : halves) {
            for (Direction side : Direction.values()) {
                if (side == Direction.DOWN)
                    continue;
                BlockPos pos = half.relative(side);
                BlockState state = mc.level.getBlockState(pos);
                if (state.getBlock() instanceof BedBlock)
                    continue;
                if (state.isAir())
                    return bed;
                if (state.getDestroySpeed(mc.level, pos) < 0)
                    continue;
                double d = mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                double reachSq = range.getValue().getInput() * range.getValue().getInput();
                if (d > reachSq)
                    continue;
                float time = mineTime(state, pos);
                if (time < bestTime || (time == bestTime && d < nearestDist)) {
                    bestTime = time;
                    nearestDist = d;
                    nearest = pos;
                }
            }
        }
        return nearest;
    }

    /**
     * Whether to stay on the block already being mined rather than the newly picked one (null = nothing picked).
     * Only when the new pick is the same kind of block (bed vs cover) or nothing, and the old one is still
     * there and still reachable (and visible, outside Hypixel mode).
     */
    private boolean keepPrevious(BlockPos previous, BlockPos picked) {
        Block block = mc.level.getBlockState(previous).getBlock();
        if (mc.level.getBlockState(previous).isAir())
            return false;
        if (picked != null) {
            boolean previousBed = block instanceof BedBlock;
            boolean pickedBed = mc.level.getBlockState(picked).getBlock() instanceof BedBlock;
            if (previousBed != pickedBed || previous.distSqr(picked) > 9)
                return false;
        }
        if (hypixel())
            return mc.player.getEyePosition(1f).distanceTo(jitteredCentre(previous)) <= range.getValue().getInput();
        return visiblePoint(previous) != null;
    }

    private static final class Plan {
        final BlockPos first;
        final Vec3 aim;
        final int count;
        final float time;

        Plan(BlockPos first, Vec3 aim, int count, float time) {
            this.first = first;
            this.aim = aim;
            this.count = count;
            this.time = time;
        }
    }

    /** Seconds-ish to mine a block with the best hotbar tool (relative: hardness over tool speed). */
    private float mineTime(BlockState state, BlockPos pos) {
        float hardness = state.getDestroySpeed(mc.level, pos);
        if (hardness <= 0)
            return 0f;
        float best = 1f;
        for (int i = 0; i < 9; i++) {
            net.minecraft.world.item.ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty())
                best = Math.max(best, PlayerUtils.getEfficiency(stack, state));
        }
        return hardness / best;
    }

    /** The block's outline box in world space, or the whole cell when it has no shape. */
    private AABB box(BlockPos pos) {
        VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
        return shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
    }

    /**
     * The route to a bed that breaks the fewest blocks along a line of sight: for sample points on both halves it
     * counts the solid blocks between the eyes and the point (ties go to the least mining time) and returns the
     * first block of the cheapest line with the point to aim at. A visible bed comes back as the bed itself.
     */
    private Plan planLegit(BlockPos bed) {
        java.util.List<BlockPos> halves = new java.util.ArrayList<>();
        halves.add(bed);
        for (Direction side : Direction.Plane.HORIZONTAL)
            if (mc.level.getBlockState(bed.relative(side)).getBlock() instanceof BedBlock)
                halves.add(bed.relative(side));

        Vec3 eyes = mc.player.getEyePosition(1f);
        double reach = range.getValue().getInput();
        Plan best = null;
        for (BlockPos half : halves) {
            AABB box = box(half).deflate(INSET);
            double[][] samples = {{0.5, 0.5, 0.5}, {0.5, 1, 0.5}, {0.5, 0, 0.5}, {0, 0.5, 0.5}, {1, 0.5, 0.5}, {0.5, 0.5, 0}, {0.5, 0.5, 1}};
            for (int s = 0; s < samples.length; s++) {
                Vec3 point = s == 0 ? jitteredCentre(half) : new Vec3(
                        box.minX + (box.maxX - box.minX) * samples[s][0],
                        box.minY + (box.maxY - box.minY) * samples[s][1],
                        box.minZ + (box.maxZ - box.minZ) * samples[s][2]);
                Plan plan = lineCost(eyes, point, half, reach);
                if (plan != null && (best == null || plan.count < best.count
                        || (plan.count == best.count && plan.time < best.time)))
                    best = plan;
            }
        }
        return best;
    }

    /** Solid blocks on the straight line from the eyes to a point on the bed; null if unusable (unbreakable or out of reach). */
    private Plan lineCost(Vec3 eyes, Vec3 point, BlockPos bedHalf, double reach) {
        double length = eyes.distanceTo(point);
        Vec3 dir = point.subtract(eyes).normalize();
        BlockPos eyeBlock = BlockPos.containing(eyes);
        BlockPos last = eyeBlock;
        BlockPos first = null;
        Vec3 aim = null;
        int count = 0;
        float time = 0f;
        for (double t = 0.05; t <= length + 0.05; t += 0.1) {
            Vec3 p = eyes.add(dir.x * t, dir.y * t, dir.z * t);
            BlockPos pos = BlockPos.containing(p);
            if (pos.equals(last))
                continue;
            last = pos;
            BlockState state = mc.level.getBlockState(pos);
            if (state.getBlock() instanceof BedBlock) {
                // reached a bed block (this half or the other one): the line is done
                if (first == null)
                    return t <= reach ? new Plan(pos, point, 0, 0f) : null;
                break;
            }
            if (state.getCollisionShape(mc.level, pos).isEmpty())
                continue;
            if (state.getDestroySpeed(mc.level, pos) < 0)
                return null;
            count++;
            time += mineTime(state, pos);
            if (first == null) {
                if (t > reach)
                    return null;
                first = pos;
                aim = p;
            }
        }
        return first == null ? null : new Plan(first, aim, count, time);
    }

    private boolean hypixel() {
        return breakMode.getValue() == BreakMode.Hypixel;
    }

    /** The nearest bed block within reach (either half), never your own bed (see BedwarsTracker). */
    private BlockPos nearestBed(double radius) {
        BlockPos best = null;
        double bestDist = radius * radius;
        BlockPos origin = mc.player.blockPosition();
        int r = (int) Math.ceil(radius);
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!(mc.level.getBlockState(pos).getBlock() instanceof BedBlock) || BedwarsTracker.isOwnBed(pos))
                        continue;
                    double d = mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (d < bestDist) {
                        bestDist = d;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    /** The middle of the block's box, shifted by the per-target jitter. */
    private Vec3 jitteredCentre(BlockPos pos) {
        AABB box = box(pos).deflate(INSET);
        return new Vec3(
                (box.minX + box.maxX) / 2 + jitter[0] * (box.maxX - box.minX),
                (box.minY + box.maxY) / 2 + jitter[1] * (box.maxY - box.minY),
                (box.minZ + box.maxZ) / 2 + jitter[2] * (box.maxZ - box.minZ));
    }

    /** A point on the block that is in reach and has a clear line from the eyes: the jittered middle, then a 3x3x3 grid. */
    private Vec3 visiblePoint(BlockPos pos) {
        AABB box = box(pos).deflate(INSET);
        java.util.List<Vec3> candidates = new java.util.ArrayList<>();
        candidates.add(jitteredCentre(pos));
        double[] fx = {0.5, 0, 1}, fy = {0.5, 0, 1}, fz = {0.5, 0, 1};
        for (double ax : fx)
            for (double ay : fy)
                for (double az : fz)
                    candidates.add(new Vec3(box.minX + (box.maxX - box.minX) * ax,
                            box.minY + (box.maxY - box.minY) * ay,
                            box.minZ + (box.maxZ - box.minZ) * az));
        Vec3 eyes = mc.player.getEyePosition(1f);
        for (Vec3 point : candidates) {
            if (eyes.distanceTo(point) > range.getValue().getInput())
                continue;
            Vec3 dir = point.subtract(eyes).normalize();
            BlockHitResult hit = PlayerUtils.rayTraceBlocks(eyes,
                    point.add(dir.x * 0.1, dir.y * 0.1, dir.z * 0.1));
            if (hit != null && hit.getType() == HitResult.Type.BLOCK && pos.equals(hit.getBlockPos()))
                return point;
        }
        return null;
    }

    private static float angleBetween(float yawA, float pitchA, float yawB, float pitchB) {
        float dYaw = RotationUtils.getYawDifference(yawA, yawB);
        float dPitch = pitchA - pitchB;
        return (float) Math.sqrt(dYaw * dYaw + dPitch * dPitch);
    }
}

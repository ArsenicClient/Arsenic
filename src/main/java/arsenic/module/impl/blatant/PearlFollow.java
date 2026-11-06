package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.injection.accessor.IMixinMultiPlayerGameMode;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.BadPacketsManager;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Follows an enemy's ender pearl: when someone throws one, it works out where it will land and throws your own pearl
 * at the same spot, aiming silently and switching back to your previous slot afterwards.
 */
@ModuleInfo(name = "PearlFollow", description = "Throws a pearl to where an enemy's pearl is about to land", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class PearlFollow extends Module {

    private static final int MAX_SIM_TICKS = 400;
    private static final double PEARL_SPEED = 1.5;
    private static final double GRAVITY = 0.03;
    private static final float MIN_PITCH = -85f, MAX_PITCH = 85f;
    private static final float COARSE_STEP = 2f;
    private static final float FINE_STEP = 0.2f;
    private static final int FINE_STEPS = 11;
    private static final float MIN_MINIMA_GAP = 6f;
    private static final int MAX_MINIMA = 3;
    private static final double ERROR_SLACK = 0.25;
    /** How close the rotation the server has must be to the solution before the pearl is thrown. */
    private static final float AIM_TOLERANCE = 0.5f;
    /** Give up on a throw if the silent rotation has not settled by then. */
    private static final int AIM_TIMEOUT = 10;

    public final DoubleProperty throwerRange = new DoubleProperty("Thrower Range", new DoubleValue(5, 200, 50, 1));
    public final DoubleProperty minLanding = new DoubleProperty("Min Landing Distance", new DoubleValue(0, 50, 8, 0.5));
    public final DoubleProperty landingRange = new DoubleProperty("Landing Range", new DoubleValue(5, 200, 60, 1));
    public final DoubleProperty maxError = new DoubleProperty("Max Error", new DoubleValue(0.5, 10, 2, 0.1));
    public final RangeProperty delay = new RangeProperty("Delay (ms)", new RangeValue(0, 500, 50, 100, 1));
    public final RangeProperty swapBackDelay = new RangeProperty("Swap Back Delay (ms)", new RangeValue(0, 2000, 100, 300, 1));
    public final BooleanProperty onlyOnGround = new BooleanProperty("Only On Ground", false);
    public final BooleanProperty targetOnly = new BooleanProperty("Target Only", false);

    /** Pearls already looked at, so each one is judged once. */
    private final Set<Integer> seen = new HashSet<>();
    private final Set<Integer> alive = new HashSet<>();

    private Vec3 landing;
    private long throwAt;
    private float[] aim;
    private int aimTicks;

    private int restoreSlot = -1;
    private long restoreAt;
    private int throwTick;

    /** One simulated throw: its pitch, how far it lands from the goal, its flight time and its highest point. */
    private record Shot(float pitch, double error, int ticks, double peak) {}

    @Override
    public boolean isSwappingHotbar() {
        return restoreSlot != -1;
    }

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        if (restoreSlot != -1 && mc.player != null && mc.gameMode != null) {
            mc.player.getInventory().setSelectedSlot(restoreSlot);
            ((IMixinMultiPlayerGameMode) mc.gameMode).syncCurrentPlayItem();
        }
        reset();
    }

    private void reset() {
        seen.clear();
        alive.clear();
        landing = null;
        aim = null;
        aimTicks = 0;
        restoreSlot = -1;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.level == null)
            return;
        long now = System.currentTimeMillis();

        // back to the slot we had, never in the tick the pearl went out
        if (restoreSlot != -1 && now >= restoreAt && mc.player.tickCount > throwTick) {
            if (mc.player.getInventory().getSelectedSlot() != restoreSlot) {
                mc.player.getInventory().setSelectedSlot(restoreSlot);
                ((IMixinMultiPlayerGameMode) mc.gameMode).syncCurrentPlayItem();
            }
            restoreSlot = -1;
        }

        scanPearls(now);

        if (landing != null && aim == null && now >= throwAt)
            prepareThrow();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (aim == null)
            return;
        event.setYaw(mc.player.getYRot() + Mth.wrapDegrees(aim[0] - mc.player.getYRot()));
        event.setPitch(aim[1]);
        event.setSpeed(180f);
        event.setSmoothing(false);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (aim == null)
            return;
        if (++aimTicks > AIM_TIMEOUT || !canThrow()) {
            aim = null;
            return;
        }
        // the server throws along the rotation of the last movement packet, so that one has to be on aim
        if (Math.abs(RotationUtils.getYawDifference(event.getPrevYaw(), aim[0])) > AIM_TOLERANCE
                || Math.abs(event.getPrevPitch() - aim[1]) > AIM_TOLERANCE)
            return;
        if (mc.player.isUsingItem() || BadPacketsManager.bad(false, true, false, true, true))
            return;
        int slot = pearlSlot();
        if (slot == -1) {
            aim = null;
            return;
        }
        throwPearl(slot, event.getPrevYaw(), event.getPrevPitch());
        aim = null;
    };

    private void scanPearls(long now) {
        alive.clear();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ThrownEnderpearl pearl))
                continue;
            int id = pearl.getId();
            alive.add(id);
            if (!seen.add(id) || landing != null || aim != null)
                continue;
            if (!(pearl.getOwner() instanceof Player thrower) || !validThrower(thrower))
                continue;
            Vec3 land = simulate(pearl.position(), pearl.getDeltaMovement(), null);
            if (land == null)
                continue;
            double dist = mc.player.getEyePosition().distanceTo(land);
            if (dist < minLanding.getValue().getInput() || dist > landingRange.getValue().getInput())
                continue;
            landing = land;
            throwAt = now + (long) delay.getValue().getRandomInRange();
        }
        seen.retainAll(alive);
    }

    private boolean validThrower(Player thrower) {
        if (thrower == mc.player || AntiBot.isBot(thrower) || Arsenic.getArsenic().getFriendManager().isFriend(thrower))
            return false;
        if (targetOnly.getValue()) {
            Player target = TargetManager.getTarget();
            return target != null && target.getUUID().equals(thrower.getUUID());
        }
        return mc.player.distanceTo(thrower) <= throwerRange.getValue().getInput();
    }

    private boolean canThrow() {
        return mc.gui.screen() == null && mc.isWindowActive()
                && (!onlyOnGround.getValue() || mc.player.onGround());
    }

    private void prepareThrow() {
        Vec3 goal = landing;
        landing = null;
        if (!canThrow() || pearlSlot() == -1)
            return;
        float[] solved = solve(goal);
        if (solved == null)
            return;
        aim = solved;
        aimTicks = 0;
    }

    private int pearlSlot() {
        return HotbarUse.find(s -> s.is(Items.ENDER_PEARL) && !mc.player.getCooldowns().isOnCooldown(s));
    }

    /** Throws with exactly the rotation the server already has, so the use packet changes nothing. */
    private void throwPearl(int slot, float yaw, float pitch) {
        int previous = mc.player.getInventory().getSelectedSlot();
        if (previous != slot) {
            mc.player.getInventory().setSelectedSlot(slot);
            ((IMixinMultiPlayerGameMode) mc.gameMode).syncCurrentPlayItem();
            if (restoreSlot == -1)
                restoreSlot = previous;
        }
        float realYaw = mc.player.getYRot(), realPitch = mc.player.getXRot();
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        mc.player.setYRot(realYaw);
        mc.player.setXRot(realPitch);
        throwTick = mc.player.tickCount;
        if (restoreSlot != -1)
            restoreAt = System.currentTimeMillis() + (long) swapBackDelay.getValue().getRandomInRange();
    }

    // ---------------------------------------------------------------- aim solver

    /** Yaw straight at the goal and the pitch whose pearl lands closest, preferring the quickest, flattest throw. */
    private float[] solve(Vec3 goal) {
        Vec3 start = new Vec3(mc.player.getX(), mc.player.getEyeY() - 0.1, mc.player.getZ());
        float yaw = (float) Math.toDegrees(Math.atan2(-(goal.x - start.x), goal.z - start.z));
        double tolerance = maxError.getValue().getInput();

        List<Shot> coarse = new ArrayList<>();
        List<Shot> valid = new ArrayList<>();
        for (float pitch = MIN_PITCH; pitch <= MAX_PITCH; pitch += COARSE_STEP) {
            Shot shot = shoot(start, goal, yaw, pitch);
            coarse.add(shot);
            if (shot != null)
                valid.add(shot);
        }
        if (valid.isEmpty())
            return null;

        List<Shot> minima = localMinima(coarse);
        Shot preferred = pick(valid, tolerance);
        for (Shot shot : minima)
            refine(start, goal, yaw, shot, valid);
        if (preferred != null && minima.stream().noneMatch(m -> Math.abs(m.pitch - preferred.pitch) < 0.1f))
            refine(start, goal, yaw, preferred, valid);

        Shot best = pickBest(valid, tolerance);
        return best == null ? null : new float[]{yaw, best.pitch};
    }

    /** Up to three of the best coarse dips, kept apart so they explore different arcs. */
    private static List<Shot> localMinima(List<Shot> coarse) {
        List<Shot> dips = new ArrayList<>();
        for (int i = 0; i < coarse.size(); i++) {
            Shot s = coarse.get(i);
            if (s == null)
                continue;
            double left = i > 0 && coarse.get(i - 1) != null ? coarse.get(i - 1).error : Double.POSITIVE_INFINITY;
            double right = i + 1 < coarse.size() && coarse.get(i + 1) != null ? coarse.get(i + 1).error : Double.POSITIVE_INFINITY;
            if (s.error <= left && s.error <= right)
                dips.add(s);
        }
        List<Shot> chosen = new ArrayList<>();
        while (chosen.size() < MAX_MINIMA) {
            Shot next = null;
            for (Shot s : dips) {
                boolean close = false;
                for (Shot c : chosen)
                    if (Math.abs(c.pitch - s.pitch) < MIN_MINIMA_GAP) {
                        close = true;
                        break;
                    }
                if (!close && (next == null || s.error < next.error))
                    next = s;
            }
            if (next == null)
                break;
            chosen.add(next);
        }
        return chosen;
    }

    private void refine(Vec3 start, Vec3 goal, float yaw, Shot around, List<Shot> out) {
        for (int i = -FINE_STEPS; i <= FINE_STEPS; i++) {
            float pitch = around.pitch + i * FINE_STEP;
            if (pitch < MIN_PITCH || pitch > MAX_PITCH)
                continue;
            Shot shot = shoot(start, goal, yaw, pitch);
            if (shot != null)
                out.add(shot);
        }
    }

    private static Shot pick(List<Shot> shots, double tolerance) {
        Shot best = null;
        for (Shot s : shots)
            if (s.error <= tolerance)
                best = better(best, s);
        return best;
    }

    /** Among the shots within a hair of the most accurate one, the quickest and flattest. */
    private static Shot pickBest(List<Shot> shots, double tolerance) {
        double minError = Double.POSITIVE_INFINITY;
        for (Shot s : shots)
            if (s.error <= tolerance)
                minError = Math.min(minError, s.error);
        if (!Double.isFinite(minError))
            return null;
        double cutoff = Math.min(tolerance, minError + ERROR_SLACK);
        Shot best = null;
        for (Shot s : shots)
            if (s.error <= cutoff)
                best = better(best, s);
        return best;
    }

    private static Shot better(Shot current, Shot candidate) {
        if (current == null)
            return candidate;
        int c = Integer.compare(candidate.ticks, current.ticks);
        if (c != 0)
            return c < 0 ? candidate : current;
        c = Double.compare(candidate.peak, current.peak);
        if (c != 0)
            return c < 0 ? candidate : current;
        c = Double.compare(candidate.error, current.error);
        if (c != 0)
            return c < 0 ? candidate : current;
        return candidate.pitch > current.pitch ? candidate : current;
    }

    private final double[] simResult = new double[2];

    private Shot shoot(Vec3 start, Vec3 goal, float yaw, float pitch) {
        Vec3 velocity = Vec3.directionFromRotation(pitch, yaw).scale(PEARL_SPEED);
        // a thrown projectile inherits the thrower's motion (vertical only while airborne)
        Vec3 motion = mc.player.getDeltaMovement();
        velocity = velocity.add(motion.x, mc.player.onGround() ? 0 : motion.y, motion.z);
        Vec3 land = simulate(start, velocity, simResult);
        if (land == null)
            return null;
        return new Shot(pitch, land.distanceTo(goal), (int) simResult[0], simResult[1]);
    }

    /**
     * Steps a pearl the way ThrowableProjectile does (gravity, drag, then move or hit a block) until it hits something.
     * Returns where it lands, or null if it flies longer than the cap. {@code info} gets the tick count and peak height.
     */
    private Vec3 simulate(Vec3 pos, Vec3 vel, double[] info) {
        double x = pos.x, y = pos.y, z = pos.z;
        double vx = vel.x, vy = vel.y, vz = vel.z;
        double peak = y;
        int minY = mc.level.getMinY();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int tick = 1; tick <= MAX_SIM_TICKS; tick++) {
            boolean water = mc.level.getFluidState(cursor.set(x, y, z)).is(FluidTags.WATER);
            vy -= GRAVITY;
            double drag = water ? 0.8 : 0.99;
            vx *= drag;
            vy *= drag;
            vz *= drag;
            Vec3 from = new Vec3(x, y, z);
            Vec3 to = new Vec3(x + vx, y + vy, z + vz);
            BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) {
                if (info != null) {
                    info[0] = tick;
                    info[1] = peak;
                }
                return hit.getLocation();
            }
            x = to.x;
            y = to.y;
            z = to.z;
            peak = Math.max(peak, y);
            if (y < minY)
                return null;
        }
        return null;
    }
}

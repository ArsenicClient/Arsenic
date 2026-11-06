package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPlayerJoinWorld;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.notifications.Notification;
import arsenic.notifications.NotificationManager;
import arsenic.notifications.NotificationType;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * One-shot mace setup: throws an ender pearl where you look, then a wind charge timed and aimed to hit that pearl in
 * flight, so the burst knocks it along a diagonal. Turns itself off when done.
 */
@ModuleInfo(name = "DiagonalPearlCatch", description = "Throws a pearl and a wind charge that intercepts it mid-air", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class DiagonalPearlCatch extends Module {

    /** How close the sent rotation must be to the wanted one before throwing, in degrees. */
    private static final float AIM_TOLERANCE = 1.5f;
    /** Largest miss the intercept search accepts, in blocks. */
    private static final double MAX_MISS = 0.4;
    private static final int PLAYER_TICKS = 17;
    private static final int PEARL_TICKS = 30;
    private static final int MIN_LAUNCH_TICK = 2;
    private static final int MAX_LAUNCH_TICK = 15;
    private static final double THROW_SPEED = 1.5;

    public enum Mode { Trajectory, Macro }

    private enum Stage { PEARL, WAIT, CHARGE, DONE }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Trajectory);
    public final DoubleProperty windChargeDelay = new DoubleProperty("Wind Charge Delay", new DoubleValue(10, 300, 100, 5));
    public final BooleanProperty silentAim = new BooleanProperty("Silent Aim", true);
    public final DoubleProperty maxYawDelta = new DoubleProperty("Max Yaw Delta", new DoubleValue(10, 180, 180, 1));

    private final HotbarUse swap = new HotbarUse();
    private Stage stage = Stage.PEARL;
    private Level level;
    private boolean aiming;
    private float yaw, pitch;
    private long pearlTime;
    private int pearlTick;

    // intercept solution: ticks after the pearl to throw the charge, and the charge rotation
    private boolean solved;
    private int launchTick;
    private float solYaw, solPitch;

    // scratch arrays for the simulation, reused between enables
    private final double[] px = new double[PLAYER_TICKS + 1], py = new double[PLAYER_TICKS + 1], pz = new double[PLAYER_TICKS + 1];
    private final double[] pvx = new double[PLAYER_TICKS + 1], pvy = new double[PLAYER_TICKS + 1], pvz = new double[PLAYER_TICKS + 1];
    private final boolean[] pGround = new boolean[PLAYER_TICKS + 1];
    private final double[] ex = new double[PEARL_TICKS + 1], ey = new double[PEARL_TICKS + 1], ez = new double[PEARL_TICKS + 1];
    private int pearlSize;
    private double bestMiss;
    private int bestTick;
    private float bestYaw, bestPitch;

    public DiagonalPearlCatch() {
        windChargeDelay.setVisible(() -> mode.getValue() == Mode.Macro);
        silentAim.setVisible(() -> mode.getValue() == Mode.Trajectory);
        maxYawDelta.setVisible(() -> mode.getValue() == Mode.Trajectory && silentAim.getValue());
    }

    @Override
    public boolean isSwappingHotbar() {
        return swap.active();
    }

    @Override
    protected void onEnable() {
        stage = Stage.PEARL;
        aiming = false;
        solved = false;
        level = mc.level;
        if (mc.player == null)
            return;
        if (pearlSlot() == -1) {
            stop("no ender pearl in hotbar");
        } else if (chargeSlot() == -1) {
            stop("no wind charge in hotbar");
        }
    }

    @Override
    protected void onDisable() {
        swap.release();
        aiming = false;
        solved = false;
        stage = Stage.PEARL;
        level = null;
    }

    @EventLink
    public final Listener<EventPlayerJoinWorld> onJoin = event -> {
        if (isEnabled())
            setEnabled(false);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        swap.tick();
        if (mc.level != level) {
            setEnabled(false);
            return;
        }
        if (stage == Stage.DONE) {
            setEnabled(false);
        } else if (stage == Stage.WAIT) {
            boolean ready = mode.getValue() == Mode.Macro
                    ? System.currentTimeMillis() - pearlTime >= windChargeDelay.getValue().getInput()
                    // tickCount only goes up later in this tick, so a launch tick of N is N - 1 here
                    : mc.player.tickCount - pearlTick >= launchTick - 1;
            if (ready)
                stage = Stage.CHARGE;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        aiming = false;
        if ((stage != Stage.PEARL && stage != Stage.CHARGE) || mc.gui.screen() != null || busy())
            return;
        yaw = mc.player.getYRot();
        pitch = mc.player.getXRot();
        if (stage == Stage.CHARGE && solved && mode.getValue() == Mode.Trajectory) {
            float delta = Mth.wrapDegrees(solYaw - yaw);
            if (!silentAim.getValue() || Math.abs(delta) <= maxYawDelta.getValue().getInput()) {
                yaw = yaw + delta;
                pitch = solPitch;
            }
        }
        aiming = true;
        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setSpeed(180f);
        event.setPreventDuplicateLook(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (!aiming)
            return;
        aiming = false;
        if (Math.abs(event.getPitch() - pitch) > AIM_TOLERANCE
                || Math.abs(Mth.wrapDegrees(event.getYaw() - yaw)) > AIM_TOLERANCE)
            return;
        if (stage == Stage.PEARL) {
            int slot = pearlSlot();
            if (slot == -1) {
                stop("no ender pearl in hotbar");
                return;
            }
            if (mode.getValue() == Mode.Trajectory && !solve(event.getYaw(), event.getPitch())) {
                stop("no intercept for this angle");
                return;
            }
            if (throwFrom(slot, event.getYaw(), event.getPitch())) {
                pearlTime = System.currentTimeMillis();
                pearlTick = mc.player.tickCount;
                stage = Stage.WAIT;
            }
        } else if (stage == Stage.CHARGE) {
            int slot = chargeSlot();
            if (slot == -1) {
                stop("no wind charge in hotbar");
                return;
            }
            if (!silentAim.getValue() && mode.getValue() == Mode.Trajectory) {
                // without silent aim the camera turns to the intercept angle
                mc.player.setYRot(event.getYaw());
                mc.player.setXRot(event.getPitch());
            }
            if (throwFrom(slot, event.getYaw(), event.getPitch()))
                stage = Stage.DONE;
        }
    };

    /** Uses the item in the slot with the rotation the server gets this tick, so the use packet matches it. */
    private boolean throwFrom(int slot, float sentYaw, float sentPitch) {
        float realYaw = mc.player.getYRot(), realPitch = mc.player.getXRot();
        swap.switchOnce(slot);
        mc.player.setYRot(sentYaw);
        mc.player.setXRot(sentPitch);
        boolean accepted;
        try {
            ItemStack stack = mc.player.getMainHandItem();
            InteractionResult result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            accepted = result.consumesAction();
            if (result instanceof InteractionResult.Success success
                    && success.swingSource() == InteractionResult.SwingSource.PREDICTED)
                mc.player.swing(InteractionHand.MAIN_HAND, stack.getInteractAnimation(), false);
        } finally {
            mc.player.setYRot(realYaw);
            mc.player.setXRot(realPitch);
        }
        return accepted;
    }

    // ---- intercept search ----

    /**
     * Simulates our own motion and a pearl thrown at the given rotation, then searches the tick and angle at which a
     * wind charge passes closest to the pearl. Sets {@link #solved}, {@link #launchTick}, {@link #solYaw}, {@link #solPitch}.
     */
    private boolean solve(float pearlYaw, float pearlPitch) {
        solved = false;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 vel = mc.player.getDeltaMovement();
        simulatePlayer(eye, vel, mc.player.onGround());
        simulatePearl(pearlYaw, pearlPitch);

        bestMiss = Double.MAX_VALUE;
        bestTick = -1;
        for (int tick = MIN_LAUNCH_TICK; tick <= MAX_LAUNCH_TICK && tick < pearlSize - 1 && tick <= PLAYER_TICKS; tick++) {
            double ivx = pvx[tick], ivy = pGround[tick] ? 0 : pvy[tick], ivz = pvz[tick];
            for (int j = tick + 1; j < pearlSize; j++) {
                double dx = ex[j] - px[tick], dy = ey[j] - py[tick], dz = ez[j] - pz[tick];
                if (dx * dx + dy * dy + dz * dz < 1.0E-6)
                    continue;
                int n = j - tick;
                // aim for the target with our own inherited motion taken out
                double ax = dx - ivx * n, ay = dy - ivy * n, az = dz - ivz * n;
                float baseYaw = (float) Math.toDegrees(Math.atan2(-ax, az));
                float basePitch = (float) Math.toDegrees(-Math.atan2(ay, Math.sqrt(ax * ax + az * az)));
                for (float p = basePitch - 5f; p <= basePitch + 5f; p += 0.5f)
                    for (float y = baseYaw - 5f; y <= baseYaw + 5f; y += 0.5f)
                        tryCharge(tick, y, p, n + 4);
            }
        }
        if (bestTick == -1)
            return false;

        // finer pass around the best coarse hit
        int tick = bestTick;
        float coarseYaw = bestYaw, coarsePitch = bestPitch;
        int steps = pearlSize - tick;
        if (steps > 1) {
            for (float p = coarsePitch - 0.5f; p <= coarsePitch + 0.5f; p += 0.05f)
                for (float y = coarseYaw - 1f; y <= coarseYaw + 1f; y += 0.1f)
                    tryCharge(tick, y, p, steps);
        }
        if (bestMiss > MAX_MISS)
            return false;
        launchTick = bestTick;
        solYaw = bestYaw;
        solPitch = Mth.clamp(bestPitch, -90f, 90f);
        solved = true;
        return true;
    }

    /** Flies a wind charge from where we stand at the tick and keeps it if it passes the pearl closer than the best so far. */
    private void tryCharge(int tick, float yaw, float pitch, int steps) {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double dx = -Math.sin(yr) * Math.cos(pr), dy = -Math.sin(pr), dz = Math.cos(yr) * Math.cos(pr);
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-9) {
            dx = 0;
            dy = 0;
            dz = 1;
        } else {
            dx /= len;
            dy /= len;
            dz /= len;
        }
        double vx = dx * THROW_SPEED + pvx[tick];
        double vy = dy * THROW_SPEED + (pGround[tick] ? 0 : pvy[tick]);
        double vz = dz * THROW_SPEED + pvz[tick];
        double x = px[tick], y = py[tick], z = pz[tick];
        double best = Double.MAX_VALUE;
        for (int k = 1; k <= steps; k++) {
            int idx = tick + k;
            if (idx >= pearlSize)
                break;
            x += vx;
            y += vy;
            z += vz;
            vx *= 0.99;
            vy *= 0.99;
            vz *= 0.99;
            double ox = x - ex[idx], oy = y - ey[idx], oz = z - ez[idx];
            double d = ox * ox + oy * oy + oz * oz;
            if (d < best)
                best = d;
        }
        if (best == Double.MAX_VALUE)
            return;
        double miss = Math.sqrt(best);
        if (miss < bestMiss) {
            bestMiss = miss;
            bestTick = tick;
            bestYaw = yaw;
            bestPitch = pitch;
        }
    }

    /** Our eye position and motion for the next ticks under plain air physics. */
    private void simulatePlayer(Vec3 eye, Vec3 vel, boolean onGround) {
        double x = eye.x, y = eye.y, z = eye.z, vx = vel.x, vy = vel.y, vz = vel.z;
        for (int i = 0; i <= PLAYER_TICKS; i++) {
            px[i] = x;
            py[i] = y;
            pz[i] = z;
            pvx[i] = vx;
            pvy[i] = vy;
            pvz[i] = vz;
            pGround[i] = i == 0 && onGround;
            x += vx;
            y += vy;
            z += vz;
            vy = (vy - 0.08) * 0.98;
            vx *= 0.91;
            vz *= 0.91;
        }
    }

    /** The pearl's path from our eyes, thrown at the rotation with our own motion added. */
    private void simulatePearl(float yaw, float pitch) {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double vx = -Math.sin(yr) * Math.cos(pr) * THROW_SPEED + pvx[0];
        double vy = -Math.sin(pr) * THROW_SPEED + (pGround[0] ? 0 : pvy[0]);
        double vz = Math.cos(yr) * Math.cos(pr) * THROW_SPEED + pvz[0];
        double x = px[0], y = py[0], z = pz[0];
        ex[0] = x;
        ey[0] = y;
        ez[0] = z;
        pearlSize = 1;
        for (int i = 1; i <= PEARL_TICKS; i++) {
            x += vx;
            y += vy;
            z += vz;
            vx *= 0.99;
            vy = vy * 0.99 - 0.03;
            vz *= 0.99;
            ex[i] = x;
            ey[i] = y;
            ez[i] = z;
            pearlSize = i + 1;
            if (y < py[0] - 256)
                break;
        }
    }

    // ---- helpers ----

    /** Holding right click or using an item: wait so we never swap mid-use. */
    private boolean busy() {
        return mc.player.isUsingItem() || mc.options.keyUse.isDown();
    }

    private int pearlSlot() {
        return HotbarUse.find(s -> s.is(Items.ENDER_PEARL) && !mc.player.getCooldowns().isOnCooldown(s));
    }

    private int chargeSlot() {
        return HotbarUse.find(s -> s.is(Items.WIND_CHARGE) && !mc.player.getCooldowns().isOnCooldown(s));
    }

    private void stop(String reason) {
        NotificationManager.show(new Notification(NotificationType.INFO, getName(), "Stopped: " + reason, 1));
        setEnabled(false);
    }
}

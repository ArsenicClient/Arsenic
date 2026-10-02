package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.impl.ghost.Hitflick;
import arsenic.injection.accessor.IMixinEntity;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.minecraft.ServerInfo;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.AimController;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.lag.LagManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

@ModuleInfo(name = "KillAura", category = ModuleCategory.COMBAT)
public class KillAura extends Module {

    public RangeProperty speed = new RangeProperty("speed", new RangeValue(1, 360, 20, 50,1));
    public RangeProperty returnSpeed = new RangeProperty("Return Speed", new RangeValue(1, 90, 5, 15, 1));
    public RangeProperty aps = new RangeProperty("APS", new RangeValue(1, 20, 8, 12, 1));
    public final EnumProperty<AimController.RotationMode> rotationMode = new EnumProperty<>("Rotations", AimController.RotationMode.Instant);
    /** Ticks of target movement to lead the aim by. */
    public final DoubleProperty prediction = new DoubleProperty("Prediction", new DoubleValue(0, 5, 1, 0.1));
    /** How long before/after the crosshair is on the target the aura keeps clicking. */
    public final DoubleProperty clickGrace = new DoubleProperty("Click Grace", new DoubleValue(0, 500, 200, 10));
    /**
     * Extra distance past attack range to start aiming from - only when there is a single target,
     * so the aura never pre-aims at one player while another is the real threat.
     */
    public final DoubleProperty preAim = new DoubleProperty("Pre-Aim Range", new DoubleValue(0, 5, 1, 0.1));
    /** Off: the aura's rotation is applied to the real camera too, so what you see is what's sent. */
    public final BooleanProperty silentRotations = new BooleanProperty("Silent Rotations", true);
    public Player target = null;
    private boolean hadTarget = false;
    private boolean wasUsingItem;
    private final MSTimer attackTimer = new MSTimer();
    /** Time since the committed rotation last actually pointed at {@link #target}. */
    private final MSTimer onTargetTimer = new MSTimer();
    private boolean everOnTarget = false;

    /**
     * The delay the current attack cycle is actually waiting on. {@link #getAttackDelay()} rolls a
     * fresh random value on every call, so Lazy has to budget against the same number the attack
     * check will use rather than re-rolling and getting a different answer each tick.
     */
    private long currentAttackDelay = 100L;

    private static final double ATTACK_RANGE = 3.0;

    /** Aim point and turn shaping, shared with AimAssist. */
    private final AimController aim = new AimController();


    /**
     * How targets are being chosen - the setting that actually changes how the aura behaves.
     * <p>
     * Deliberately constant: this used to swap to the current target's name whenever one was locked
     * on, so the suffix changed on every target switch and every time a fight started or ended.
     * A label that only ever reflects a setting stays put, which is the whole point of it.
     */
    @Override
    public String getHudInfo() {
        return TargetManager.sortMode.getValue().name().toLowerCase();
    }

    @Override
    protected void onEnable() {
        target = null;
        hadTarget = false;
        everOnTarget = false;
        aim.reset();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        target = pickTarget();
        aim.updateDrift();
        if (target != null && hitflick().ownsRotation()) {
            // Hitflick is turning away to throw the knockback; aiming resumes once it's done.
            hadTarget = true;
            return;
        }
        if (target != null) {
            float[] rots = aim.getPredictedRotations(target, (float) prediction.getValue().getInput());
            aim.rotate(event, target, rots, rotationMode.getValue(),
                    (float) speed.getValue().getMin(), (float) speed.getValue().getMax(), flickBudget());
            hadTarget = true;
        } else if (hadTarget) {
            aim.cancelFlick();
            // Target lost/killed: rotate back to player yaw at a slower speed
            event.setSpeed((float) returnSpeed.getValue().getRandomInRange());
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> eventTickListener = event -> {
        // Reset hadTarget once rotation has fully returned to player yaw
        if (target == null && hadTarget && !event.isModified()) {
            hadTarget = false;
        }
        // Non-silent: put the committed rotation on the real camera. prevRotationYaw/Pitch were
        // already rolled over this tick, so the frame interpolation renders it as a smooth turn.
        // Next tick the manager starts from a player yaw equal to its own, so once there's no
        // target there's nothing to rotate back from.
        Hitflick hitflick = hitflick();
        boolean flickInProgress = hitflick.ownsRotation();
        if (!silentRotations.getValue() && target != null && !flickInProgress) {
            mc.player.rotationYaw = event.getYaw();
            mc.player.rotationPitch = event.getPitch();
        }
        boolean usingItem = mc.player.isUsingItem();
        MovingObjectPosition raytrace = event.getRayTraceEntity();
        Entity hit = raytrace != null ? raytrace.entityHit : null;
        // Reach is measured where the look ray actually enters the hitbox - that's what the server
        // checks - not at the box's nearest point. Aiming anywhere but the nearest point (drift,
        // prediction, mid-turn) puts the entry point further away, so a nearest-point check can
        // pass at 2.9 while the real hit lands past 3. Past reach, vanilla wouldn't have the entity
        // under the crosshair at all, so treat it as nothing there.
        if (hit != null && mc.player.getEyePosition(1f).distanceTo(raytrace.hitVec) > ATTACK_RANGE)
            hit = null;
        if (target != null && hit == target) {
            onTargetTimer.reset();
            everOnTarget = true;
        }
        // The flick's own hit landing this tick counts as ours, so the next one waits a full cycle
        // instead of stacking a second attack on the same tick.
        if (hitflick.attackedThisTick())
            resetAttackCycle();
        if (target != null
                && !flickInProgress
                && attackTimer.getTime() >= currentAttackDelay
                && mc.gui.screen() == null
                && !usingItem
                && !wasUsingItem) {
            if (hit == target) {
                // Hand the hit to Hitflick when it takes it: it flicks next tick and throws this
                // attack itself the tick after. Void mode declines when no angle empties into the
                // void, and the hit goes through normally then.
                if (hitflick.isEnabled() && hitflick.shouldFlick() && hitflick.armFlick(hit, event.getYaw())) {
                    resetAttackCycle();
                } else {
                    mc.player.swingItem();
                    mc.gameMode.attackEntity(mc.player, hit);
                    resetAttackCycle();
                }
            } else if (hit == null
                    && RotationUtils.getDistanceToEntityBox(target) <= ATTACK_RANGE
                    && shouldMissClick(event)) {
                // Crosshair just slipped off, or is about to land: keep clicking like a player
                // would. Nothing is under the crosshair, so this is a plain miss-swing - attacking
                // an entity the ray doesn't touch is exactly what hitbox/raytrace checks catch.
                mc.player.swingItem();
                resetAttackCycle();
            }
            // A different entity under the crosshair (teammate, bot, armour stand) gets neither a
            // hit nor a swing - a player wouldn't click on it either.
        }
        wasUsingItem = usingItem;
    };


    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onServerMove = event -> {
        if (event.getPacket() instanceof S08PacketPlayerPosLook)
            setEnabled(false);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        if(target == null)
            return;
        int col = Arsenic.getInstance().getThemeManager().getCurrentTheme().getMainColor();
        RenderUtils.drawCircle(target, event.partialTicks, 0.7, col, 255);
    };

    /**
     * The best target to aim at, if any is close enough. Aiming normally starts at attack range;
     * with exactly one candidate it starts {@link #preAim} further out, so the turn is already done
     * by the time they walk into reach instead of snapping the moment they do.
     */
    private Hitflick hitflick() {
        return Arsenic.getArsenic().getModuleManager().getModuleByClass(Hitflick.class);
    }

    private Player pickTarget() {
        List<Player> candidates = TargetManager.getTargets();
        double aimRange = ATTACK_RANGE + (candidates.size() == 1 ? preAim.getValue().getInput() : 0);
        for (Player candidate : candidates) {
            if (RotationUtils.getDistanceToEntityBox(candidate) <= aimRange)
                return candidate;
        }
        return null;
    }

    /**
     * Lazy's flick deadline: the wait until the next swing plus the one-way trip for it to reach
     * the server, so the flick lands right as the hit does.
     */
    private float flickBudget() {
        long remainingMs = Math.max(0L, currentAttackDelay - attackTimer.getTime());
        return remainingMs / 50f + LagManager.getPingAsTicks() / 2f;
    }

    private void resetAttackCycle() {
        // Attacks can only fire on a tick, so the timer always passes the delay by up to a tick
        // before one goes out. Carry that overrun into the next cycle - otherwise every delay
        // rounds up to the next tick and real APS lands well under the setting (which is what the
        // old "+ 6" on the APS was papering over). An overrun of a tick or more means we were idle
        // rather than rounding, and isn't carried, so a pause never banks a burst.
        long now = System.currentTimeMillis();
        long overrun = now - (attackTimer.lastMS + currentAttackDelay);
        attackTimer.setTime(now - (overrun >= 0 && overrun < 50 ? overrun : 0));
        currentAttackDelay = getAttackDelay(); // roll the next cycle once, here
    }

    /**
     * Whether the aura should keep clicking while the crosshair is off the target: either it was on
     * the target within the grace window, or it will be within the grace window - because the
     * target is walking into the current aim line, or because the rotation is turning onto it fast
     * enough to get there in time.
     */
    private boolean shouldMissClick(EventSilentRotation.Post event) {
        double graceMs = clickGrace.getValue().getInput();
        if (graceMs <= 0)
            return false;
        if (everOnTarget && onTargetTimer.getTime() <= graceMs)
            return true;

        int lookahead = Math.max(1, (int) Math.round(graceMs / 50.0));
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 look = ((IMixinEntity) mc.player).invokeGetVectorForRotation(event.getPitch(), event.getYaw());
        Vec3 end = eyes.add(look.x * ATTACK_RANGE, look.y * ATTACK_RANGE, look.z * ATTACK_RANGE);
        float border = target.getPickRadius();
        for (int t = 1; t <= lookahead; t++) {
            AABB box = aim.predictBox(target, t).expand(border, border, border);
            if (box.isVecInside(eyes) || box.calculateIntercept(eyes, end) != null)
                return true;
        }

        // Turning onto the target: will the rotation arrive within the window at its current speed?
        float speed = Math.max(event.getSpeed(), 0.01f);
        float[] rots = aim.getPredictedRotations(target, lookahead);
        float yawDelta = Math.abs(RotationUtils.getYawDifference(rots[0], event.getYaw()));
        float pitchDelta = Math.abs(rots[1] - event.getPitch());
        return Math.max(yawDelta, pitchDelta) / speed <= lookahead;
    }

    private long getAttackDelay() {
        return (long) (1000.0 / aps.getValue().getRandomInRange());
    }
}
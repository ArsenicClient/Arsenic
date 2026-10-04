package arsenic.module.impl.blatant;

import arsenic.module.property.impl.SliderScale;
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
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.minecraft.ServerInfo;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.aimcore.TargetPicker;
import arsenic.utils.rotations.AimController;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.lag.LagManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "KillAura", category = ModuleCategory.COMBAT)
public class KillAura extends Module {

    public RangeProperty speed = new RangeProperty("speed", new RangeValue(1, 360, 20, 50,1), SliderScale.LOG);
    public RangeProperty returnSpeed = new RangeProperty("Return Speed", new RangeValue(1, 90, 5, 15, 1), SliderScale.LOG);
    public RangeProperty aps = new RangeProperty("APS", new RangeValue(1, 20, 8, 12, 1));
    /** Off: the aura's rotation is applied to the real camera too, so what you see is what's sent. */
    public final BooleanProperty silentRotations = new BooleanProperty("Silent Rotations", true);
    public EntityPlayer target = null;
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

    // Fixed tuning - these used to be settings.
    /** Rotations always use the human-shaped Lazy turn. */
    private static final AimController.RotationMode ROTATION_MODE = AimController.RotationMode.Lazy;
    /**
     * Ticks of target movement to lead the aim by. 3 led so far that the crosshair sat ahead of the
     * hitbox: 1 tick stays on target far more often, with fewer missed swings.
     */
    private static final float PREDICTION_TICKS = 1f;
    /** How long before/after the crosshair is on the target the aura keeps clicking. */
    private static final double CLICK_GRACE_MS = 300;
    /**
     * Extra distance past attack range to start aiming from - only when there is a single target,
     * so the aura never pre-aims at one player while another is the real threat.
     */
    private static final double PRE_AIM_RANGE = 4;

    /** Aim point and turn shaping, shared with AimAssist. */
    private final AimController aim = new AimController();
    /**
     * Which candidate to aim at. Taking the first in sort order every tick flipped targets whenever
     * one stepped across the range edge or the order shuffled, each flip a fresh flick; this keeps
     * the current one unless another can clearly be hit sooner.
     */
    private final TargetPicker picker = new TargetPicker();


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
            float[] rots = aim.aimAt(target, PREDICTION_TICKS);
            aim.rotate(event, target, rots, ROTATION_MODE,
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
            mc.thePlayer.rotationYaw = event.getYaw();
            mc.thePlayer.rotationPitch = event.getPitch();
        }
        boolean usingItem = mc.thePlayer.isUsingItem();
        MovingObjectPosition raytrace = event.getRayTraceEntity();
        Entity hit = raytrace != null ? raytrace.entityHit : null;
        // Reach is measured where the look ray actually enters the hitbox - that's what the server
        // checks - not at the box's nearest point. Aiming anywhere but the nearest point (drift,
        // prediction, mid-turn) puts the entry point further away, so a nearest-point check can
        // pass at 2.9 while the real hit lands past 3. Past reach, vanilla wouldn't have the entity
        // under the crosshair at all, so treat it as nothing there.
        if (hit != null && mc.thePlayer.getPositionEyes(1f).distanceTo(raytrace.hitVec) > ATTACK_RANGE)
            hit = null;
        if (target != null && hit == target) {
            onTargetTimer.reset();
            everOnTarget = true;
        }
        // The flick's own hit landing this tick counts as ours, so the next one waits a full cycle
        // instead of stacking a second attack on the same tick.
        if (hitflick.attackedThisTick())
            resetAttackCycle();
        // Only the turn is lazy, never the clicking: any valid (non-friend) player under the
        // crosshair is hit as soon as the APS allows, whether or not it's the one being aimed at.
        EntityPlayer hitPlayer = hit instanceof EntityPlayer && TargetManager.isValidTarget((EntityPlayer) hit)
                ? (EntityPlayer) hit : null;
        if ((target != null || hitPlayer != null)
                && !Arsenic.getArsenic().getServerInfo().isInGuiServerSide()
                && !flickInProgress
                && attackTimer.getTime() >= currentAttackDelay
                && !usingItem
                && !wasUsingItem) {
            if (hitPlayer != null) {
                // Hand the hit to Hitflick when it takes it: it flicks next tick and throws this
                // attack itself the tick after. Void mode declines when no angle empties into the
                // void, and the hit goes through normally then.
                if (hitflick.isEnabled() && hitflick.shouldFlick() && hitflick.armFlick(hit, event.getYaw())) {
                    resetAttackCycle();
                } else {
                    mc.thePlayer.swingItem();
                    mc.playerController.attackEntity(mc.thePlayer, hit);
                    resetAttackCycle();
                }
            } else if (hit == null && target != null
                    && RotationUtils.getDistanceToEntityBox(target) <= ATTACK_RANGE
                    && shouldMissClick(event)) {
                // Crosshair just slipped off, or is about to land: keep clicking like a player
                // would. Nothing is under the crosshair, so this is a plain miss-swing - attacking
                // an entity the ray doesn't touch is exactly what hitbox/raytrace checks catch.
                mc.thePlayer.swingItem();
                resetAttackCycle();
            }
            // Anything else under the crosshair (a friend, a filtered-out player, an armour stand)
            // gets neither a hit nor a swing.
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
     * with exactly one candidate it starts {@link #PRE_AIM_RANGE} further out, so the turn is already done
     * by the time they walk into reach instead of snapping the moment they do.
     */
    private Hitflick hitflick() {
        return Arsenic.getArsenic().getModuleManager().getModuleByClass(Hitflick.class);
    }

    private EntityPlayer pickTarget() {
        // The server thinks we're in an inventory (or just sent chat): no aiming, no hitting. With no
        // target the rotation eases back to the camera and the attack check never runs.
        if (Arsenic.getArsenic().getServerInfo().isInGuiServerSide())
            return null;
        List<EntityPlayer> candidates = TargetManager.getTargets();
        double aimRange = ATTACK_RANGE + (candidates.size() == 1 ? PRE_AIM_RANGE : 0);
        // The sort mode's value in ticks-until-hittable terms: SmartSwitch's already is ticks, a
        // degree off the crosshair is worth 1/25 of a tick, a point of health a tick.
        picker.valueScale = TargetManager.sortMode.getValue() == TargetManager.SortMode.Fov ? 0.04f : 1f;
        SilentRotationManager srm = Arsenic.getArsenic().getSilentRotationManager();
        Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
        List<TargetPicker.Candidate> list = new ArrayList<>(candidates.size());
        for (EntityPlayer p : candidates) {
            AxisAlignedBB box = p.getEntityBoundingBox();
            double dx = (box.minX + box.maxX) / 2 - eyes.xCoord, dz = (box.minZ + box.maxZ) / 2 - eyes.zCoord;
            double dy = MathHelper.clamp_double(eyes.yCoord, box.minY, box.maxY) - eyes.yCoord;
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            float angle = Math.max(Math.abs(MathHelper.wrapAngleTo180_float(yaw - srm.yaw)), Math.abs(pitch - srm.pitch));
            list.add(new TargetPicker.Candidate(p.getEntityId(), TargetManager.sortValue(p),
                    RotationUtils.getDistanceToEntityBox(p), angle));
        }
        int i = picker.pick(list, target == null ? Integer.MIN_VALUE : target.getEntityId(), aimRange);
        return i < 0 ? null : candidates.get(i);
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
        double graceMs = CLICK_GRACE_MS;
        if (graceMs <= 0)
            return false;
        if (everOnTarget && onTargetTimer.getTime() <= graceMs)
            return true;

        int lookahead = Math.max(1, (int) Math.round(graceMs / 50.0));
        Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
        Vec3 look = ((IMixinEntity) mc.thePlayer).invokeGetVectorForRotation(event.getPitch(), event.getYaw());
        Vec3 end = eyes.addVector(look.xCoord * ATTACK_RANGE, look.yCoord * ATTACK_RANGE, look.zCoord * ATTACK_RANGE);
        float border = target.getCollisionBorderSize();
        for (int t = 1; t <= lookahead; t++) {
            AxisAlignedBB box = aim.predictBox(target, t).expand(border, border, border);
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
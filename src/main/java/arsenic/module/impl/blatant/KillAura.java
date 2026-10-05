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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "KillAura", category = ModuleCategory.COMBAT)
public class KillAura extends Module {

    public RangeProperty speed = new RangeProperty("speed", new RangeValue(1, 360, 20, 50,1), SliderScale.LOG);
    public RangeProperty returnSpeed = new RangeProperty("Return Speed", new RangeValue(1, 90, 5, 15, 1), SliderScale.LOG);
    public RangeProperty aps = new RangeProperty("APS", new RangeValue(1, 20, 8, 12, 1));
    public final BooleanProperty silentRotations = new BooleanProperty("Silent Rotations", true);
    public final BooleanProperty disableOnFlag = new BooleanProperty("Disable On Flag", true);
    public Player target = null;
    private boolean hadTarget = false;
    private boolean wasUsingItem;
    private final MSTimer attackTimer = new MSTimer();
    private final MSTimer onTargetTimer = new MSTimer();
    private boolean everOnTarget = false;

    private long currentAttackDelay = 100L;

    private static final double ATTACK_RANGE = 3.0;

    private static final AimController.RotationMode ROTATION_MODE = AimController.RotationMode.Lazy;
    private static final float PREDICTION_TICKS = 1f;
    private static final double CLICK_GRACE_MS = 300;
    private static final double PRE_AIM_RANGE = 4;

    private final AimController aim = new AimController();
    private final TargetPicker picker = new TargetPicker();


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
            event.setSpeed((float) returnSpeed.getValue().getRandomInRange());
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> eventTickListener = event -> {
        if (target == null && hadTarget && !event.isModified()) {
            hadTarget = false;
        }
        Hitflick hitflick = hitflick();
        boolean flickInProgress = hitflick.ownsRotation();
        if (!silentRotations.getValue() && target != null && !flickInProgress) {
            mc.player.setYRot(event.getYaw());
            mc.player.setXRot(event.getPitch());
        }
        boolean usingItem = mc.player.isUsingItem();
        HitResult raytrace = event.getRayTraceEntity();
        Entity hit = raytrace instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
        if (hit != null && mc.player.getEyePosition(1f).distanceTo(raytrace.getLocation()) > ATTACK_RANGE)
            hit = null;
        if (target != null && hit == target) {
            onTargetTimer.reset();
            everOnTarget = true;
        }
        if (hitflick.attackedThisTick())
            resetAttackCycle();
        Player hitPlayer = hit instanceof Player && TargetManager.isValidTarget((Player) hit)
                ? (Player) hit : null;
        if ((target != null || hitPlayer != null)
                && !Arsenic.getArsenic().getServerInfo().isInGuiServerSide()
                && !flickInProgress
                && attackTimer.getTime() >= currentAttackDelay
                && !usingItem
                && !wasUsingItem) {
            if (hitPlayer != null) {
                if (hitflick.isEnabled() && hitflick.shouldFlick() && hitflick.armFlick(hit, event.getYaw())) {
                    resetAttackCycle();
                } else {
                    PlayerUtils.swingItem();
                    mc.gameMode.attack(mc.player, hit);
                    resetAttackCycle();
                }
            } else if (hit == null && target != null
                    && RotationUtils.getDistanceToEntityBox(target) <= ATTACK_RANGE
                    && shouldMissClick(event)) {
                PlayerUtils.swingItem();
                resetAttackCycle();
            }
        }
        wasUsingItem = usingItem;
    };


    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onServerMove = event -> {
        if (disableOnFlag.getValue() && event.getPacket() instanceof ClientboundPlayerPositionPacket)
            setEnabled(false);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        if(target == null)
            return;
        int col = Arsenic.getInstance().getThemeManager().getCurrentTheme().getMainColor();
        RenderUtils.drawCircle(target, 0.7, col, 1f);
    };

    private Hitflick hitflick() {
        return Arsenic.getArsenic().getModuleManager().getModuleByClass(Hitflick.class);
    }

    private Player pickTarget() {
        if (Arsenic.getArsenic().getServerInfo().isInGuiServerSide())
            return null;
        List<Player> candidates = TargetManager.getTargets();
        double aimRange = ATTACK_RANGE + (candidates.size() == 1 ? PRE_AIM_RANGE : 0);
        picker.valueScale = TargetManager.sortMode.getValue() == TargetManager.SortMode.Fov ? 0.04f : 1f;
        SilentRotationManager srm = Arsenic.getArsenic().getSilentRotationManager();
        Vec3 eyes = mc.player.getEyePosition(1f);
        List<TargetPicker.Candidate> list = new ArrayList<>(candidates.size());
        for (Player p : candidates) {
            AABB box = p.getBoundingBox();
            double dx = (box.minX + box.maxX) / 2 - eyes.x, dz = (box.minZ + box.maxZ) / 2 - eyes.z;
            double dy = Mth.clamp(eyes.y, box.minY, box.maxY) - eyes.y;
            float yaw = RotationUtils.yawTo(dx, dz);
            float pitch = RotationUtils.pitchTo(dx, dy, dz);
            float angle = Math.max(Math.abs(Mth.wrapDegrees(yaw - srm.yaw)), Math.abs(pitch - srm.pitch));
            list.add(new TargetPicker.Candidate(p.getId(), TargetManager.sortValue(p),
                    RotationUtils.getDistanceToEntityBox(p), angle));
        }
        int i = picker.pick(list, target == null ? Integer.MIN_VALUE : target.getId(), aimRange);
        return i < 0 ? null : candidates.get(i);
    }

    private float flickBudget() {
        long remainingMs = Math.max(0L, currentAttackDelay - attackTimer.getTime());
        return remainingMs / 50f + LagManager.getPingAsTicks() / 2f;
    }

    private void resetAttackCycle() {
        long now = System.currentTimeMillis();
        long overrun = now - (attackTimer.lastMS + currentAttackDelay);
        attackTimer.setTime(now - (overrun >= 0 && overrun < 50 ? overrun : 0));
        currentAttackDelay = getAttackDelay();
    }

    private boolean shouldMissClick(EventSilentRotation.Post event) {
        double graceMs = CLICK_GRACE_MS;
        if (graceMs <= 0)
            return false;
        if (everOnTarget && onTargetTimer.getTime() <= graceMs)
            return true;

        int lookahead = Math.max(1, (int) Math.round(graceMs / 50.0));
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 look = Entity.calculateViewVector(event.getPitch(), event.getYaw());
        Vec3 end = eyes.add(look.x * ATTACK_RANGE, look.y * ATTACK_RANGE, look.z * ATTACK_RANGE);
        float border = target.getPickRadius();
        for (int t = 1; t <= lookahead; t++) {
            AABB box = aim.predictBox(target, t).inflate(border);
            if (box.contains(eyes) || box.clip(eyes, end).isPresent())
                return true;
        }

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
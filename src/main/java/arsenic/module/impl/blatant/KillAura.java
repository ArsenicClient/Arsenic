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
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.minecraft.AutoBlocker;
import arsenic.utils.minecraft.BadPacketsManager;
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

@ModuleInfo(name = "KillAura", category = ModuleCategory.COMBAT, tier = arsenic.module.ModuleTier.BLATANT)
public class KillAura extends Module {

    public RangeProperty speed = new RangeProperty("speed", new RangeValue(1, 360, 20, 50,1), SliderScale.LOG);
    public RangeProperty returnSpeed = new RangeProperty("Return Speed", new RangeValue(1, 90, 5, 15, 1), SliderScale.LOG);
    public RangeProperty aps = new RangeProperty("APS", new RangeValue(1, 20, 8, 12, 1));
    public final BooleanProperty silentRotations = new BooleanProperty("Silent Rotations", true);
    public final BooleanProperty disableOnFlag = new BooleanProperty("Disable On Flag", true);
    public final EnumProperty<AutoBlocker.Mode> autoBlock = new EnumProperty<>("Auto Block", AutoBlocker.Mode.None);
    @PropertyInfo(reliesOn = "Auto Block", value = "Hypixel")
    public final BooleanProperty blockOnRightClickOnly = new BooleanProperty("Only On Right Click", true);
    public EntityPlayer target = null;
    private boolean hadTarget = false;
    private boolean wasUsingItem;
    private final AutoBlocker blocker = new AutoBlocker();
    private AutoBlocker.Mode lastBlockMode = AutoBlocker.Mode.None;
    private boolean blockAttackOk = true;
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
        blocker.reset();
    }

    @Override
    protected void onDisable() {
        blocker.reset();
        if (lastBlockMode == AutoBlocker.Mode.Legit && mc.thePlayer != null)
            blocker.tickLegit(null, false);
        lastBlockMode = AutoBlocker.Mode.None;
        blockAttackOk = true;
    }

    /** Runs the selected autoblock for this tick; the result gates the attack in the post listener. */
    private void autoBlockTick() {
        AutoBlocker.Mode mode = autoBlock.getValue();
        if (mode != AutoBlocker.Mode.Hypixel && blocker.isCycling())
            blocker.tickHypixel(false);
        if (mode != AutoBlocker.Mode.Legit && lastBlockMode == AutoBlocker.Mode.Legit)
            blocker.tickLegit(null, false);
        lastBlockMode = mode;
        blockAttackOk = true;

        boolean active = target != null && PlayerUtils.isPlayerHoldingSword();
        if (mode == AutoBlocker.Mode.Hypixel) {
            boolean rightClick = mc.currentScreen == null && mc.gameSettings.keyBindUseItem.isKeyDown();
            blockAttackOk = blocker.tickHypixel(active && (!blockOnRightClickOnly.getValue() || rightClick));
        } else if (mode == AutoBlocker.Mode.Legit) {
            blocker.tickLegit(target, active);
        }
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        target = pickTarget();
        autoBlockTick();
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
            mc.thePlayer.rotationYaw = event.getYaw();
            mc.thePlayer.rotationPitch = event.getPitch();
        }
        boolean usingItem = mc.thePlayer.isUsingItem();
        MovingObjectPosition raytrace = event.getRayTraceEntity();
        Entity hit = raytrace != null ? raytrace.entityHit : null;
        if (hit != null && mc.thePlayer.getPositionEyes(1f).distanceTo(raytrace.hitVec) > ATTACK_RANGE)
            hit = null;
        if (target != null && hit == target) {
            onTargetTimer.reset();
            everOnTarget = true;
        }
        if (hitflick.attackedThisTick())
            resetAttackCycle();
        EntityPlayer hitPlayer = hit instanceof EntityPlayer && TargetManager.isValidTarget((EntityPlayer) hit)
                ? (EntityPlayer) hit : null;
        if ((target != null || hitPlayer != null)
                && !Arsenic.getArsenic().getServerInfo().isInGuiServerSide()
                && !flickInProgress
                && attackTimer.getTime() >= currentAttackDelay
                && (blocker.isCycling() ? blockAttackOk
                    : !usingItem && !wasUsingItem
                        && !(autoBlock.getValue() == AutoBlocker.Mode.Legit && BadPacketsManager.bad(false, false, false, true, false)))) {
            if (hitPlayer != null) {
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
                mc.thePlayer.swingItem();
                resetAttackCycle();
            }
        }
        wasUsingItem = usingItem;
    };


    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onServerMove = event -> {
        if (disableOnFlag.getValue() && event.getPacket() instanceof S08PacketPlayerPosLook)
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

    private Hitflick hitflick() {
        return Arsenic.getArsenic().getModuleManager().getModuleByClass(Hitflick.class);
    }

    private EntityPlayer pickTarget() {
        if (Arsenic.getArsenic().getServerInfo().isInGuiServerSide())
            return null;
        List<EntityPlayer> candidates = TargetManager.getTargets();
        double aimRange = ATTACK_RANGE + (candidates.size() == 1 ? PRE_AIM_RANGE : 0);
        picker.valueScale = TargetManager.sortMode.getValue() == TargetManager.SortMode.Fov ? 0.04f : 1f;
        SilentRotationManager srm = Arsenic.getArsenic().getSilentRotationManager();
        Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
        List<TargetPicker.Candidate> list = new ArrayList<>(candidates.size());
        for (EntityPlayer p : candidates) {
            AxisAlignedBB box = p.getEntityBoundingBox();
            double dx = (box.minX + box.maxX) / 2 - eyes.xCoord, dz = (box.minZ + box.maxZ) / 2 - eyes.zCoord;
            double dy = MathHelper.clamp_double(eyes.yCoord, box.minY, box.maxY) - eyes.yCoord;
            float yaw = RotationUtils.yawTo(dx, dz);
            float pitch = RotationUtils.pitchTo(dx, dy, dz);
            float angle = Math.max(Math.abs(MathHelper.wrapAngleTo180_float(yaw - srm.yaw)), Math.abs(pitch - srm.pitch));
            list.add(new TargetPicker.Candidate(p.getEntityId(), TargetManager.sortValue(p),
                    RotationUtils.getDistanceToEntityBox(p), angle));
        }
        int i = picker.pick(list, target == null ? Integer.MIN_VALUE : target.getEntityId(), aimRange);
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
        Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
        Vec3 look = ((IMixinEntity) mc.thePlayer).invokeGetVectorForRotation(event.getPitch(), event.getYaw());
        Vec3 end = eyes.addVector(look.xCoord * ATTACK_RANGE, look.yCoord * ATTACK_RANGE, look.zCoord * ATTACK_RANGE);
        float border = target.getCollisionBorderSize();
        for (int t = 1; t <= lookahead; t++) {
            AxisAlignedBB box = aim.predictBox(target, t).expand(border, border, border);
            if (box.isVecInside(eyes) || box.calculateIntercept(eyes, end) != null)
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
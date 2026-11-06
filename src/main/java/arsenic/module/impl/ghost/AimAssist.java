package arsenic.module.impl.ghost;

import arsenic.module.property.Group;
import arsenic.module.property.PropertyGroup;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.SliderScale;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.blatant.KillAura;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.rotations.AimController;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.util.Mth;

import java.util.List;

/** Gently pulls your aim toward the nearest target, either players while you fight or end crystals while you place them. */
@ModuleInfo(name = "AimAssist", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class AimAssist extends Module {

    public enum AimMode {
        /** Your own mouse movement is added on top of the aim. */
        Additive,
        /** The aim takes over the axes it is moving. Axes it is not moving still follow your mouse. */
        Override
    }

    public enum TargetMode {
        Players,
        Crystals
    }

    public final RangeProperty speed = new RangeProperty("Turn Speed", new RangeValue(1, 90, 8, 12, 1), SliderScale.LOG);
    public final EnumProperty<AimMode> mode = new EnumProperty<>("Mode", AimMode.Override);
    public final EnumProperty<AimController.RotationMode> rotationMode = new EnumProperty<>("Rotation Mode", AimController.RotationMode.Lazy);

    @ShowWhen(property = "Rotation Mode", is = "Heuristics")
    @PropertyGroup(Group.ADVANCED)
    public final DoubleProperty missChance = new DoubleProperty("Miss Chance", new DoubleValue(0, 50, 5, 1));

    public final EnumProperty<TargetMode> targetMode = new EnumProperty<>("Targets", TargetMode.Players);
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(1, 10, 10, 0.1));
    public final DoubleProperty fov = new DoubleProperty("FOV", new DoubleValue(1, 360, 360, 1));
    public final BooleanProperty throughWalls = new BooleanProperty("Through Walls", false);
    public final BooleanProperty stickyTarget = new BooleanProperty("Sticky Targeting", false);

    @ShowWhen(property = "Targets", is = "Players")
    public final BooleanProperty requireLeftClick = new BooleanProperty("Require Left Click", true);
    @ShowWhen(property = "Targets", is = "Players")
    public final BooleanProperty weaponOnly = new BooleanProperty("Weapon Only", false);
    @ShowWhen(property = "Targets", is = "Crystals")
    public final BooleanProperty requireRightClick = new BooleanProperty("Require Right Click", false);
    @ShowWhen(property = "Targets", is = "Crystals")
    public final BooleanProperty crystalOnly = new BooleanProperty("Crystal Only", false);
    @ShowWhen(property = "Targets", is = "Crystals")
    public final BooleanProperty verticalOnly = new BooleanProperty("Vertical Only", false);

    public final BooleanProperty onlyOnRotate = new BooleanProperty("Only On Rotate", false);
    public final BooleanProperty stopOnHitbox = new BooleanProperty("Stop On Hitbox", false);
    public final BooleanProperty stopOnBlockBreak = new BooleanProperty("Stop On Block Break", false);

    @PropertyGroup(Group.ADVANCED)
    public final DoubleProperty reactionDelay = new DoubleProperty("Reaction Delay", new DoubleValue(0, 500, 0, 5));
    @PropertyGroup(Group.ADVANCED)
    public final BooleanProperty predictMovement = new BooleanProperty("Predict Movement", true);
    @ShowWhen(property = "Predict Movement", is = "true")
    @PropertyGroup(Group.ADVANCED)
    public final DoubleProperty predictMultiply = new DoubleProperty("Predict Multiply", new DoubleValue(1, 4, 1, 0.1));

    private static final float PREDICTION_TICKS = 3f;
    // Entity.setAngles scales mouse input by this before applying it to rotationYaw / rotationPitch
    private static final float MOUSE_TO_ROTATION = 0.15f;
    // A controller step smaller than this counts as "not moving that axis"
    private static final float OWNS_EPSILON = 0.01f;

    private final AimController aim = new AimController();
    private Entity target;
    private Entity lastPicked;
    private long acquiredAt;
    private boolean aiming;
    private boolean mouseMoved;

    // Mouse input taken while aiming, in rotation units, applied to the silent rotation after the aim has stepped
    private float pendingYaw, pendingPitch;

    @Override
    protected void onEnable() {
        target = null;
        lastPicked = null;
        aiming = false;
        mouseMoved = false;
        clearPending();
        aim.reset();
    }

    @Override
    protected void onDisable() {
        target = null;
        lastPicked = null;
        aiming = false;
        mouseMoved = false;
        clearPending();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        aim.updateDrift();
        aiming = false;
        boolean moved = mouseMoved;
        mouseMoved = false;

        Entity picked = pickTarget();
        if (picked != lastPicked) {
            lastPicked = picked;
            acquiredAt = System.currentTimeMillis();
        }
        target = picked != null && shouldAim(picked, moved) ? picked : null;
        if (target == null) {
            aim.cancelFlick();
            // Nothing is aiming, so the buffered mouse input goes straight to the player
            mc.player.setYRot(mc.player.getYRot() + pendingYaw);
            mc.player.setXRot(clampPitch(mc.player.getXRot() + pendingPitch));
            clearPending();
            return;
        }

        aim.setMissChance((float) (missChance.getValue().getInput() / 100));
        float ticks = predictMovement.getValue() ? PREDICTION_TICKS * (float) predictMultiply.getValue().getInput() : 0f;
        float[] rots = aim.aimAt(target, ticks);
        aim.rotate(event, target, rots, rotationMode.getValue(),
                (float) speed.getValue().getMin(), (float) speed.getValue().getMax(), 0f);
        aiming = true;

        SilentRotationManager srm = Arsenic.getArsenic().getSilentRotationManager();
        // Crystal pitch-only aim: the yaw stays yours
        if (targetMode.getValue() == TargetMode.Crystals && verticalOnly.getValue())
            event.setYaw(srm.yaw);
        float outYaw = event.getYaw();
        float outPitch = event.getPitch();
        boolean additive = mode.getValue() == AimMode.Additive;
        boolean ownsYaw = Math.abs(Mth.wrapDegrees(outYaw - srm.yaw)) > OWNS_EPSILON;
        boolean ownsPitch = Math.abs(outPitch - srm.pitch) > OWNS_EPSILON;

        if (additive || !ownsYaw)
            outYaw += pendingYaw;
        if (additive || !ownsPitch)
            outPitch += pendingPitch;

        event.setYaw(outYaw);
        event.setPitch(clampPitch(outPitch));
        clearPending();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onRotationPost = event -> {
        if (!aiming)
            return;
        mc.player.setYRot(event.getYaw());
        mc.player.setXRot(event.getPitch());
    };

    /** Per-tick gates that pause the aim without dropping the target. */
    private boolean shouldAim(Entity candidate, boolean moved) {
        if (onlyOnRotate.getValue() && !moved)
            return false;
        if (stopOnHitbox.getValue() && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() == candidate)
            return false;
        return System.currentTimeMillis() - acquiredAt >= reactionDelay.getValue().getInput();
    }

    private Entity pickTarget() {
        if (mc.gui.screen() != null)
            return null;
        if (stopOnBlockBreak.getValue() && mc.options.keyAttack.isDown()
                && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK)
            return null;
        KillAura killAura = Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class);
        if (killAura.isEnabled() && killAura.target != null)
            return null;
        Hitflick hitflick = Arsenic.getArsenic().getModuleManager().getModuleByClass(Hitflick.class);
        if (hitflick.ownsRotation())
            return null;
        return targetMode.getValue() == TargetMode.Crystals ? pickCrystal() : pickPlayer();
    }

    private Entity pickPlayer() {
        if (requireLeftClick.getValue() && !mc.options.keyAttack.isDown())
            return null;
        if (weaponOnly.getValue() && !PlayerUtils.isPlayerHoldingWeapon())
            return null;
        if (stickyTarget.getValue() && lastPicked instanceof Player sticky && sticky.isAlive()
                && TargetManager.isValidTarget(sticky) && passesFilters(sticky))
            return sticky;
        for (Player candidate : TargetManager.getTargets())
            if (passesFilters(candidate))
                return candidate;
        return null;
    }

    private Entity pickCrystal() {
        if (requireRightClick.getValue() && !mc.options.keyUse.isDown())
            return null;
        if (crystalOnly.getValue() && !mc.player.getMainHandItem().is(Items.END_CRYSTAL)
                && !mc.player.getOffhandItem().is(Items.END_CRYSTAL))
            return null;
        if (stickyTarget.getValue() && lastPicked instanceof EndCrystal sticky && sticky.isAlive() && passesFilters(sticky))
            return sticky;
        double r = range.getValue().getInput();
        List<EndCrystal> crystals = mc.level.getEntitiesOfClass(EndCrystal.class, mc.player.getBoundingBox().inflate(r));
        EndCrystal best = null;
        double bestDist = Double.MAX_VALUE;
        for (EndCrystal crystal : crystals) {
            if (!crystal.isAlive() || !passesFilters(crystal))
                continue;
            double d = RotationUtils.getDistanceToEntityBox(crystal);
            if (d < bestDist) {
                bestDist = d;
                best = crystal;
            }
        }
        return best;
    }

    private boolean passesFilters(Entity entity) {
        if (RotationUtils.getDistanceToEntityBox(entity) > range.getValue().getInput())
            return false;
        double f = fov.getValue().getInput();
        if (f < 360 && !PlayerUtils.withinFov(entity, (float) f))
            return false;
        return throughWalls.getValue() || !isBehindWall(entity);
    }

    private boolean isBehindWall(Entity entity) {
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 aimVec = RotationUtils.getBestHitVec(entity);
        HitResult mop = PlayerUtils.rayTraceBlocks(eyes, aimVec);
        return mop != null && mop.getType() == HitResult.Type.BLOCK;
    }

    private void clearPending() {
        pendingYaw = 0f;
        pendingPitch = 0f;
    }

    private static float clampPitch(float pitch) {
        return Mth.clamp(pitch, -90f, 90f);
    }

    // Called from setAngles (mouse look). While aiming, the mouse delta is taken here and added back in onRotation.
    public float modifyYaw(float yaw) {
        if (yaw != 0f)
            mouseMoved = true;
        if (target == null) return yaw;
        pendingYaw += yaw * MOUSE_TO_ROTATION;
        return 0f;
    }

    // setAngles subtracts the pitch input, so the rotation delta is the negated value
    public float modifyPitch(float pitch) {
        if (pitch != 0f)
            mouseMoved = true;
        if (target == null) return pitch;
        pendingPitch -= pitch * MOUSE_TO_ROTATION;
        return 0f;
    }
}

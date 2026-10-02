package arsenic.module.impl.ghost;

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
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.rotations.AimController;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Pulls the player's view onto the current target while they are attacking, using the same aim
 * as KillAura ({@link AimController}): the same prediction-led, drifting aim point and the same
 * Instant/Lazy turn shaping.
 * <p>
 * The turn goes through the silent rotation manager like KillAura's, then is written onto the real
 * camera once the manager has committed it - exactly KillAura's non-silent path - so the view and
 * what the server sees never differ. Mouse input is ignored while a target is up, so it can't
 * fight the turn between ticks.
 */
@ModuleInfo(name = "AimAssist", category = ModuleCategory.COMBAT)
public class AimAssist extends Module {

    /** Degrees per tick. Named apart from the old single-value "Speed" so old configs don't fail to load. */
    public final RangeProperty speed = new RangeProperty("Turn Speed", new RangeValue(1, 90, 8, 12, 1));
    public final EnumProperty<AimController.RotationMode> rotationMode = new EnumProperty<>("Rotations", AimController.RotationMode.Lazy);
    /** Ticks of target movement to lead the aim by. */
    public final DoubleProperty prediction = new DoubleProperty("Prediction", new DoubleValue(0, 5, 1, 0.1));

    private final AimController aim = new AimController();
    private LivingEntity target;
    /** Whether this tick's rotation is ours, so Post knows to put it on the camera. */
    private boolean aiming;

    @Override
    protected void onEnable() {
        target = null;
        aiming = false;
        aim.reset();
    }

    @Override
    protected void onDisable() {
        target = null;
        aiming = false;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        aim.updateDrift();
        aiming = false;
        target = pickTarget();
        if (target == null) {
            aim.cancelFlick();
            return;
        }

        float[] rots = aim.getPredictedRotations(target, (float) prediction.getValue().getInput());
        aim.rotate(event, target, rots, rotationMode.getValue(),
                (float) speed.getValue().getMin(), (float) speed.getValue().getMax(), 0f);
        aiming = true;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onRotationPost = event -> {
        if (!aiming)
            return;
        // prevRotationYaw/Pitch were already rolled over this tick, so the frame interpolation
        // renders this as a smooth turn rather than a snap.
        mc.player.rotationYaw = event.getYaw();
        mc.player.rotationPitch = event.getPitch();
    };

    /**
     * The target to pull onto, if any: only while attacking, never through terrain, and never
     * while KillAura or Hitflick already own the rotation - two aimers fighting over one view
     * just jitter between them.
     */
    private LivingEntity pickTarget() {
        if (!mc.options.keyBindAttack.isKeyDown() || mc.gui.screen() != null)
            return null;
        KillAura killAura = Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class);
        if (killAura.isEnabled() && killAura.target != null)
            return null;
        Hitflick hitflick = Arsenic.getArsenic().getModuleManager().getModuleByClass(Hitflick.class);
        if (hitflick.ownsRotation())
            return null;
        LivingEntity candidate = TargetManager.getTarget();
        if (candidate == null || isBehindWall(candidate))
            return null;
        return candidate;
    }

    /**
     * Line of sight test against terrain: trace from the eyes to the target's own hitbox rather
     * than wherever the crosshair happens to be pointing, so being off-target (crosshair on the
     * ground or a wall behind them) doesn't switch the assist off when it's needed most.
     */
    private boolean isBehindWall(LivingEntity entity) {
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 aimVec = RotationUtils.getBestHitVec(entity);
        MovingObjectPosition mop = mc.level.rayTraceBlocks(eyes, aimVec, false, true, false);
        return mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
    }

    /**
     * @param yaw the player's raw mouse delta, before {@code setAngles} scales it
     * @return the value {@code setAngles} should use instead - nothing while a target is up, so
     *         the mouse can't drag the view off the tick's committed turn between ticks
     */
    public float modifyYaw(float yaw) {
        return target == null ? yaw : 0f;
    }

    public float modifyPitch(float pitch) {
        return target == null ? pitch : 0f;
    }
}

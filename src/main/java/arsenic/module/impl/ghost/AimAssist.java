package arsenic.module.impl.ghost;

import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.SliderScale;
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
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

@ModuleInfo(name = "AimAssist", category = ModuleCategory.COMBAT)
public class AimAssist extends Module {

    public enum AimMode {
        /** Your own mouse movement is added on top of the aim. */
        Additive,
        /** The aim takes over the axes it is moving. Axes it is not moving still follow your mouse. */
        Override
    }

    public final RangeProperty speed = new RangeProperty("Turn Speed", new RangeValue(1, 90, 8, 12, 1), SliderScale.LOG);
    public final EnumProperty<AimMode> mode = new EnumProperty<>("Mode", AimMode.Override);

    private static final AimController.RotationMode ROTATION_MODE = AimController.RotationMode.Lazy;
    private static final float PREDICTION_TICKS = 3f;
    // Entity.setAngles scales mouse input by this before applying it to rotationYaw / rotationPitch
    private static final float MOUSE_TO_ROTATION = 0.15f;
    // A controller step smaller than this counts as "not moving that axis"
    private static final float OWNS_EPSILON = 0.01f;

    private final AimController aim = new AimController();
    private EntityLivingBase target;
    private boolean aiming;

    // Mouse input taken while aiming, in rotation units, applied to the silent rotation after the aim has stepped
    private float pendingYaw, pendingPitch;

    @Override
    protected void onEnable() {
        target = null;
        aiming = false;
        clearPending();
        aim.reset();
    }

    @Override
    protected void onDisable() {
        target = null;
        aiming = false;
        clearPending();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        aim.updateDrift();
        aiming = false;
        target = pickTarget();
        if (target == null) {
            aim.cancelFlick();
            // Nothing is aiming, so the buffered mouse input goes straight to the player
            mc.thePlayer.rotationYaw += pendingYaw;
            mc.thePlayer.rotationPitch = clampPitch(mc.thePlayer.rotationPitch + pendingPitch);
            clearPending();
            return;
        }

        float[] rots = aim.aimAt(target, PREDICTION_TICKS);
        aim.rotate(event, target, rots, ROTATION_MODE,
                (float) speed.getValue().getMin(), (float) speed.getValue().getMax(), 0f);
        aiming = true;

        SilentRotationManager srm = Arsenic.getArsenic().getSilentRotationManager();
        float outYaw = event.getYaw();
        float outPitch = event.getPitch();
        boolean additive = mode.getValue() == AimMode.Additive;
        boolean ownsYaw = Math.abs(MathHelper.wrapAngleTo180_float(outYaw - srm.yaw)) > OWNS_EPSILON;
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
        mc.thePlayer.rotationYaw = event.getYaw();
        mc.thePlayer.rotationPitch = event.getPitch();
    };

    private EntityLivingBase pickTarget() {
        if (!mc.gameSettings.keyBindAttack.isKeyDown() || mc.currentScreen != null)
            return null;
        KillAura killAura = Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class);
        if (killAura.isEnabled() && killAura.target != null)
            return null;
        Hitflick hitflick = Arsenic.getArsenic().getModuleManager().getModuleByClass(Hitflick.class);
        if (hitflick.ownsRotation())
            return null;
        EntityLivingBase candidate = TargetManager.getTarget();
        if (candidate == null || isBehindWall(candidate))
            return null;
        return candidate;
    }

    private boolean isBehindWall(EntityLivingBase entity) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
        Vec3 aimVec = RotationUtils.getBestHitVec(entity);
        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyes, aimVec, false, true, false);
        return mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
    }

    private void clearPending() {
        pendingYaw = 0f;
        pendingPitch = 0f;
    }

    private static float clampPitch(float pitch) {
        return MathHelper.clamp_float(pitch, -90f, 90f);
    }

    // Called from setAngles (mouse look). While aiming, the mouse delta is taken here and added back in onRotation.
    public float modifyYaw(float yaw) {
        if (target == null) return yaw;
        pendingYaw += yaw * MOUSE_TO_ROTATION;
        return 0f;
    }

    // setAngles subtracts the pitch input, so the rotation delta is the negated value
    public float modifyPitch(float pitch) {
        if (target == null) return pitch;
        pendingPitch -= pitch * MOUSE_TO_ROTATION;
        return 0f;
    }
}

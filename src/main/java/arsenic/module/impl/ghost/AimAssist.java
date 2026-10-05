package arsenic.module.impl.ghost;

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
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

@ModuleInfo(name = "AimAssist", category = ModuleCategory.COMBAT)
public class AimAssist extends Module {

    public final RangeProperty speed = new RangeProperty("Turn Speed", new RangeValue(1, 90, 8, 12, 1), SliderScale.LOG);

    private static final AimController.RotationMode ROTATION_MODE = AimController.RotationMode.Lazy;
    private static final float PREDICTION_TICKS = 3f;

    private final AimController aim = new AimController();
    private EntityLivingBase target;
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

        float[] rots = aim.aimAt(target, PREDICTION_TICKS);
        aim.rotate(event, target, rots, ROTATION_MODE,
                (float) speed.getValue().getMin(), (float) speed.getValue().getMax(), 0f);
        aiming = true;
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

    public float modifyYaw(float yaw) {
        return target == null ? yaw : 0f;
    }

    public float modifyPitch(float pitch) {
        return target == null ? pitch : 0f;
    }
}

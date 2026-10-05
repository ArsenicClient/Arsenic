package arsenic.module.impl.ghost;

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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "AimAssist", category = ModuleCategory.COMBAT)
public class AimAssist extends Module {

    public final RangeProperty speed = new RangeProperty("Turn Speed", new RangeValue(1, 90, 8, 12, 1), SliderScale.LOG);

    private static final AimController.RotationMode ROTATION_MODE = AimController.RotationMode.Lazy;
    private static final float PREDICTION_TICKS = 3f;

    private final AimController aim = new AimController();
    private LivingEntity target;
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
        mc.player.setYRot(event.getYaw());
        mc.player.setXRot(event.getPitch());
    };

    private LivingEntity pickTarget() {
        if (!mc.options.keyAttack.isDown() || mc.gui.screen() != null)
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

    private boolean isBehindWall(LivingEntity entity) {
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 aimVec = RotationUtils.getBestHitVec(entity);
        HitResult mop = arsenic.utils.minecraft.PlayerUtils.rayTraceBlocks(eyes, aimVec);
        return mop != null && mop.getType() == HitResult.Type.BLOCK;
    }

    public float modifyYaw(float yaw) {
        return target == null ? yaw : 0f;
    }

    public float modifyPitch(float pitch) {
        return target == null ? pitch : 0f;
    }
}

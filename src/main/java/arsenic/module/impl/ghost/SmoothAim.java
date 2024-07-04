package arsenic.module.impl.ghost;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

@ModuleInfo(name = "SmoothAim", category = ModuleCategory.GHOST)
public class SmoothAim extends Module {

    public final EnumProperty<aaMode> mode = new EnumProperty<>("Mode: ", aaMode.Silent);
    @PropertyInfo(reliesOn = "Mode: ", value = "Silent")
    public final BooleanProperty movementFix = new BooleanProperty("MovementFix", true);
    public final BooleanProperty clickOnly = new BooleanProperty("ClickOnly",true);
    public final BooleanProperty fovBased = new BooleanProperty("FovBased",true);
    public final BooleanProperty pitchAssist = new BooleanProperty("PitchAssist",true);
    public final DoubleProperty speed = new DoubleProperty("speed", new DoubleValue(1, 100, 20, 1));

    @EventLink
    public Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        if(mc.currentScreen != null) return;
        if (clickOnly.getValue() && !mc.gameSettings.keyBindAttack.isKeyDown()) return;
        EntityAndRots target = getTargetAndRotations();
        if(target == null) return;
        double fov = RotationUtils.fovFromEntity(target.entity);
        double rotSpeed = speed.getValue().getInput() * (fovBased.getValue() ? (Math.abs(fov) * 2 / 180) : 1);

        if (mode.getValue().equals(aaMode.Silent)) {
            event.setDoMovementFix(movementFix.getValue());
            event.setJumpFix(movementFix.getValue());
            event.setSpeed((float) rotSpeed);
            event.setYaw(target.yaw);
            event.setPitch(pitchAssist.getValue() ? target.pitch : mc.thePlayer.rotationPitch);
        } else {
            float[] rots = RotationUtils.getPatchedAndCappedRots(
                    new float[]{mc.thePlayer.prevRotationYaw,mc.thePlayer.prevRotationPitch},
                    new float[]{target.yaw,pitchAssist.getValue() ? target.pitch : mc.thePlayer.rotationPitch},
                    (float) rotSpeed
            );
            mc.thePlayer.rotationYaw = rots[0];
            mc.thePlayer.rotationPitch = rots[1];
        }
    };

    private EntityAndRots getTargetAndRotations() {
        EntityAndRots target = new EntityAndRots();
        target.entity = TargetManager.getTarget();
        if (target.entity == null) return null;
        float[] rotationsToTarget = RotationUtils.getRotationsToEntity((EntityLivingBase) target.entity);
        target.yaw = rotationsToTarget[0];
        target.pitch = rotationsToTarget[1];
        return target;
    }

    private static class EntityAndRots {
        public Entity entity;
        public float yaw, pitch;
    }

    public enum aaMode {
        Silent,
        Normal
    }
}

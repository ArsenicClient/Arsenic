package arsenic.module.impl.ghost;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

import static arsenic.utils.rotations.RotationUtils.*;

@ModuleInfo(name = "SmoothAim", category = ModuleCategory.GHOST)
public class SmoothAim extends Module {

    public final EnumProperty<aaMode> mode = new EnumProperty<>("Mode: ", aaMode.Silent);
    @PropertyInfo(reliesOn = "Mode: ", value = "Silent")
    public final BooleanProperty movementFix = new BooleanProperty("MovementFix", true);
    public final BooleanProperty clickOnly = new BooleanProperty("ClickOnly",true);
    public final BooleanProperty fovBased = new BooleanProperty("FovBased",true);
    public final BooleanProperty pitchAssist = new BooleanProperty("PitchAssist",true);
    public final DoubleProperty range = new DoubleProperty("range", new DoubleValue(0, 5, 3, 0.1));
    public final DoubleProperty fov = new DoubleProperty("fov", new DoubleValue(1, 360, 90, 1));
    public final DoubleProperty speed = new DoubleProperty("speed", new DoubleValue(1, 50, 20, 0.1));

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

    //sorts based on yaw
    //to be improved later
    private EntityAndRots getTargetAndRotations() {
        List<Entity> targets = PlayerUtils.getPlayersWithin(range.getValue().getInput());
        EntityAndRots target = new EntityAndRots();
        try {
            target.entity = targets.stream().min(Comparator.comparingDouble(entity -> getRotationsToEntity((EntityLivingBase) entity)[0])).get(); //sorts based on yaw
        } catch (NoSuchElementException | NullPointerException e) {
            return null;
        }

        float[] rotationsToTarget = getRotationsToEntity((EntityLivingBase) target.entity);
        if(RotationUtils.getYawDifference(mc.thePlayer.rotationYaw, rotationsToTarget[0]) > fov.getValue().getInput())
            return null;
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

package arsenic.module.impl.ghost;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.entity.Entity;

import static arsenic.utils.rotations.RotationUtils.getPlayerRotationsToVec;
import static arsenic.utils.rotations.RotationUtils.getYawDifference;


@ModuleInfo(name = "SilentAimAssistTest", category = ModuleCategory.GHOST)
public class SilentAimAssistTest extends Module {

    public final DoubleProperty range = new DoubleProperty("range", new DoubleValue(0, 5, 3, 0.1));
    public final DoubleProperty fov = new DoubleProperty("fov", new DoubleValue(0, 90, 90, 1));

    @EventLink
    public Listener<EventSilentRotation> eventListener = event -> {
        if (mc.currentScreen != null)
            return;
        Entity target = null;
        double distance = range.getValue().getInput();
        float[] rotationsToTarget = null;
        for(Entity entity : mc.theWorld.playerEntities) {
            float[] rotations = getPlayerRotationsToVec(entity.getPositionVector().addVector(0, 1.8, 0));
            float tempDistance = mc.thePlayer.getDistanceToEntity(entity);
            if(entity != mc.thePlayer && getYawDifference(rotations[0], mc.thePlayer.rotationYaw) < fov.getValue().getInput() && tempDistance <= distance) {
                rotationsToTarget = rotations;
                target = entity;
                distance = tempDistance;
            }
        }
        if(target == null)
            return;

        event.setYaw(rotationsToTarget[0]);
        event.setPitch(rotationsToTarget[1]);
    };

}

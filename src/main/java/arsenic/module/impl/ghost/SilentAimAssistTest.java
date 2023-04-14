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
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.entity.Entity;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

import static arsenic.utils.rotations.RotationUtils.getPlayerRotationsToVec;
import static arsenic.utils.rotations.RotationUtils.getYawDifference;


@ModuleInfo(name = "SilentAimAssistTest", category = ModuleCategory.GHOST)
public class SilentAimAssistTest extends Module {

    public final DoubleProperty range = new DoubleProperty("range", new DoubleValue(0, 5, 3, 0.1));
    public final DoubleProperty fov = new DoubleProperty("fov", new DoubleValue(0, 180, 90, 1));
    public final DoubleProperty speed = new DoubleProperty("speed", new DoubleValue(0, 180, 20, 1));

    @EventLink
    public Listener<EventSilentRotation> eventListener = event -> {
        if (mc.currentScreen != null)
            return;
        List<Entity> targets = PlayerUtils.getClosestPlayersWithin(range.getValue().getInput());
        Entity target = null;
        try {
            target = targets.stream().min(Comparator.comparingDouble(entity -> getPlayerRotationsToVec(entity.getPositionVector())[0])).get(); //sorts based on yaw
        } catch (NoSuchElementException e) {
            return;
        }

        float[] rotationsToTarget = getPlayerRotationsToVec(target.getPositionVector().addVector(0, target.getEyeHeight(), 0));
        event.setSpeed((float) speed.getValue().getInput());
        event.setYaw(rotationsToTarget[0]);
        event.setPitch(rotationsToTarget[1]);
    };

}

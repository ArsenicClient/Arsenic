package arsenic.module.impl.ghost;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;

import static arsenic.utils.rotations.RotationUtils.*;

@ModuleInfo(name = "AimAssist", category = ModuleCategory.GHOST)
public class AimAssist extends Module {

    public final DoubleProperty range = new DoubleProperty("range", new DoubleValue(0, 5, 3, 0.1));
    public final DoubleProperty speed = new DoubleProperty("speed", new DoubleValue(1, 180, 20, 0.1));

    @EventLink
    public Listener<EventTick> eventListener = event -> {
        if(mc.currentScreen != null)
            return;
        Entity target = null;
        double distance = range.getValue().getInput();
        for(Entity entity : mc.theWorld.playerEntities) {
            float tempDistance = mc.thePlayer.getDistanceToEntity(entity);
            if(entity != mc.thePlayer && tempDistance <= distance) {
                target = entity;
                distance = tempDistance;
            }
        }
        if(target == null)
            return;

        float[] rotations = getPlayerRotationsToVec(target.getPositionVector().addVector(0, target.getEyeHeight(), 0));

        float yawDiff = getYawDifference(rotations[0], mc.thePlayer.rotationYaw);
        if(Math.abs(yawDiff) > (speed.getValue().getInput()/2f))
            yawDiff = (float) (speed.getValue().getInput()/2f) * (yawDiff > 0 ? 1 : -1);
        mc.thePlayer.rotationYaw = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw + yawDiff);

        float pitchDiff = getPitchDifference(mc.thePlayer.rotationPitch, rotations[1]);
        if(Math.abs(pitchDiff) > (speed.getValue().getInput()/2f))
            pitchDiff = (float) (speed.getValue().getInput()/2f) * (pitchDiff > 0 ? 1 : -1);

        mc.thePlayer.rotationPitch = mc.thePlayer.rotationPitch + pitchDiff;

    };


}

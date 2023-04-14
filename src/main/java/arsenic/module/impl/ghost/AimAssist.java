package arsenic.module.impl.ghost;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
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
    public final DoubleProperty speed = new DoubleProperty("speed", new DoubleValue(1, 30, 20, 0.1));

    private float lastPartialTicks;

    @EventLink
    public Listener<EventRender2D> eventListener = event -> {
        if(mc.currentScreen != null)
            return;
        Entity target = PlayerUtils.getClosestPlayerWithin(range.getValue().getInput());
        if(target == null)
            return;

        float partialTicksDiff = event.getPartialTicks() > lastPartialTicks ? event.getPartialTicks() - lastPartialTicks : event.getPartialTicks() - lastPartialTicks + 1;
        lastPartialTicks = event.getPartialTicks();

        float[] rotations = getPlayerRotationsToVec(target.getPositionVector().addVector(0, target.getEyeHeight(), 0));

        double rotSpeed = speed.getValue().getInput()/partialTicksDiff;

        float yawDiff = getYawDifference(rotations[0], mc.thePlayer.rotationYaw);
        if(Math.abs(yawDiff) > rotSpeed)
            yawDiff = (float) rotSpeed * (yawDiff > 0 ? 1 : -1);

        mc.thePlayer.rotationYaw = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw + yawDiff);

        float pitchDiff = -getPitchDifference(mc.thePlayer.rotationPitch, rotations[1]);
        if(Math.abs(pitchDiff) > (rotSpeed/2f))
            pitchDiff = (float) (rotSpeed/2f) * (pitchDiff > 0 ? 1 : -1);

        mc.thePlayer.rotationPitch = mc.thePlayer.rotationPitch + pitchDiff;

    };


}

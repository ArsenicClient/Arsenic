package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;

import static net.minecraft.util.MathHelper.wrapAngleTo180_float;

@ModuleInfo(name = "AimAssist", category = ModuleCategory.GHOST)
public class AimAssist extends Module {

    public final DoubleProperty speed = new DoubleProperty("Speed", new DoubleValue(1, 20, 10, 1));
    private float prevPartialTicks, yawDelta, pitchDelta;

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> eventTickListener = eventTick -> {
        if (!mc.gameSettings.keyBindAttack.isKeyDown()) {
            setNullRots();
            return;
        }

        EntityLivingBase target = TargetManager.getTarget();
        if (target == null) {
            setNullRots();
            return;
        }
        float[] rotationsToTarget = RotationUtils.getRotationsToEntity(target);
        yawDelta = getYawDelta((float) (rotationsToTarget[0] + Math.random() - Math.random()));
        pitchDelta = getPitchDelta((float) (rotationsToTarget[1] + Math.random() - Math.random()));
        prevPartialTicks = 0;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> eventGameLoopListener = event -> {
        if(yawDelta == 0 && pitchDelta == 0)
            return;

        float partialTicksElapsed = event.partialTicks - prevPartialTicks;

        // Add the delta to the current rotations
        float newYaw = mc.thePlayer.rotationYaw + (yawDelta * partialTicksElapsed);
        float newPitch = mc.thePlayer.rotationPitch + (pitchDelta * partialTicksElapsed);

        // Clamp pitch to valid range [-90, 90]
        newPitch = MathHelper.clamp_float(newPitch, -90.0f, 90.0f);

        float[] rotations = RotationUtils.patchGCD(
                new float[]{mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch},
                new float[]{newYaw, newPitch}
        );

        mc.thePlayer.rotationYaw = rotations[0];
        mc.thePlayer.rotationPitch = rotations[1];

        // Store the current partialTicks for next frame calculation
        prevPartialTicks = event.partialTicks;
    };

    private float getYawDelta(float targetYaw) {
        float delta = wrapAngleTo180_float(wrapAngleTo180_float(targetYaw) - wrapAngleTo180_float(mc.thePlayer.rotationYaw));
        float speedValue = (float) (speed.getValue().getInput() * ((Math.sin(Math.toRadians(Math.abs(delta)))/2.0f) + 0.5f));
        return Math.min(speedValue, Math.abs(delta)) * Math.signum(delta);
    }

    private float getPitchDelta(float targetPitch) {
        float delta = targetPitch - mc.thePlayer.rotationPitch;
        float speedValue = (float) (speed.getValue().getInput() * ((Math.sin(Math.toRadians(Math.abs(delta)))/2.0f) + 0.5f));
        return Math.min(speedValue, Math.abs(delta)) * Math.signum(delta);
    }

    private void setNullRots() {
        this.yawDelta = 0;
        this.pitchDelta = 0;
    }

}
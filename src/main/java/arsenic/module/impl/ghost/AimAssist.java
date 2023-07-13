package arsenic.module.impl.ghost;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventSilentRotation;
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
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Mouse;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

import static arsenic.utils.rotations.RotationUtils.*;

@ModuleInfo(name = "AimAssist", category = ModuleCategory.GHOST)
public class AimAssist extends Module { //TODO: Recode this coz its just AHHHHHHHHHHHHHHHHHHHHHHHHHHHHHH

    public final EnumProperty<aaMode> mode = new EnumProperty<>("Mode: ", aaMode.Silent);
    @PropertyInfo(reliesOn = "Mode: ", value = "Silent")
    public final BooleanProperty movementFix = new BooleanProperty("MovementFix", true);
    public final BooleanProperty clickOnly = new BooleanProperty("ClickOnly",true);
    public final DoubleProperty range = new DoubleProperty("range", new DoubleValue(0, 5, 3, 0.1));
    public final DoubleProperty fov = new DoubleProperty("fov", new DoubleValue(1, 360, 90, 1));
    public final DoubleProperty speed = new DoubleProperty("speed", new DoubleValue(1, 50, 20, 0.1));
    private float lastPartialTicks;

    @EventLink
    public Listener<EventRender2D> eventRender2DListener = event -> {
        if(mc.currentScreen != null || mode.getValue() != aaMode.NotSilent)
            return;

        EntityAndRots target = getTargetAndRotations();
        if (clickOnly.getValue() && !mc.gameSettings.keyBindAttack.isKeyDown())
            target = null;
        if(target == null)
            return;

        float partialTicksDiff = event.getPartialTicks() > lastPartialTicks ? event.getPartialTicks() - lastPartialTicks : event.getPartialTicks() - lastPartialTicks + 1;
        lastPartialTicks = event.getPartialTicks();
        double rotSpeed = speed.getValue().getInput()/partialTicksDiff;

        float yawDiff = getYawDifference(target.yaw, mc.thePlayer.rotationYaw);
        if(Math.abs(yawDiff) > rotSpeed)
            yawDiff = (float) rotSpeed * (yawDiff > 0 ? 1 : -1);

        mc.thePlayer.rotationYaw = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw + yawDiff);

        float pitchDiff = -getPitchDifference(mc.thePlayer.rotationPitch, target.pitch);
        if(Math.abs(pitchDiff) > (rotSpeed/2f))
            pitchDiff = (float) (rotSpeed/2f) * (pitchDiff > 0 ? 1 : -1);

        mc.thePlayer.rotationPitch = mc.thePlayer.rotationPitch + pitchDiff;
    };

    @EventLink
    public Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        if(mc.currentScreen != null || mode.getValue() != aaMode.Silent)
            return;
        EntityAndRots target = getTargetAndRotations();
        if (clickOnly.getValue() && !mc.gameSettings.keyBindAttack.isKeyDown())
            target = null;
        if(target == null)
            return;
        event.setDoMovementFix(movementFix.getValue());
        event.setJumpFix(movementFix.getValue());
        event.setSpeed((float) speed.getValue().getInput());
        event.setYaw(target.yaw);
        event.setPitch(target.pitch);
    };

    //sorts based on yaw
    //to be improved later
    private EntityAndRots getTargetAndRotations() {
        List<Entity> targets = PlayerUtils.getPlayersWithin(range.getValue().getInput());
        EntityAndRots target = new EntityAndRots();
        try {
            target.entity = targets.stream().min(Comparator.comparingDouble(entity -> getPlayerRotationsToVec(entity.getPositionVector())[0])).get(); //sorts based on yaw
        } catch (NoSuchElementException | NullPointerException e) {
            return null;
        }

        float[] rotationsToTarget = getPlayerRotationsToVec(target.entity.getPositionVector().addVector(0, target.entity.getEyeHeight() - 0.3, 0));
        if(RotationUtils.getYawDifference(mc.thePlayer.rotationYaw, rotationsToTarget[0]) > fov.getValue().getInput())
            return null;
        target.yaw = rotationsToTarget[0];
        target.pitch = rotationsToTarget[1];
        return target;
    }

    private class EntityAndRots {
        public Entity entity;
        public float yaw, pitch;
    }


    public enum aaMode {
        Silent,
        NotSilent
    }


}

package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.entity.Entity;

@ModuleInfo(name = "Hitflick", category = ModuleCategory.GHOST)
public class Hitflick extends Module {

    public final EnumProperty<FlickDirection> direction = new EnumProperty<>("Direction", FlickDirection.Right);
    @PropertyInfo(reliesOn = "direction", value = "Custom")
    public final DoubleProperty customAngle = new DoubleProperty("Custom Angle", new DoubleValue(1, 180, 90, 1));

    public enum FlickState {
        IDLE,
        FLICKING_AWAY,
        RESTORING
    }

    private FlickState state = FlickState.IDLE;
    private float flickYaw;
    private float originalYaw;
    private Entity pendingTarget; // store target to attack on restore

    public void armFlick(Entity target) {
        this.pendingTarget = target;
        originalYaw = mc.thePlayer.rotationYaw;
        flickYaw = originalYaw + getFlickAngle();
        state = FlickState.FLICKING_AWAY;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onSilentRotation = event -> {
        event.setSpeed(180);
        switch (state) {
            case FLICKING_AWAY:
                event.setYaw(flickYaw);
                state = FlickState.RESTORING;
                break;

            case RESTORING:
                if (pendingTarget != null) {
                    mc.thePlayer.swingItem();
                    mc.playerController.attackEntity(mc.thePlayer, pendingTarget);
                    pendingTarget = null;
                }
                state = FlickState.IDLE;
                break;

            case IDLE:
            default:
                break;
        }
    };

    private float getFlickAngle() {
        switch (direction.getValue()) {
            case Left:   return -90f;
            case Right:  return  90f;
            case Back:   return 180f;
            case Custom: return (float) customAngle.getValue().getInput();
            default:     return  90f;
        }
    }

    public FlickState getState() {
        return state;
    }

    @Override
    protected void onDisable() {
        state = FlickState.IDLE;
        flickYaw = 0;
        originalYaw = 0;
        pendingTarget = null;
    }

    public enum FlickDirection {
        Left, Right, Back, Custom;
    }
}
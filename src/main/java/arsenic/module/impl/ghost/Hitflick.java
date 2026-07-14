package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.player.Blink;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.lag.LagManager;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.entity.Entity;

@ModuleInfo(name = "Hitflick", category = ModuleCategory.GHOST)
public class Hitflick extends Module {

    public final EnumProperty<FlickDirection> direction = new EnumProperty<>("Direction", FlickDirection.Right);
    @PropertyInfo(reliesOn = "Direction", value = "Custom")
    public final DoubleProperty customAngle = new DoubleProperty("Custom Angle", new DoubleValue(1, 180, 90, 1));
    public final DoubleProperty cooldown = new DoubleProperty("Cooldown (ticks)", new DoubleValue(1, 40, 1, 1));
    public final BooleanProperty blinkDuringFlick = new BooleanProperty("Blink", false);

    private long sinceLastFlick = 0;

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
        if (blinkDuringFlick.getValue()) {
            LagManager.acquire(getClass());
        }
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onSilentRotation = event -> {
        switch (state) {
            case FLICKING_AWAY:
                event.setYaw(flickYaw);
                event.setSpeed(360f);
                event.setMovementFix(SilentRotationManager.MovementFix.STRICT);
                state = FlickState.RESTORING;
                break;

            case RESTORING:
                if (blinkDuringFlick.getValue()) {
                    LagManager.release(getClass());
                }
                if (pendingTarget != null) {
                    mc.thePlayer.swingItem();
                    mc.playerController.attackEntity(mc.thePlayer, pendingTarget);
                    pendingTarget = null;
                }
                state = FlickState.IDLE;
                sinceLastFlick = 0;
                break;

            case IDLE:
                sinceLastFlick++;
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

    public boolean shouldFlick() {
        return state == FlickState.IDLE && sinceLastFlick >= cooldown.getValue().getInput();
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
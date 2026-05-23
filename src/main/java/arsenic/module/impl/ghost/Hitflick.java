package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventUpdate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import scala.util.Left;
import scala.util.Right;

@ModuleInfo(name = "Hitflick", category = ModuleCategory.GHOST)
public class Hitflick extends Module {

    public final EnumProperty<FlickDirection> direction = new EnumProperty<>("Direction", FlickDirection.Right);
    public final DoubleProperty customAngle = new DoubleProperty("Custom Angle", new DoubleValue(1, 180, 90, 1));
    public final DoubleProperty flickDuration = new DoubleProperty("Duration (ms)", new DoubleValue(10, 500, 100, 10));
    public final BooleanProperty onlySword = new BooleanProperty("Only Sword", true);
    public final BooleanProperty silent = new BooleanProperty("Silent", true);

    private boolean isFlicking;
    private float flickYaw;
    private long flickUntil;
    private float originalYaw;

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        if (onlySword.getValue() && !PlayerUtils.isPlayerHoldingSword()) return;
        PlayerUtils.addWaterMarkedMessageToChat(event.getTarget().hurtResistantTime);
        float angle;
        switch (direction.getValue()) {
            case Left:
                angle = -90;
                break;
            case Right:
                angle = 90;
                break;
            case Back:
                angle = 180;
                break;
            case Custom:
                angle = (float) customAngle.getValue().getInput();
                break;
            default:
                angle = 90;
        }

        originalYaw = mc.thePlayer.rotationYaw;
        flickYaw = originalYaw + angle;
        isFlicking = true;
        flickUntil = System.currentTimeMillis() + (long) flickDuration.getValue().getInput();

        if (!silent.getValue()) {
            mc.thePlayer.rotationYaw = flickYaw;
        }
    };




    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onUpdate = event -> {
        if (!isFlicking) return;

        if (System.currentTimeMillis() < flickUntil) {
            event.setYaw(flickYaw);
            if (!silent.getValue()) {
                mc.thePlayer.rotationYaw = flickYaw;
            }
        } else {
            isFlicking = false;
            event.setYaw(originalYaw);
            if (!silent.getValue()) {
                mc.thePlayer.rotationYaw = originalYaw;
            }
        }
    };

    @Override
    protected void onDisable() {
        isFlicking = false;
        flickYaw = 0;
        flickUntil = 0;
    }

    public enum FlickDirection {
        Left, Right, Back, Custom;
    }
}

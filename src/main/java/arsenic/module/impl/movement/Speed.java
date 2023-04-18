package arsenic.module.impl.movement;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMove;
import arsenic.event.impl.EventMovementInput;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;


@ModuleInfo(name = "Speed", category = ModuleCategory.MOVEMENT)
public class Speed extends Module {

    public final EnumProperty<sMode> mode = new EnumProperty<>("Mode: ", sMode.ONHIT);

    @PropertyInfo(reliesOn = "Mode: ", value = "ONHIT")
    public final DoubleProperty hurtTicks = new DoubleProperty("Hurt Ticks", new DoubleValue(0, 10, 7, 1));
    public final DoubleProperty speed = new DoubleProperty("Speed multi", new DoubleValue(-2, 2, 0.98, 0.02));
    public final DoubleProperty friction = new DoubleProperty("Friction multi", new DoubleValue(-2, 2, 1, 0.04));

    @EventLink
    public final Listener<EventMovementInput> eventMovementInputListener = event -> {
        if(!mode.getValue().shouldSpeed() || !mc.gameSettings.keyBindForward.isKeyDown())
            return;
        event.setSpeed((float) speed.getValue().getInput());
    };

    @EventLink
    public final Listener<EventMove> eventMoveListener = event -> {
        if(!mode.getValue().shouldSpeed())
            return;
        event.setFriction((float) (event.getFriction() * friction.getValue().getInput()));
    };

    public enum sMode {
        NORMAL,
        ONHIT {
            @Override
            public boolean shouldSpeed() {
                return mc.thePlayer.hurtTime > 5;
            }
        };

        public boolean shouldSpeed() {
            return true;
        }
    }

}

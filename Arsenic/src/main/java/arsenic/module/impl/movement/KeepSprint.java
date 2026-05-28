package arsenic.module.impl.movement;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;

@ModuleInfo(name = "KeepSprint", category = ModuleCategory.MOVEMENT, dev = true)
public class KeepSprint extends Module {

    public final DoubleProperty slowPercent = new DoubleProperty("Slow %", new DoubleValue(0, 100, 40, 1));
    public final BooleanProperty stopSprint = new BooleanProperty("Stop Sprint", true);

    private boolean attacked;

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        attacked = true;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (!attacked) return;
        attacked = false;

        double val = (100.0 - slowPercent.getValue().getInput()) / 100.0;
        mc.thePlayer.motionX *= val;
        mc.thePlayer.motionZ *= val;

        if (stopSprint.getValue()) {
            mc.thePlayer.setSprinting(false);
        }
    };
}

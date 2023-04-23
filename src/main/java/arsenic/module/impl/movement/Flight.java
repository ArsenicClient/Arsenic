package arsenic.module.impl.movement;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;

@ModuleInfo(name = "Flight", category = ModuleCategory.MOVEMENT)

public class Flight extends Module {
    public final DoubleProperty speed = new DoubleProperty("Fly speed", new DoubleValue(0, 1, 0.05, 0.01));
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.thePlayer != null) {
            mc.thePlayer.capabilities.isFlying = true;
            mc.thePlayer.capabilities.setFlySpeed((float) speed.getValue().getInput());
        }
    };
    @Override
    protected void onDisable() {
        if (mc.thePlayer != null) {
            mc.thePlayer.capabilities.isFlying = false;
            mc.thePlayer.capabilities.setFlySpeed((float) 0.05);
        }
    }
}

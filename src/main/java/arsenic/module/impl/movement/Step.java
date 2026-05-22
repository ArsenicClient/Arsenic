package arsenic.module.impl.movement;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;

@ModuleInfo(name = "Step", category = ModuleCategory.MOVEMENT)
public class Step extends Module {

    public final DoubleProperty height = new DoubleProperty("Height", new DoubleValue(0.5, 2.0, 1.0, 0.5));
    private boolean wasStep;

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.thePlayer.onGround && !mc.thePlayer.isInWater() && !mc.thePlayer.isOnLadder()) {
            mc.thePlayer.stepHeight = (float) height.getValue().getInput();
            wasStep = true;
        } else if (wasStep && !mc.thePlayer.onGround) {
            mc.thePlayer.stepHeight = 0.5f;
            wasStep = false;
        }
    };

    @Override
    protected void onDisable() {
        if (mc.thePlayer != null) {
            mc.thePlayer.stepHeight = 0.5f;
        }
        wasStep = false;
    }
}

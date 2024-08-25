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
    public final DoubleProperty speed = new DoubleProperty("Horizontal speed", new DoubleValue(0, 10, 0.5, 0.1));
    public final DoubleProperty speedy = new DoubleProperty("Vertical speed", new DoubleValue(0, 10, 0.5, 0.1));
    private boolean move = false;
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.thePlayer != null) {
            double a = getYaw(mc.thePlayer.moveForward, mc.thePlayer.moveStrafing) + 90;
            double pit = Math.sin(Math.toRadians(a));
            double yaw = Math.cos(Math.toRadians(a));

            if (move){
                mc.thePlayer.motionX = speed.getValue().getInput() *  yaw;
                mc.thePlayer.motionZ = speed.getValue().getInput() * pit;
            }
            if (mc.gameSettings.keyBindJump.isKeyDown() && !mc.gameSettings.keyBindSneak.isKeyDown()) {
                mc.thePlayer.motionY = speedy.getValue().getInput();
            }
            else mc.thePlayer.motionY = 0;

            if (mc.gameSettings.keyBindSneak.isKeyDown() && !mc.gameSettings.keyBindJump.isKeyDown()) {
                mc.thePlayer.motionY = -speedy.getValue().getInput();
            }
        }
    };
    private double getYaw(double f, double s) {
        double yaw = mc.thePlayer.rotationYaw;
        if (f > 0) {
            move = true;
            yaw += s > 0 ? -45 : s < 0 ? 45 : 0;
        } else if (f < 0) {
            move = true;
            yaw += s > 0 ? -135 : s < 0 ? 135 : 180;
        } else {
            move = s != 0;
            yaw += s > 0 ? -90 : s < 0 ? 90 : 0;
        }
        return yaw;
    }
}

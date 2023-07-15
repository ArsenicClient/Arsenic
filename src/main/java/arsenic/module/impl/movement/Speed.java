package arsenic.module.impl.movement;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMove;
import arsenic.event.impl.EventMovementInput;
import arsenic.event.impl.EventPacket;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.timer.Timer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

@ModuleInfo(name = "Speed", category = ModuleCategory.MOVEMENT)
public class Speed extends Module {

    public final EnumProperty<sMode> mode = new EnumProperty<>("Mode: ", sMode.ONHIT);
    @PropertyInfo(reliesOn = "Mode: ", value = "ONHIT")
    public final DoubleProperty bypass = new DoubleProperty("bypass", new DoubleValue(0, 1, 0.1, 0.01));
    @PropertyInfo(reliesOn = "Mode: ", value = "ONHIT")
    public final DoubleProperty time = new DoubleProperty("length", new DoubleValue(0, 1000, 200, 1));
    public final DoubleProperty speedMulti = new DoubleProperty("Speed multi", new DoubleValue(-2, 5, 0.98, 0.02));
    public final DoubleProperty friction = new DoubleProperty("Friction multi", new DoubleValue(-2, 5, 1, 0.04));

    private final Timer disableTimer = new Timer();

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> eventPacketListener = event -> {
        if(!(event.getPacket() instanceof S12PacketEntityVelocity))
            return;
        S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
        if(packet.getEntityID() != mc.thePlayer.getEntityId() || mode.getValue() != sMode.ONHIT)
            return;

        if(packet.getMotionX() + packet.getMotionZ() < bypass.getValue().getInput())
            return;

	disableTimer.setCooldown((long) time.getValue().getInput());
    disableTimer.start();
    };

    @EventLink
    public final Listener<EventMovementInput> eventMovementInputListener = event -> {
        if(!mc.gameSettings.keyBindForward.isKeyDown())
            return;
        if(mode.getValue() == sMode.ONHIT && disableTimer.hasFinished())
            return;
        event.setSpeed((float) speedMulti.getValue().getInput());
    };

    @EventLink
    public final Listener<EventMove> eventMoveListener = event -> {
        if(mode.getValue() == sMode.ONHIT && disableTimer.hasFinished())
            return;
        event.setFriction((float) (event.getFriction() * friction.getValue().getInput()));
    };

    public enum sMode {
        NORMAL,
        ONHIT
    }
}

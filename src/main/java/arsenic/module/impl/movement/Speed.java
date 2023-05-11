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
import net.minecraft.network.play.server.S12PacketEntityVelocity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@ModuleInfo(name = "Speed", category = ModuleCategory.MOVEMENT)
public class Speed extends Module {

    public final EnumProperty<sMode> mode = new EnumProperty<sMode>("Mode: ", sMode.ONHIT) {
        @Override
        public void onValueUpdate() {
            switch (getValue()) {
                case NORMAL:
                    speed = true;
                    break;
                case ONHIT:
                    speed = false;
                    break;
            }
        }
    };

    @PropertyInfo(reliesOn = "Mode: ", value = "ONHIT")
    public final DoubleProperty bypass = new DoubleProperty("bypass", new DoubleValue(0, 1, 0.1, 0.01));
    @PropertyInfo(reliesOn = "Mode: ", value = "ONHIT")
    public final DoubleProperty time = new DoubleProperty("length", new DoubleValue(0, 1000, 200, 1));
    public final DoubleProperty speedMulti = new DoubleProperty("Speed multi", new DoubleValue(-2, 5, 0.98, 0.02));
    public final DoubleProperty friction = new DoubleProperty("Friction multi", new DoubleValue(-2, 5, 1, 0.04));

    private boolean speed;

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> eventPacketListener = event -> {
        if(!(event.getPacket() instanceof S12PacketEntityVelocity))
            return;
        S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
        if(packet.getEntityID() != mc.thePlayer.getEntityId() || mode.getValue() != sMode.ONHIT)
            return;

        if(packet.getMotionX() + packet.getMotionZ() < bypass.getValue().getInput())
            return;

        speed = true;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                Thread.sleep((long) time.getValue().getInput());
            } catch (InterruptedException e) {}
            if(mode.getValue() == sMode.ONHIT)
                speed = false;
        });
    };

    @EventLink
    public final Listener<EventMovementInput> eventMovementInputListener = event -> {
        if(!speed || !mc.gameSettings.keyBindForward.isKeyDown())
            return;
        event.setSpeed((float) speedMulti.getValue().getInput());
    };

    @EventLink
    public final Listener<EventMove> eventMoveListener = event -> {
        if(!speed)
            return;
        event.setFriction((float) (event.getFriction() * friction.getValue().getInput()));
    };

    public enum sMode {
        NORMAL,
        ONHIT
    }

}

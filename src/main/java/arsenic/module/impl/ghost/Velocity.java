package arsenic.module.impl.ghost;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.injection.accessor.IMixinS12PacketEntityVelocity;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

@ModuleInfo(name = "Velocity", category = ModuleCategory.GHOST)
public class Velocity extends Module {

    public final EnumProperty<vMode> veloMode = new EnumProperty<>("Mode:", vMode.Reduce);

    //public final BooleanProperty jumpPacket = new BooleanProperty("Jump Packet", false);

    @PropertyInfo(reliesOn = "Mode:", value = "Reduce")
    public final DoubleProperty horizontalVelo = new DoubleProperty("Horizontal", new DoubleValue(0, 100, 80, 1));
    @PropertyInfo(reliesOn = "Mode:", value = "Reduce")
    public final DoubleProperty verticalVelo = new DoubleProperty("Vertical", new DoubleValue(0, 100, 80, 1));

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> packetEvent = event -> {
        if(!(event.getPacket() instanceof S12PacketEntityVelocity && ((S12PacketEntityVelocity) event.getPacket()).getEntityID() == mc.thePlayer.getEntityId()))
            return;
        switch(veloMode.getValue()) {
            case Reduce:
                S12PacketEntityVelocity velocityPacket = (S12PacketEntityVelocity) event.getPacket();
                IMixinS12PacketEntityVelocity iVelocityPacket = (IMixinS12PacketEntityVelocity) velocityPacket;
                iVelocityPacket.setMotionX((int) (velocityPacket.getMotionX() * (horizontalVelo.getValue().getInput()/100f)));
                iVelocityPacket.setMotionY((int) (velocityPacket.getMotionY() * (verticalVelo.getValue().getInput()/100f)));
                iVelocityPacket.setMotionZ((int) (velocityPacket.getMotionZ() * (horizontalVelo.getValue().getInput()/100f)));
                break;
            case Cancel:
                event.cancel();
                break;
        }
    };

    public enum vMode {
        Reduce,
        Cancel
    }

}

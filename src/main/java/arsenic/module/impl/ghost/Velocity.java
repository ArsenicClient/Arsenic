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
    public final EnumProperty<vMode> veloMode = new EnumProperty<>("Mode:", vMode.PACKET);
    @PropertyInfo(reliesOn = "Mode:", value = "PACKET")
    public final DoubleProperty horizontalVelo = new DoubleProperty("Horizontal", new DoubleValue(0, 100, 80, 1));
    @PropertyInfo(reliesOn = "Mode:", value = "PACKET")
    public final DoubleProperty verticalVelo = new DoubleProperty("Vertical", new DoubleValue(0, 100, 80, 1));


    @EventLink
    public final Listener<EventPacket.Incoming.Pre> packetEvent = event -> {
        if (!(event.getPacket() instanceof S12PacketEntityVelocity && ((S12PacketEntityVelocity) event.getPacket()).getEntityID() == mc.thePlayer.getEntityId())) {
            return;
        }

        S12PacketEntityVelocity velocityPacket = (S12PacketEntityVelocity) event.getPacket();
        IMixinS12PacketEntityVelocity iVelocityPacket = (IMixinS12PacketEntityVelocity) velocityPacket;

        switch (veloMode.getValue()) {
            case PACKET:
                iVelocityPacket.setMotionX((int) (velocityPacket.getMotionX() * (horizontalVelo.getValue().getInput() / 100f)));
                iVelocityPacket.setMotionY((int) (velocityPacket.getMotionY() * (verticalVelo.getValue().getInput() / 100f)));
                iVelocityPacket.setMotionZ((int) (velocityPacket.getMotionZ() * (horizontalVelo.getValue().getInput() / 100f)));
                break;
            case CANCEL:
                event.cancel();
                break;
            case HYPIXEL:
                if (mc.thePlayer.onGround) {
                    mc.thePlayer.motionY = velocityPacket.getMotionY() / 8000.0;
                }
                event.cancel();
                break;
        }
    };

    public enum vMode {
        PACKET,
        HYPIXEL,
        CANCEL
    }

}
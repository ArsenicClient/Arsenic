package arsenic.module.impl.movement;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.MoveUtil;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

@ModuleInfo(name = "Speed", category = ModuleCategory.MOVEMENT)
public class Speed extends Module {
    public EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.GROUND);

    @EventLink
    public final Listener<EventUpdate.Pre> eventUpdateListener = event -> {
        if (nullCheck()) {
            return;
        }
        if (!MoveUtil.isMoving()) {
            return;
        }
        switch (mode.getValue()) {
            case GROUND:
                if (mc.thePlayer.onGround) {
                    MoveUtil.strafe(0.28F);
                }
                break;
        }
    };

    @EventLink
    public final Listener<EventMovementInput> movementInputListener = eventMovementInput -> {
        if (MoveUtil.isMoving() && mc.thePlayer.onGround) {
            eventMovementInput.setJump(true);

        }
    };

    public enum Mode {
        GROUND
    }
}

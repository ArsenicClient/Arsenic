package arsenic.module.impl.movement;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.minecraft.MoveUtil;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

@ModuleInfo(name = "Speed", category = ModuleCategory.MOVEMENT)
public class Speed extends Module {
    public EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.BOOST);

    public boolean strafe, cooldown;
    private int cooldownticks = 0;
    private int jumps;

    @Override
    protected void onEnable() {
        cooldownticks = 0;
        jumps = 0;
        strafe = false;
        cooldown = false;
        super.onEnable();
    }

    @EventLink
    public final Listener<EventUpdate.Pre> eventUpdateListener = event -> {
        if (nullCheck()) {
            return;
        }
        if (!MoveUtil.isMoving()) {
            return;
        }
        switch (mode.getValue()) {
            case BOOST:
                if (mc.thePlayer.motionX != 0 && mc.thePlayer.motionZ != 0 && mc.thePlayer.onGround) {
                    mc.thePlayer.jump();
                    if (!strafe) {
                        MoveUtil.strafe(MoveUtil.getPerfectValue(0.42f, 0.45f, 0.50f));
                    }
                }

                if (mc.thePlayer.hurtTime == 9 && !mc.thePlayer.onGround && !cooldown && mc.thePlayer.motionX != 0 && mc.thePlayer.motionZ != 0) {
                    strafe = true;
                    MoveUtil.strafe(Math.hypot(mc.thePlayer.motionX, mc.thePlayer.motionZ) * 1.2);
                    cooldown = true;
                } else {
                    strafe = false;
                }

                if (cooldown) {
                    cooldownticks++;
                }

                if (cooldownticks == 10) {
                    cooldown = false;
                    cooldownticks = 0;
                }
                break;

            case GROUND:
                if (mc.thePlayer.onGround) {
                    MoveUtil.strafe(0.28F);
                }
                break;
        }
    };

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> packetEvent = event -> {
        if (nullCheck()) {
            return;
        }
        Packet packet = event.getPacket();
        if (mode.getValue() == Mode.BOOST) {
            if (packet instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity s12 = (S12PacketEntityVelocity) packet;
                if (s12.getEntityID() == mc.thePlayer.getEntityId()) {
                    if (mc.thePlayer.hurtTime <= 1 && s12.getMotionY() > 0d) {
                        mc.thePlayer.setVelocity(-(s12.getMotionX()) / 8000d, s12.getMotionY() / 8000d, -(s12.getMotionZ()) / 8000d);
                    } else {
                        mc.thePlayer.setVelocity(mc.thePlayer.motionX, s12.getMotionY() / 8000d, mc.thePlayer.motionZ);
                        event.cancel();
                    }
                }
            }
        }
    };

    @EventLink
    public final Listener<EventMovementInput> movementInputListener = eventMovementInput -> {
        if (MoveUtil.isMoving() && mc.thePlayer.onGround) {
            if (mode.getValue() == Mode.GROUND) {
                eventMovementInput.setJump(true);
            }
        }
    };

    public enum Mode {
        GROUND,
        BOOST
    }
}

package arsenic.module.impl.combat;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventUpdate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.timer.Timer;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;


@ModuleInfo(name = "Aura",category = ModuleCategory.BLATANT)
public class Aura extends Module {
    public final EnumProperty<rotMode> mode = new EnumProperty<>("Mode: ", rotMode.Silent);
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(0, 6, 3, 0.1));
    public final BooleanProperty rotate = new BooleanProperty("Rotate", true);

    private final Timer attackTimer = new Timer(1000);
    //public final DoubleProperty rotSpeed = new DoubleProperty("Rotation speed", new DoubleValue(0, 20, 10, 1));
    @EventLink
    public final Listener<EventUpdate.Pre> eventUpdateListener = event -> {
        if(mc.thePlayer == null || mc.theWorld == null)
            return;

        Entity target = PlayerUtils.getClosestPlayerWithin(range.getValue().getInput());

        if (target == null)
            return;

        if(rotate.getValue()) {
            float[] rotations = RotationUtils.getRotations(mc.thePlayer.getPositionVector(), target.getPositionVector());
            switch(mode.getValue()) {
                case Silent:
                    event.setYaw(rotations[0]);
                    event.setPitch(rotations[1]);
                    break;
                case LockView:
                    mc.thePlayer.rotationYaw = rotations[0];
                    mc.thePlayer.rotationPitch = rotations[1];
            }
        }

        if(attackTimer.firstFinish()) {
            mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
            mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
            attackTimer.start();
        }
    };

    public enum rotMode {
        Silent,
        LockView
    }
}

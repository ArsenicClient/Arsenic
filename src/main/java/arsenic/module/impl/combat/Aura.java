package arsenic.module.impl.combat;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;


@ModuleInfo(name = "Aura",category = ModuleCategory.BLATANT)
public class Aura extends Module {
    public final EnumProperty<rotMode> mode = new EnumProperty<>("Mode: ", rotMode.Silent);
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(0, 6, 3, 0.1));
    public final DoubleProperty delay = new DoubleProperty("Delay", new DoubleValue(0, 1000, 300, 1));
    public final BooleanProperty rotate = new BooleanProperty("Rotate", true);
    //public final DoubleProperty rotSpeed = new DoubleProperty("Rotation speed", new DoubleValue(0, 20, 10, 1));
    @EventLink
    public final Listener<EventRender2D> eventRender2DListenerk = event -> {
        double time = 0;
        double delaytime = time + delay.getValue().getInput() / 1000;
        if (mc.thePlayer != null && mc.theWorld != null) {
            Entity target = PlayerUtils.getClosestPlayerWithin(range.getValue().getInput());
            if (rotate.getValue() && target != null) {
                float[] rotations = RotationUtils.getRotations(mc.thePlayer.getPositionVector(), target.getPositionVector());
                if (mode.equals(rotMode.LockView)) {
                    mc.thePlayer.rotationYaw = rotations[0];
                    mc.thePlayer.rotationPitch = rotations[1];
                }
                if (mode.equals(rotMode.Silent)) {
                    mc.getNetHandler().addToSendQueue(new S08PacketPlayerPosLook());
                }
            }
            if (target != null && time <= delaytime) {
                mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
                mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
                time = System.currentTimeMillis();
            }
        }
    };
    public enum rotMode {
        Silent,
        LockView
    }
}

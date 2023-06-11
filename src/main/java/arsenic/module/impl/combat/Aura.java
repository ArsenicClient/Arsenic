package arsenic.module.impl.combat;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventUpdate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.timer.Timer;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.C02PacketUseEntity;


@ModuleInfo(name = "Aura",category = ModuleCategory.BLATANT)
public class Aura extends Module {
    public final EnumProperty<rotMode> mode = new EnumProperty<>("Mode: ", rotMode.Silent);
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(0, 6, 3, 0.1));
    public final RangeProperty aps = new RangeProperty("APS", new RangeValue(1, 20, 10, 15, 1));
    public final BooleanProperty rotate = new BooleanProperty("Rotate", true);

    private Timer attackTimer = new Timer();

    @EventLink
    public final Listener<EventUpdate.Pre> eventUpdateListener = event -> {
        if(mc.thePlayer == null || mc.theWorld == null)
            return;

        Entity target = PlayerUtils.getClosestPlayerWithin(range.getValue().getInput());

        if (target == null) {
            return;
        }

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
            mc.thePlayer.swingItem();
            mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
            long timerDelay = (long) (1000 / aps.getValue().getRandomInRange());
            attackTimer = new Timer(timerDelay);
            attackTimer.start();
        }
    };

    public enum rotMode {
        Silent,
        LockView
    }
}

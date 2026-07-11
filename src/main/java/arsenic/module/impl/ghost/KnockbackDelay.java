package arsenic.module.impl.ghost;

import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventUpdate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.lag.LagManager;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.timer.MSTimer;
import ibxm.Player;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static arsenic.utils.lag.LagManager.releaseDelayed;

@ModuleInfo(name = "KnockbackDelay", category = ModuleCategory.GHOST)
public class KnockbackDelay extends Module {

    public enum DelayMode {Normal, AntiCombo}

    public final RangeProperty delay = new RangeProperty("Delay (ms)", new RangeValue(0, 500, 200, 300, 10));
    public final DoubleProperty chance = new DoubleProperty("Chance %", new DoubleValue(0, 100, 100, 1));
    public final EnumProperty<DelayMode> mode = new EnumProperty<>("Mode", DelayMode.AntiCombo);
    public final DoubleProperty coolDown = new DoubleProperty("Cooldown (ms)", new DoubleValue(0, 5000, 500, 100));
    private final MSTimer releaseTimer = new MSTimer();
    private final MSTimer cdTimer = new MSTimer();
    private long lag = 0;
    private boolean lagging = false;

    @Override
    protected void onDisable() {
        releaseDelayed(p -> true);
        LagManager.undelay(Packet.class);
        lagging = false;
        cdTimer.reset();
    }

    @EventLink
    public Listener<EventUpdate.Pre> preListener = event -> {
        if(releaseTimer.finished(lag))  {
            releaseDelayed(p -> true);
            LagManager.undelay(Packet.class);
            lagging = false;
            cdTimer.reset();
        }
    };

    @EventLink(Priorities.HIGH)
    public Listener<EventPacket.Incoming.Pre> listener = event -> {
       if(event.getPacket() instanceof S12PacketEntityVelocity) {
           S12PacketEntityVelocity p = (S12PacketEntityVelocity) event.getPacket();
           if(p.getMotionX() != 0 && p.getMotionZ() != 0 && !lagging && releaseTimer.finished((long) coolDown.getValue().getInput())) {
               if(Math.random() > chance.getValue().getInput()/100f)
                   return;
               EntityPlayer target = PlayerUtils.getClosestPlayerWithin(5.0);
               if(mode.getValue() == DelayMode.AntiCombo && target != null && (TargetManager.getTimeSinceLastClientSidedHit(target) <= 200 || TargetManager.getTimeSinceLastClientSidedHit(target) >= 1000)  && RotationUtils.getDistanceToEntityBox(target) <= 3)
                   return;
               lagging = true;
               lag = (long) delay.getValue().getRandomInRange();
               releaseTimer.reset();
               LagManager.delay(Packet.class, pk -> lag);
           }
       }
    };

}

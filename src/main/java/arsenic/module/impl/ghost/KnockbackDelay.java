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
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

@ModuleInfo(name = "KnockbackDelay", category = ModuleCategory.COMBAT)
public class KnockbackDelay extends Module {

    public enum DelayMode {Normal, AntiCombo}

    public final RangeProperty delay = new RangeProperty("Delay (ms)", new RangeValue(0, 500, 200, 300, 10));
    public final EnumProperty<DelayMode> mode = new EnumProperty<>("Mode", DelayMode.AntiCombo);
    /** Minimum gap after a delay ends before another may be triggered. */
    public final DoubleProperty cooldown = new DoubleProperty("Cooldown (ms)", new DoubleValue(0, 2000, 500, 10));
    private final MSTimer releaseTimer = new MSTimer();
    private final MSTimer cdTimer = new MSTimer();
    private long lag = 0;
    private boolean lagging = false;

    @Override
    protected void onDisable() {
        LagManager.releaseDelayedFor(KnockbackDelay.class);
        LagManager.undelay(KnockbackDelay.class);
        lagging = false;
        cdTimer.reset();
    }

    @EventLink
    public Listener<EventUpdate.Pre> preListener = event -> {
        // Only act while we're actually holding packets, otherwise the lag==0 initial state
        // makes releaseTimer.finished() perpetually true and drains other modules' queues.
        if(lagging && releaseTimer.finished(lag))  {
            LagManager.releaseDelayedFor(KnockbackDelay.class);
            LagManager.undelay(KnockbackDelay.class);
            lagging = false;
            cdTimer.reset();
        }
    };

    @EventLink(Priorities.HIGH)
    public Listener<EventPacket.Incoming.Pre> listener = event -> {
       if(event.getPacket() instanceof ClientboundSetEntityMotionPacket) {
           ClientboundSetEntityMotionPacket p = (ClientboundSetEntityMotionPacket) event.getPacket();
           if(p.movement().x != 0 && p.movement().z != 0 && !lagging && cdTimer.finished((long) cooldown.getValue().getInput())) {
               if(Math.random() > 100/100f)
                   return;
               Player target = PlayerUtils.getClosestPlayerWithin(5.0);
               if(mode.getValue() == DelayMode.AntiCombo && target != null && (TargetManager.getTimeSinceLastClientSidedHit(target) <= 200 || TargetManager.getTimeSinceLastClientSidedHit(target) >= 1000)  && RotationUtils.getDistanceToEntityBox(target) <= 3)
                   return;
               lagging = true;
               lag = (long) delay.getValue().getRandomInRange();
               releaseTimer.reset();
               LagManager.delay(KnockbackDelay.class, Packet.class, pk -> lag);
           }
       }
    };

}

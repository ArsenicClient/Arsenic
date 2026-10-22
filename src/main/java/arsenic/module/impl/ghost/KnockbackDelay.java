package arsenic.module.impl.ghost;

import arsenic.module.ModuleTier;
import arsenic.module.property.impl.SliderScale;
import arsenic.asm.RequiresPlayer;
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
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

/**
 * Applies the knockback at once, but holds all of our outgoing packets for the delay, in order. The server keeps
 * not hearing from us while the knockback is in flight, so our position lands late from the attacker's side. Holding
 * only movement let swings and attacks overtake it, which Grim flags as packets out of order (Post).
 */
@ModuleInfo(name = "KnockbackDelay", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class KnockbackDelay extends Module {

    public enum DelayMode {Normal, AntiCombo}

    public final RangeProperty delay = new RangeProperty("Delay (ms)", new RangeValue(0, 300, 80, 150, 10));
    public final EnumProperty<DelayMode> mode = new EnumProperty<>("Mode", DelayMode.AntiCombo);
    public final DoubleProperty cooldown = new DoubleProperty("Cooldown (ms)", new DoubleValue(0, 3000, 800, 10), SliderScale.LOG);
    private final MSTimer releaseTimer = new MSTimer();
    private final MSTimer cdTimer = new MSTimer();
    private long lag = 0;
    private boolean lagging = false;

    @Override
    protected void onDisable() {
        stopHolding();
    }

    @EventLink
    public Listener<EventUpdate.Pre> preListener = event -> {
        if (lagging && releaseTimer.finished(lag))
            stopHolding();
    };

    private void stopHolding() {
        LagManager.releaseDelayedOutgoingFor(KnockbackDelay.class);
        LagManager.undelayOutgoing(KnockbackDelay.class);
        lagging = false;
        cdTimer.reset();
    }

    @RequiresPlayer
    @EventLink(Priorities.HIGH)
    public Listener<EventPacket.Incoming.Pre> listener = event -> {
        if (!(event.getPacket() instanceof ClientboundSetEntityMotionPacket))
            return;
        ClientboundSetEntityMotionPacket p = (ClientboundSetEntityMotionPacket) event.getPacket();
        if (p.id() != mc.player.getId())
            return;
        if ((p.movement().x != 0 || p.movement().z != 0) && !lagging && cdTimer.finished((long) cooldown.getValue().getInput())) {
            Player target = PlayerUtils.getClosestPlayerWithin(5.0);
            if (mode.getValue() == DelayMode.AntiCombo && target != null && (TargetManager.getTimeSinceLastClientSidedHit(target) <= 200 || TargetManager.getTimeSinceLastClientSidedHit(target) >= 1000) && RotationUtils.getDistanceToEntityBox(target) <= 3)
                return;
            lagging = true;
            lag = (long) delay.getValue().getRandomInRange();
            releaseTimer.reset();
            LagManager.delayOutgoing(KnockbackDelay.class, LagManager.ALL_PACKETS, pk -> lag);
        }
    };

}

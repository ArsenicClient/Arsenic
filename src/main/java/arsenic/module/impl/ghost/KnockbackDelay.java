package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.ghost.backtrack.TimedPacket;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.PacketUtil;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S40PacketDisconnect;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@ModuleInfo(name = "Knockback Delay", category = ModuleCategory.GHOST)
public class KnockbackDelay extends Module {

    public final RangeProperty delay = new RangeProperty("Delay (ms)", new RangeValue(0, 1000, 200, 300, 10));
    public final DoubleProperty chance = new DoubleProperty("Chance %", new DoubleValue(0, 100, 100, 1));

    private final Queue<TimedPacket> packetQueue = new ConcurrentLinkedQueue<>();
    private final List<Packet<?>> skipPackets = new ArrayList<>();
    private long holdUntil = 0;

    @Override
    protected void onDisable() {
        releaseAll();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onPacket = event -> {
        Packet<?> packet = event.getPacket();

        if (skipPackets.contains(packet)) {
            skipPackets.remove(packet);
            return;
        }

        if (packet instanceof S08PacketPlayerPosLook || packet instanceof S40PacketDisconnect) {
            releaseAll();
            return;
        }

        if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity vel = (S12PacketEntityVelocity) packet;
            if (vel.getEntityID() == mc.thePlayer.getEntityId()) {
                if (Math.random() * 100 <= chance.getValue().getInput()) {
                    int low = (int) delay.getValue().getMin();
                    int high = (int) delay.getValue().getMax();
                    holdUntil = System.currentTimeMillis() + (high > low ? low + (long) (Math.random() * (high - low + 1)) : low);
                }
            }
        }

        if (System.currentTimeMillis() < holdUntil) {
            event.cancel();
            packetQueue.add(new TimedPacket(packet, 0));
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (System.currentTimeMillis() >= holdUntil && !packetQueue.isEmpty()) {
            releaseAll();
        }
    };

    private void releaseAll() {
        holdUntil = 0;
        while (!packetQueue.isEmpty()) {
            Packet<?> packet = packetQueue.poll().getPacket();
            skipPackets.add(packet);
            PacketUtil.receivePacket(packet);
        }
    }
}

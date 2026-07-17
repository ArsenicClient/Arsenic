package arsenic.utils.lag;

import arsenic.utils.timer.Timer;
import net.minecraft.network.Packet;

public class TimedPacket {

    private final Packet<?> packet;
    private final Timer timer;
    /** The module (holder key) that delayed this packet. May be null for legacy usages. */
    private final Class<?> owner;

    public TimedPacket(Packet<?> packet) {
        this(packet, 0L, null);
    }

    public TimedPacket(final Packet<?> packet, final long millis) {
        this(packet, millis, null);
    }

    public TimedPacket(final Packet<?> packet, final long millis, final Class<?> owner) {
        this.packet = packet;
        this.timer = new Timer(millis);
        this.owner = owner;
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public Timer getTimer() {
        return timer;
    }

    public Class<?> getOwner() {
        return owner;
    }

    public boolean isOwnedBy(Class<?> holderKey) {
        return owner != null && owner.equals(holderKey);
    }

}

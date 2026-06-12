package arsenic.module.impl.ghost.fakelag;

import net.minecraft.network.Packet;

public class QueueData {
    private final Packet<?> packet;
    private final long timestamp;

    public QueueData(Packet<?> packet, long timestamp) {
        this.packet = packet;
        this.timestamp = timestamp;
    }

    public Packet<?> getPacket() { return packet; }
    public long getTimestamp() { return timestamp; }
}

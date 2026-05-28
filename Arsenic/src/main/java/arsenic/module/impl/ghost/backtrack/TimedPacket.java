package arsenic.module.impl.ghost.backtrack;

import arsenic.utils.timer.Timer;
import net.minecraft.network.Packet;

public class TimedPacket {

    private final Packet<?> packet;
    private final Timer timer;

    public TimedPacket(Packet<?> packet) {
        this.packet = packet;
        this.timer = new Timer(0);
    }

    public TimedPacket(final Packet<?> packet, final long millis) {
        this.packet = packet;
        this.timer = new Timer(millis);
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public Timer getTimer() {
        return timer;
    }


}

package arsenic.event.impl;

import arsenic.event.types.Event;
import net.minecraft.network.Packet;

public class EventPacket implements Event {

    private final Packet packet;
    private boolean cancelled;

    public EventPacket(Packet packet) {
        this.packet = packet;
    }

    public static class OutGoing extends EventPacket {
        public OutGoing(Packet packet) {
            super(packet);
        }
    }

    public static class Incoming extends EventPacket {
        public Incoming(Packet packet) {
            super(packet);
        }
    }

    public void setCancelled() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public Packet getPacket(){
        return packet;
    }
}

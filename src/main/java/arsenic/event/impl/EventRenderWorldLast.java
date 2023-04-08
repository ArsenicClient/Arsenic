package arsenic.event.impl;

import arsenic.event.types.Event;

public class EventRenderWorldLast implements Event {
    public final float partialTicks;
    public EventRenderWorldLast(float partialTicks) {
        this.partialTicks = partialTicks;
    }
}

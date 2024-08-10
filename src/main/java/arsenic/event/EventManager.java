package arsenic.event;

import arsenic.event.bus.bus.impl.EventBus;
import arsenic.event.types.Event;
import org.jetbrains.annotations.NotNull;

public class EventManager {

    private final EventBus<Event> bus;
    private final boolean flag = false;

    public EventManager() {
        this.bus = new EventBus<>();
    }

    public EventBus<Event> getBus() { return bus; }

    public void subscribe(Object listener) {
        bus.subscribe(listener);
    }

    public void unsubscribe(Object listener) {
        bus.unsubscribe(listener);
    }

    public void post(Event event) {
        bus.post(event);
    }

}

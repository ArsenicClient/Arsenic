package arsenic.event;

import arsenic.event.bus.bus.impl.EventBus;
import arsenic.event.types.Event;

public class EventManager {

    private final EventBus<Event> bus;
    private static boolean replaying;

    public EventManager() {
        this.bus = new EventBus<>();
    }

    public EventBus<Event> getBus() { return bus; }

    public static boolean isReplaying() { return replaying; }
    public static void setReplaying(boolean r) { replaying = r; }

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

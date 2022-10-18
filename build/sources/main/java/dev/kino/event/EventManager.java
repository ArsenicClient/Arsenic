package dev.kino.event;

import dev.kino.event.types.Event;
import io.github.nevalackin.homoBus.bus.impl.EventBus;

public class EventManager {

    private final EventBus<Event> bus;

    public EventManager() {
        this.bus = new EventBus<>();
    }

    public EventBus<Event> getBus() {
        return bus;
    }

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

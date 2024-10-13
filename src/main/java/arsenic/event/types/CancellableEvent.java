package arsenic.event.types;

import java.util.concurrent.atomic.AtomicBoolean;

public class CancellableEvent implements Event {

    AtomicBoolean cancelled = new AtomicBoolean(false);

    public boolean isCancelled() { return this.cancelled.get(); }

    public void setCancelled(boolean cancelled) { this.cancelled.set(cancelled); }

    public void cancel() {
        setCancelled(true);
    }

}

package arsenic.event.bus;

import arsenic.main.Arsenic;
import arsenic.module.Module;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects exceptions thrown by event listeners so the client can show which module is at fault
 * instead of silently swallowing it into the console.
 *
 * <p>A listener that throws usually throws every single tick, so errors are folded together by
 * owner + event + throw site, counted, and dropped again a few seconds after they stop happening.
 */
public final class EventErrors {

    /** How long an error stays on screen after the last time it happened. */
    private static final long LIFETIME = 8_000L;

    /** Most errors shown at once; anything past this is counted but not drawn. */
    private static final int MAX_SHOWN = 5;

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    private EventErrors() {
    }

    public static void report(Object owner, Object event, Throwable throwable) {
        if (throwable == null)
            return;

        String ownerName = nameOf(owner);
        String eventName = event == null ? "?" : event.getClass().getSimpleName();
        String site = siteOf(throwable);

        // deliberately NOT keyed on the site: the same fault reports a real site until the JIT
        // starts eliding traces and a placeholder afterwards, and keying on it would split one
        // fault into two entries - the counted one being the half that never captured a trace
        String key = ownerName + "|" + eventName + "|" + throwable.getClass().getName();

        boolean firstOccurrence;
        synchronized (ENTRIES) {
            Entry entry = ENTRIES.get(key);
            firstOccurrence = entry == null;
            if (firstOccurrence) {
                entry = new Entry(ownerName, eventName, describe(throwable), site);
                ENTRIES.put(key, entry);
            }
            entry.count++;
            entry.lastSeen = System.currentTimeMillis();
            entry.captureTrace(throwable);
        }

        // Log the first occurrence only. A listener that throws on every packet produces thousands
        // of identical lines a minute, which buries the rest of the log and is the reason the trace
        // is kept on the entry instead - see .errors trace.
        if (!firstOccurrence)
            return;

        try {
            Arsenic.getInstance().getLogger().error(
                    "Listener error in " + ownerName + " (" + eventName + ") - further occurrences are"
                            + " counted rather than logged, see .errors", throwable);
        } catch (Throwable ignored) {
        }
    }

    /** Live errors, newest last, with expired ones pruned. */
    public static List<Entry> getActive() {
        long now = System.currentTimeMillis();
        List<Entry> active = new ArrayList<>();

        synchronized (ENTRIES) {
            Iterator<Map.Entry<String, Entry>> iterator = ENTRIES.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry entry = iterator.next().getValue();
                if (now - entry.lastSeen > LIFETIME)
                    iterator.remove();
                else
                    active.add(entry);
            }
        }

        while (active.size() > MAX_SHOWN)
            active.remove(0);
        return active;
    }

    public static void clear() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
        }
    }

    // -----------------------------------------------------------------

    private static String nameOf(Object owner) {
        if (owner == null)
            return "unknown";
        if (owner instanceof Module)
            return ((Module) owner).getName();
        return owner.getClass().getSimpleName();
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        String type = throwable.getClass().getSimpleName();
        if (message == null || message.isEmpty())
            return type;

        // some exceptions (compiler internal errors especially) carry enormous multi-line messages,
        // and this string ends up drawn on the HUD and printed to chat
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (message.length() > 160)
            message = message.substring(0, 160) + "...";
        return type + ": " + message;
    }

    /**
     * First stack frame that is not part of the event bus itself.
     *
     * <p>A listener that throws every tick hits HotSpot's fast-throw optimisation within seconds:
     * the JVM starts reusing one preallocated exception with an empty stack trace, which is why a
     * long running error shows up as a bare "NullPointerException" with nowhere attached. When that
     * happens fall back to the dispatch site and say how to turn the optimisation off.
     */
    private static String siteOf(Throwable throwable) {
        String site = firstUsefulFrame(throwable.getStackTrace());
        if (site != null)
            return site;

        String dispatch = firstUsefulFrame(new Throwable().getStackTrace());
        return (dispatch == null ? "?" : "posted from " + dispatch)
                + " §8(throw site elided by JIT - relaunch with -XX:-OmitStackTraceInFastThrow)";
    }

    private static String firstUsefulFrame(StackTraceElement[] trace) {
        if (trace == null)
            return null;
        for (StackTraceElement element : trace) {
            String clazz = element.getClassName();
            if (clazz.startsWith("arsenic.event.bus") || clazz.startsWith("arsenic.event.EventManager")
                    || clazz.equals(EventErrors.class.getName()))
                continue;
            int dot = clazz.lastIndexOf('.');
            return (dot == -1 ? clazz : clazz.substring(dot + 1)) + "." + element.getMethodName()
                    + ":" + element.getLineNumber();
        }
        return null;
    }

    public static final class Entry {

        private final String owner;
        private final String event;
        private final String message;

        private String site;

        private int count;
        private long lastSeen;
        private List<String> trace;

        private Entry(String owner, String event, String message, String site) {
            this.owner = owner;
            this.event = event;
            this.message = message;
            this.site = site;
        }

        public String getOwner() {
            return owner;
        }

        public String getEvent() {
            return event;
        }

        public String getMessage() {
            return message;
        }

        public String getSite() {
            return site;
        }

        public int getCount() {
            return count;
        }

        /**
         * The stack trace from the first time this error had one.
         *
         * <p>HotSpot only swaps in the trace-less shared exception once a throw site is hot, so the
         * first few occurrences carry a real trace. Keeping it means the throw site is still
         * recoverable later, without restarting the game under
         * {@code -XX:-OmitStackTraceInFastThrow}.
         */
        public List<String> getTrace() {
            return trace == null ? java.util.Collections.emptyList() : java.util.Collections.unmodifiableList(trace);
        }

        private void captureTrace(Throwable throwable) {
            if (trace != null)
                return;
            StackTraceElement[] elements = throwable.getStackTrace();
            if (elements == null || elements.length == 0)
                return;

            trace = new ArrayList<>();
            for (StackTraceElement element : elements) {
                trace.add(element.toString());
                if (trace.size() >= 12)
                    break;
            }

            // if the entry was opened by an already-elided throw, this is the first time the real
            // throw site is known, so replace the placeholder
            String real = firstUsefulFrame(elements);
            if (real != null)
                site = real;
        }

        public long getLastSeen() {
            return lastSeen;
        }

        /** 1 while the error is fresh, fading to 0 as it expires. */
        public float getFade() {
            float remaining = (LIFETIME - (System.currentTimeMillis() - lastSeen)) / (float) LIFETIME;
            return Math.max(0f, Math.min(1f, remaining * 4f));
        }
    }
}

package arsenic.event.bus.bus.impl;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import arsenic.asm.RequiresPlayer;
import arsenic.utils.java.FrameProfiler;
import arsenic.utils.minecraft.PlayerUtils;
import org.jetbrains.annotations.NotNull;
import arsenic.event.bus.EventErrors;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.bus.bus.Bus;
import static arsenic.utils.minecraft.PlayerUtils.isPlayerLoaded;

public final class EventBus<Event> implements Bus<Event> {

    private final Map<Type, List<CallSite<Event>>> callSiteMap = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<Listener<Event>>> listenerCache = new ConcurrentHashMap<>();

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    public EventBus() {
    }

    @Override
    public void subscribe(final @NotNull Object subscriber) {
        boolean modified = false;
        for (final Field field : subscriber.getClass().getDeclaredFields()) {
            final EventLink annotation = field.getAnnotation(EventLink.class);
            final RequiresPlayer rp = field.getAnnotation(RequiresPlayer.class);
            if (annotation != null) {
                final Type eventType = ((ParameterizedType) (field.getGenericType())).getActualTypeArguments()[0];

                if (!field.isAccessible())
                    field.setAccessible(true);
                try {
                    Listener<Event> listener = (Listener<Event>) LOOKUP.unreflectGetter(field).invokeWithArguments(subscriber);

                    if (listener == null) {
                        EventErrors.report(subscriber, null, new IllegalStateException(
                                "listener field '" + field.getName() + "' was null when subscribed"
                                        + " - subscribing before field initialisers have run"));
                        continue;
                    }

                    Listener<Event> originalListener = listener;
                    final FrameProfiler.Section profiled = FrameProfiler.listener(subscriber, field.getName(), eventType);
                    listener = event -> {
                        if (rp != null && !isPlayerLoaded()) return;
                        long start = FrameProfiler.start();
                        try {
                            originalListener.call(event);
                        } catch (Throwable t) {
                            EventErrors.report(subscriber, event, t);
                        } finally {
                            FrameProfiler.end(profiled, start);
                        }
                    };

                    final byte priority = annotation.value();
                    final CallSite<Event> callSite = new CallSite<>(subscriber, listener, priority);

                    List<CallSite<Event>> callSites = this.callSiteMap.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
                    callSites.add(callSite);
                    if (callSites.size() > 1) {
                        List<CallSite<Event>> sorted = new ArrayList<>(callSites);
                        sorted.sort((o1, o2) -> o2.priority - o1.priority);
                        callSites.clear();
                        callSites.addAll(sorted);
                    }
                    modified = true;
                } catch (Throwable ignored) {
                }
            }
        }

        if (modified) {
            listenerCache.clear();
        }
    }

    @Override
    public void unsubscribe(final Object subscriber) {
        boolean modified = false;
        for (List<CallSite<Event>> callSites : this.callSiteMap.values()) {
            if (callSites.removeIf(eventCallSite -> eventCallSite.owner == subscriber)) {
                modified = true;
            }
        }

        if (modified) {
            listenerCache.clear();
        }
    }

    private List<Listener<Event>> computeListenersFor(Class<?> eventClass) {
        List<CallSite<Event>> matching = new ArrayList<>();
        for (Map.Entry<Type, List<CallSite<Event>>> entry : callSiteMap.entrySet()) {
            Type type = entry.getKey();
            if (type instanceof Class<?>) {
                if (((Class<?>) type).isAssignableFrom(eventClass)) {
                    matching.addAll(entry.getValue());
                }
            } else if (type.equals(eventClass)) {
                matching.addAll(entry.getValue());
            }
        }

        if (matching.isEmpty()) {
            return Collections.emptyList();
        }

        matching.sort((o1, o2) -> o2.priority - o1.priority);
        List<Listener<Event>> list = new ArrayList<>(matching.size());
        for (CallSite<Event> cs : matching) {
            list.add(cs.listener);
        }
        return list;
    }

    @Override
    public void post(final @NotNull Event event) {
        try {
            final List<Listener<Event>> listeners = listenerCache.computeIfAbsent(event.getClass(), this::computeListenersFor);

            int i = 0;
            final int listenersSize = listeners.size();

            while (i < listenersSize) {
                listeners.get(i++).call(event);
            }
        } catch (Exception e) {
            EventErrors.report(null, event, e);
            System.out.println("\u001B[31m" + "ERROR IN THE EVENT BUS");
            e.printStackTrace();
            System.out.println("StackTrace Ends" + "\u001B[0m");
        }
    }

    private static class CallSite<Event> {
        private final Object owner;
        private final Listener<Event> listener;
        private final byte priority;

        public CallSite(Object owner, Listener<Event> listener, byte priority) {
            this.owner = owner;
            this.listener = listener;
            this.priority = priority;
        }
    }
}

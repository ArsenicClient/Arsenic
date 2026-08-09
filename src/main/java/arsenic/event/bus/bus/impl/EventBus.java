package arsenic.event.bus.bus.impl;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import arsenic.asm.RequiresPlayer;
import arsenic.utils.minecraft.PlayerUtils;
import org.jetbrains.annotations.NotNull;
import arsenic.event.bus.EventErrors;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.bus.bus.Bus;
import static arsenic.utils.minecraft.PlayerUtils.isPlayerNotLoaded;

public final class EventBus<Event> implements Bus<Event> {

    private final Map<Type, List<CallSite<Event>>> callSiteMap;
    private final Map<Type, List<Listener<Event>>> listenerCache;

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    public EventBus() {
        callSiteMap = new HashMap<>();
        listenerCache = new HashMap<>();
    }

    @Override
    public void subscribe(final @NotNull Object subscriber) {
        for (final Field field : subscriber.getClass().getDeclaredFields()) {
            final EventLink annotation = field.getAnnotation(EventLink.class);
            final RequiresPlayer rp = field.getAnnotation(RequiresPlayer.class);
            if (annotation != null) {
                final Type eventType = ((ParameterizedType) (field.getGenericType())).getActualTypeArguments()[0];

                if (!field.isAccessible())
                    field.setAccessible(true);
                try {
                    Listener<Event> listener = (Listener<Event>) LOOKUP.unreflectGetter(field).invokeWithArguments(subscriber);

                    // A null field means we were handed a half-constructed object: its initialisers
                    // have not run. Registering it anyway would bake a call site that throws NPE on
                    // every dispatch, forever, with nothing pointing back at the real mistake.
                    if (listener == null) {
                        EventErrors.report(subscriber, null, new IllegalStateException(
                                "listener field '" + field.getName() + "' was null when subscribed"
                                        + " - subscribing before field initialisers have run"));
                        continue;
                    }

                    Listener<Event> originalListener = listener;
                    listener = event -> {
                        if (rp != null && !isPlayerNotLoaded()) return;
                        try {
                            originalListener.call(event);
                        } catch (Throwable t) {
                            // caught per listener so the throw can be attributed to the module that
                            // owns it, and so one broken listener cannot kill the rest of the event
                            EventErrors.report(subscriber, event, t);
                        }
                    };


                    final byte priority = annotation.value();

                    final List<CallSite<Event>> callSites;
                    final CallSite<Event> callSite = new CallSite<>(subscriber, listener, priority);

                    if (this.callSiteMap.containsKey(eventType)) {
                        callSites = this.callSiteMap.get(eventType);
                        callSites.add(callSite);
                        callSites.sort((o1, o2) -> o2.priority - o1.priority);
                    } else {
                        callSites = new ArrayList<>(1);
                        callSites.add(callSite);
                        this.callSiteMap.put(eventType, callSites);
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        this.populateListenerCache();
    }

    private void populateListenerCache() {
        final Map<Type, List<CallSite<Event>>> callSiteMap = this.callSiteMap;
        final Map<Type, List<Listener<Event>>> listenerCache = this.listenerCache;

        for (final Type type : callSiteMap.keySet()) {
            final List<CallSite<Event>> callSites = callSiteMap.get(type);
            final int size = callSites.size();
            final List<Listener<Event>> listeners = new ArrayList<>(size);

            for (int i = 0; i < size; i++)
                listeners.add(callSites.get(i).listener);

            listenerCache.put(type, listeners);
        }
    }

    @Override
    public void unsubscribe(final Object subscriber) {
        for (List<CallSite<Event>> callSites : this.callSiteMap.values()) {
            callSites.removeIf(eventCallSite -> eventCallSite.owner == subscriber);
        }

        this.populateListenerCache();
    }

    @Override
    public void post(final @NotNull Event event) {
        try {
            final List<Listener<Event>> listeners = listenerCache.getOrDefault(event.getClass(), Collections.emptyList());

            int i = 0;
            final int listenersSize = listeners.size();

            while (i < listenersSize) { listeners.get(i++).call(event); }
        } catch (Exception e){
            // safety net: listener bodies are wrapped individually, so this only catches problems in
            // the dispatch itself
            EventErrors.report(null, event, e);
            System.out.println("\u001B[31m"+"ERROR IN THE EVENT BUS");
            e.printStackTrace();
            System.out.println("StackTrace Ends"+"\u001B[0m");
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
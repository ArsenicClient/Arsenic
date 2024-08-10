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
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.bus.bus.Bus;
import static arsenic.utils.minecraft.PlayerUtils.isPlayerNotLoaded;

public class EventBus<Event> implements Bus<Event> {

    private final Map<Type, List<CallSite<Event>>> callSiteMap;
    private final Map<Type, List<Listener<Event>>> listenerCache;

    private boolean flag = false;

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

                    Listener<Event> originalListener = listener;
                    listener = event -> {
                        if (rp != null && !isPlayerNotLoaded()) return;
                        originalListener.call(event);
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
        if(flag)
            return;
        try {
            final List<Listener<Event>> listeners = listenerCache.getOrDefault(event.getClass(), Collections.emptyList());

            int i = 0;
            final int listenersSize = listeners.size();

            while (i < listenersSize) { listeners.get(i++).call(event); }
        } catch (Exception e){
            PlayerUtils.addWaterMarkedMessageToChat(e.getClass().getSimpleName() + " IN THE EVENT BUS!");
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

    public void setFlag(boolean flag) {
        this.flag = flag;
    }
}
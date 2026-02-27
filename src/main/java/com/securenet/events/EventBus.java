package com.securenet.events;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Bus d'événements central (pattern Observer).
 * Permet la communication découplée entre les modules.
 *
 * Usage :
 *   EventBus.subscribe(SecurityEvent.class, e -> handleEvent(e));
 *   EventBus.publish(new SecurityEvent(...));
 */
public class EventBus {

    private static final EventBus INSTANCE = new EventBus();
    private final Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

    private EventBus() {}

    public static EventBus getInstance() {
        return INSTANCE;
    }

    /**
     * S'abonner à un type d'événement.
     */
    @SuppressWarnings("unchecked")
    public <T> void subscribe(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                 .add((Consumer<Object>) listener);
    }

    /**
     * Publier un événement à tous les abonnés.
     */
    public void publish(Object event) {
        List<Consumer<Object>> subs = listeners.get(event.getClass());
        if (subs != null) {
            for (Consumer<Object> sub : subs) {
                sub.accept(event);
            }
        }
    }

    /**
     * Se désabonner (optionnel, utile en test).
     */
    public void clearAll() {
        listeners.clear();
    }
}

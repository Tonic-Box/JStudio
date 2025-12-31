package com.tonic.ui.event;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central event bus for JStudio.
 * Thread-safe and ensures event handlers are called on the EDT.
 */
public class EventBus {

    private static final EventBus INSTANCE = new EventBus();

    private final Map<Class<?>, List<EventHandler<?>>> handlers = new HashMap<>();

    private EventBus() {
    }

    public static EventBus getInstance() {
        return INSTANCE;
    }

    /**
     * Register a handler for a specific event type.
     */
    public <T extends Event> void register(Class<T> eventType, EventHandler<T> handler) {
        synchronized (handlers) {
            List<EventHandler<?>> list = handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
            list.add(handler);
        }
    }

    /**
     * Unregister a handler.
     */
    public <T extends Event> void unregister(Class<T> eventType, EventHandler<T> handler) {
        synchronized (handlers) {
            List<EventHandler<?>> list = handlers.get(eventType);
            if (list != null) {
                list.remove(handler);
            }
        }
    }

    /**
     * Post an event to all registered handlers.
     * Handlers are invoked on the Swing EDT.
     */
    @SuppressWarnings("unchecked")
    public void post(Event event) {
        List<EventHandler<?>> list;
        synchronized (handlers) {
            list = handlers.get(event.getClass());
            if (list == null || list.isEmpty()) {
                return;
            }
            // Copy to avoid concurrent modification
            list = new ArrayList<>(list);
        }

        for (EventHandler<?> handler : list) {
            if (SwingUtilities.isEventDispatchThread()) {
                ((EventHandler<Event>) handler).handle(event);
            } else {
                EventHandler<Event> h = (EventHandler<Event>) handler;
                SwingUtilities.invokeLater(() -> h.handle(event));
            }
        }
    }

    /**
     * Post an event asynchronously (always invokes handlers on EDT later).
     */
    @SuppressWarnings("unchecked")
    public void postAsync(Event event) {
        List<EventHandler<?>> list;
        synchronized (handlers) {
            list = handlers.get(event.getClass());
            if (list == null || list.isEmpty()) {
                return;
            }
            list = new ArrayList<>(list);
        }

        for (EventHandler<?> handler : list) {
            EventHandler<Event> h = (EventHandler<Event>) handler;
            SwingUtilities.invokeLater(() -> h.handle(event));
        }
    }

    /**
     * Clear all handlers. Useful for testing.
     */
    public void clear() {
        synchronized (handlers) {
            handlers.clear();
        }
    }

    /**
     * Functional interface for event handlers.
     */
    @FunctionalInterface
    public interface EventHandler<T extends Event> {
        void handle(T event);
    }
}

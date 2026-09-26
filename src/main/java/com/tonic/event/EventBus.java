package com.tonic.event;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** The application-wide event bus; handlers are keyed by exact event class and always run on the EDT. */
public class EventBus
{

    private static final EventBus INSTANCE = new EventBus();

    private final Map<Class<?>, List<EventHandler<?>>> handlers = new HashMap<>();

    private EventBus()
    {
    }

    /** @return the shared bus */
    public static EventBus getInstance()
    {
        return INSTANCE;
    }

    /**
     * Registers a handler for one event class.
     *
     * @param <T> the event type
     * @param eventType the exact event class to receive; subclasses are not delivered
     * @param handler the handler to call
     */
    public <T extends Event> void register(Class<T> eventType, EventHandler<T> handler)
    {
        synchronized (handlers)
        {
            List<EventHandler<?>> list = handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
            list.add(handler);
        }
    }

    /**
     * Removes a handler; does nothing if it was not registered.
     *
     * @param <T> the event type
     * @param eventType the event class it was registered for
     * @param handler the handler to remove
     */
    public <T extends Event> void unregister(Class<T> eventType, EventHandler<T> handler)
    {
        synchronized (handlers)
        {
            List<EventHandler<?>> list = handlers.get(eventType);
            if (list != null)
            {
                list.remove(handler);
            }
        }
    }

    /**
     * Delivers an event to every handler of its exact class, directly when on the EDT and otherwise queued to it.
     *
     * @param event the event to deliver
     */
    @SuppressWarnings("unchecked")
    public void post(Event event)
    {
        List<EventHandler<?>> list;
        synchronized (handlers)
        {
            list = handlers.get(event.getClass());
            if (list == null || list.isEmpty())
            {
                return;
            }
            list = new ArrayList<>(list);
        }

        for (EventHandler<?> handler : list)
        {
            if (SwingUtilities.isEventDispatchThread())
            {
                ((EventHandler<Event>) handler).handle(event);
            }
            else
            {
                EventHandler<Event> h = (EventHandler<Event>) handler;
                SwingUtilities.invokeLater(() -> h.handle(event));
            }
        }
    }

    /** Removes every registered handler. */
    public void clear()
    {
        synchronized (handlers)
        {
            handlers.clear();
        }
    }

    /**
     * Functional interface for event handlers.
     */
    @FunctionalInterface
    public interface EventHandler<T extends Event>
    {
        /**
         * Handles one posted event, on the EDT.
         *
         * @param event the posted event
         */
        void handle(T event);
    }
}

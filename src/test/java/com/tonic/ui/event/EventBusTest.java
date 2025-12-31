package com.tonic.ui.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = EventBus.getInstance();
        eventBus.clear();
    }

    @AfterEach
    void tearDown() {
        eventBus.clear();
    }

    @Test
    void testSingletonInstance() {
        EventBus instance1 = EventBus.getInstance();
        EventBus instance2 = EventBus.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    void testRegisterAndPostEvent() throws Exception {
        List<TestEvent> receivedEvents = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        eventBus.register(TestEvent.class, event -> {
            receivedEvents.add(event);
            latch.countDown();
        });

        TestEvent testEvent = new TestEvent("test-source", "payload");

        SwingUtilities.invokeAndWait(() -> eventBus.post(testEvent));

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(1, receivedEvents.size());
        assertEquals("payload", receivedEvents.get(0).getPayload());
    }

    @Test
    void testMultipleHandlersReceiveEvent() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);

        for (int i = 0; i < 3; i++) {
            eventBus.register(TestEvent.class, event -> {
                callCount.incrementAndGet();
                latch.countDown();
            });
        }

        SwingUtilities.invokeAndWait(() -> eventBus.post(new TestEvent("source", "data")));

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(3, callCount.get());
    }

    @Test
    void testUnregisterHandler() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        EventBus.EventHandler<TestEvent> handler = event -> callCount.incrementAndGet();

        eventBus.register(TestEvent.class, handler);
        SwingUtilities.invokeAndWait(() -> eventBus.post(new TestEvent("s", "d")));
        assertEquals(1, callCount.get());

        eventBus.unregister(TestEvent.class, handler);
        SwingUtilities.invokeAndWait(() -> eventBus.post(new TestEvent("s", "d")));
        assertEquals(1, callCount.get());
    }

    @Test
    void testNoHandlersForEventType() {
        assertDoesNotThrow(() -> {
            SwingUtilities.invokeAndWait(() -> eventBus.post(new TestEvent("s", "d")));
        });
    }

    @Test
    void testEventTypeIsolation() throws Exception {
        List<String> received = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        eventBus.register(TestEvent.class, event -> {
            received.add("TestEvent");
            latch.countDown();
        });

        eventBus.register(OtherEvent.class, event -> {
            received.add("OtherEvent");
        });

        SwingUtilities.invokeAndWait(() -> eventBus.post(new TestEvent("s", "d")));

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(1, received.size());
        assertEquals("TestEvent", received.get(0));
    }

    @Test
    void testClearRemovesAllHandlers() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        eventBus.register(TestEvent.class, event -> callCount.incrementAndGet());
        eventBus.register(OtherEvent.class, event -> callCount.incrementAndGet());

        eventBus.clear();

        SwingUtilities.invokeAndWait(() -> {
            eventBus.post(new TestEvent("s", "d"));
            eventBus.post(new OtherEvent("s"));
        });

        Thread.sleep(100);
        assertEquals(0, callCount.get());
    }

    @Test
    void testEventTimestamp() {
        long before = System.currentTimeMillis();
        TestEvent event = new TestEvent("source", "data");
        long after = System.currentTimeMillis();

        assertTrue(event.getTimestamp() >= before);
        assertTrue(event.getTimestamp() <= after);
    }

    @Test
    void testEventSource() {
        String source = "my-source";
        TestEvent event = new TestEvent(source, "data");
        assertEquals(source, event.getSource());
    }

    static class TestEvent extends Event {
        private final String payload;

        TestEvent(Object source, String payload) {
            super(source);
            this.payload = payload;
        }

        String getPayload() {
            return payload;
        }
    }

    static class OtherEvent extends Event {
        OtherEvent(Object source) {
            super(source);
        }
    }
}

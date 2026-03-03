package io.agentrunr.channel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChannelRegistryTest {

    private ChannelRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ChannelRegistry();
    }

    @Test
    void shouldHaveRestChannelByDefault() {
        var channels = registry.listChannels();
        assertTrue(channels.contains("rest"));
        assertNotNull(registry.getRestChannel());
    }

    @Test
    void shouldRegisterAndRetrieveChannel() {
        var testChannel = new TestChannel("test");
        registry.register(testChannel);

        assertTrue(registry.getChannel("test").isPresent());
        assertEquals("test", registry.getChannel("test").get().getName());
    }

    @Test
    void shouldUnregisterChannel() {
        var testChannel = new TestChannel("test");
        registry.register(testChannel);
        registry.unregister("test");

        assertTrue(registry.getChannel("test").isEmpty());
    }

    @Test
    void shouldTrackLastActiveChannel() {
        var telegramChannel = new TestChannel("telegram-123");
        registry.register(telegramChannel);
        registry.markActive("telegram-123");

        assertEquals("telegram-123", registry.getLastActiveChannel().getName());
    }

    @Test
    void shouldFallBackToRestWhenNoActiveChannel() {
        assertEquals("rest", registry.getLastActiveChannel().getName());
    }

    @Test
    void shouldFallBackToRestWhenActiveChannelRemoved() {
        var testChannel = new TestChannel("test");
        registry.register(testChannel);
        registry.markActive("test");
        registry.unregister("test");

        assertEquals("rest", registry.getLastActiveChannel().getName());
    }

    @Test
    void shouldSendToLastActiveChannel() {
        var testChannel = new TestChannel("test");
        registry.register(testChannel);
        registry.markActive("test");

        registry.sendToLastActive("Hello!");

        assertEquals(1, testChannel.messages.size());
        assertEquals("Hello!", testChannel.messages.getFirst());
    }

    @Test
    void shouldListAllChannels() {
        registry.register(new TestChannel("a"));
        registry.register(new TestChannel("b"));

        var channels = registry.listChannels();
        assertTrue(channels.contains("rest"));
        assertTrue(channels.contains("a"));
        assertTrue(channels.contains("b"));
    }

    @Test
    void shouldIgnoreMarkActiveForUnknownChannel() {
        registry.markActive("nonexistent");
        // Should not throw, falls back to rest
        assertEquals("rest", registry.getLastActiveChannel().getName());
    }

    /**
     * Test channel that records messages for verification.
     * Not part of the sealed hierarchy — used only in tests.
     */
    private record TestChannel(String name, List<String> messages) implements Channel {
        TestChannel(String name) {
            this(name, new ArrayList<>());
        }

        @Override
        public void sendMessage(String message) {
            messages.add(message);
        }

        @Override
        public String getName() {
            return name;
        }
    }
}

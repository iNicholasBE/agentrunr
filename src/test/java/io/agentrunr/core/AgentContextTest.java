package io.agentrunr.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentContextTest {

    @Test
    void shouldStartEmpty() {
        var context = new AgentContext();

        assertEquals("", context.get("key"));
        assertEquals("default", context.get("key", "default"));
        assertTrue(context.toMap().isEmpty());
    }

    @Test
    void shouldInitializeWithValues() {
        var context = new AgentContext(Map.of("user", "Alice", "lang", "en"));

        assertEquals("Alice", context.get("user"));
        assertEquals("en", context.get("lang"));
    }

    @Test
    void shouldSetAndGetValues() {
        var context = new AgentContext();
        context.set("session_id", "abc123");

        assertEquals("abc123", context.get("session_id"));
    }

    @Test
    void shouldMergeAdditionalVariables() {
        var context = new AgentContext(Map.of("a", "1"));
        context.merge(Map.of("b", "2", "c", "3"));

        assertEquals("1", context.get("a"));
        assertEquals("2", context.get("b"));
        assertEquals("3", context.get("c"));
    }

    @Test
    void shouldOverwriteOnMerge() {
        var context = new AgentContext(Map.of("key", "old"));
        context.merge(Map.of("key", "new"));

        assertEquals("new", context.get("key"));
    }

    @Test
    void shouldHandleNullMerge() {
        var context = new AgentContext(Map.of("key", "value"));
        context.merge(null);

        assertEquals("value", context.get("key"));
    }

    @Test
    void shouldReturnUnmodifiableMapFromToMap() {
        var context = new AgentContext(Map.of("key", "value"));
        var map = context.toMap();

        assertThrows(UnsupportedOperationException.class, () -> map.put("new", "value"));
    }

    @Test
    void shouldReturnMutableCopy() {
        var context = new AgentContext(Map.of("key", "value"));
        var copy = context.toMutableMap();
        copy.put("new", "value");

        // Original should not be affected
        assertEquals("", context.get("new"));
    }
}

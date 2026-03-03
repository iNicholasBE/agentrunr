package io.agentrunr.memory;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MemoryEntryTest {

    @Test
    void shouldCreateEntryWithDefaults() {
        var entry = new MemoryEntry("id1", "key1", "content1", MemoryCategory.CORE,
                Instant.now(), null);

        assertEquals("id1", entry.id());
        assertEquals("key1", entry.key());
        assertEquals("content1", entry.content());
        assertEquals(MemoryCategory.CORE, entry.category());
        assertEquals(0.0, entry.score());
        assertNull(entry.sessionId());
    }

    @Test
    void shouldCreateEntryWithScore() {
        var entry = new MemoryEntry("id1", "key1", "content1", MemoryCategory.DAILY,
                Instant.now(), "sess-1", 0.85);

        assertEquals(0.85, entry.score());
        assertEquals("sess-1", entry.sessionId());
    }

    @Test
    void shouldCreateCopyWithDifferentScore() {
        var entry = new MemoryEntry("id1", "key1", "content1", MemoryCategory.CORE,
                Instant.now(), null);

        var withScore = entry.withScore(0.92);
        assertEquals(0.92, withScore.score());
        assertEquals(entry.id(), withScore.id());
        assertEquals(entry.content(), withScore.content());
    }
}

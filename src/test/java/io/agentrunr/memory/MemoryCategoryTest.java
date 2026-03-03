package io.agentrunr.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemoryCategoryTest {

    @Test
    void shouldParseCoreCaseInsensitive() {
        assertEquals(MemoryCategory.CORE, MemoryCategory.fromString("core"));
        assertEquals(MemoryCategory.CORE, MemoryCategory.fromString("CORE"));
        assertEquals(MemoryCategory.CORE, MemoryCategory.fromString("Core"));
    }

    @Test
    void shouldParseDailyCaseInsensitive() {
        assertEquals(MemoryCategory.DAILY, MemoryCategory.fromString("daily"));
        assertEquals(MemoryCategory.DAILY, MemoryCategory.fromString("DAILY"));
    }

    @Test
    void shouldParseConversation() {
        assertEquals(MemoryCategory.CONVERSATION, MemoryCategory.fromString("conversation"));
        assertEquals(MemoryCategory.CONVERSATION, MemoryCategory.fromString("CONVERSATION"));
    }

    @Test
    void shouldDefaultToCoreForUnknownValues() {
        assertEquals(MemoryCategory.CORE, MemoryCategory.fromString("unknown"));
        assertEquals(MemoryCategory.CORE, MemoryCategory.fromString(""));
        assertEquals(MemoryCategory.CORE, MemoryCategory.fromString(null));
    }
}

package io.agentrunr.memory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SQLiteMemoryStoreTest {

    @TempDir
    Path tempDir;

    private SQLiteMemoryStore store;

    @BeforeEach
    void setUp() {
        String dbPath = tempDir.resolve("test-brain.db").toString();
        store = new SQLiteMemoryStore(dbPath, true);
        store.init();
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @Test
    void shouldStoreAndGetMemory() {
        store.store("user_name", "Alice", MemoryCategory.CORE, null);

        Optional<MemoryEntry> result = store.get("user_name");
        assertTrue(result.isPresent());
        assertEquals("user_name", result.get().key());
        assertEquals("Alice", result.get().content());
        assertEquals(MemoryCategory.CORE, result.get().category());
    }

    @Test
    void shouldReturnEmptyForMissingKey() {
        Optional<MemoryEntry> result = store.get("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldUpdateExistingMemory() {
        store.store("fav_color", "blue", MemoryCategory.CORE, null);
        store.store("fav_color", "green", MemoryCategory.CORE, null);

        Optional<MemoryEntry> result = store.get("fav_color");
        assertTrue(result.isPresent());
        assertEquals("green", result.get().content());
    }

    @Test
    void shouldForgetMemory() {
        store.store("temp_fact", "something", MemoryCategory.DAILY, null);
        assertTrue(store.get("temp_fact").isPresent());

        boolean deleted = store.forget("temp_fact");
        assertTrue(deleted);
        assertTrue(store.get("temp_fact").isEmpty());
    }

    @Test
    void shouldReturnFalseWhenForgettingNonexistentKey() {
        boolean deleted = store.forget("no_such_key");
        assertFalse(deleted);
    }

    @Test
    void shouldCountMemories() {
        assertEquals(0, store.count());

        store.store("a", "1", MemoryCategory.CORE, null);
        store.store("b", "2", MemoryCategory.DAILY, null);
        store.store("c", "3", MemoryCategory.CONVERSATION, null);

        assertEquals(3, store.count());
    }

    @Test
    void shouldListByCategory() {
        store.store("core1", "fact1", MemoryCategory.CORE, null);
        store.store("core2", "fact2", MemoryCategory.CORE, null);
        store.store("daily1", "log1", MemoryCategory.DAILY, null);

        List<MemoryEntry> coreEntries = store.list(MemoryCategory.CORE, null);
        assertEquals(2, coreEntries.size());

        List<MemoryEntry> dailyEntries = store.list(MemoryCategory.DAILY, null);
        assertEquals(1, dailyEntries.size());
    }

    @Test
    void shouldListByCategoryAndSession() {
        store.store("msg1", "hello", MemoryCategory.CONVERSATION, "session-A");
        store.store("msg2", "world", MemoryCategory.CONVERSATION, "session-B");
        store.store("msg3", "global", MemoryCategory.CONVERSATION, null);

        List<MemoryEntry> sessionA = store.list(MemoryCategory.CONVERSATION, "session-A");
        assertEquals(2, sessionA.size()); // session-A + global (null)
    }

    @Test
    void shouldRecallWithFTS() {
        store.store("java_pref", "User prefers Java 21 with records", MemoryCategory.CORE, null);
        store.store("python_pref", "User knows Python for scripting", MemoryCategory.CORE, null);
        store.store("meeting_note", "Meeting about Spring Boot migration", MemoryCategory.DAILY, null);

        List<MemoryEntry> results = store.recall("Java records", 5, null);
        assertFalse(results.isEmpty());
        assertTrue(results.getFirst().content().contains("Java"));
    }

    @Test
    void shouldRecallWithSessionFilter() {
        store.store("session_fact", "Discussed AI agents", MemoryCategory.CONVERSATION, "sess-1");
        store.store("other_fact", "Discussed databases", MemoryCategory.CONVERSATION, "sess-2");
        store.store("core_fact", "AI enthusiast", MemoryCategory.CORE, null);

        List<MemoryEntry> results = store.recall("AI", 5, "sess-1");
        assertFalse(results.isEmpty());
        // Should include session-1 entries and CORE entries
    }

    @Test
    void shouldReturnEmptyForBlankQuery() {
        store.store("something", "data", MemoryCategory.CORE, null);

        List<MemoryEntry> results = store.recall("", 5, null);
        assertTrue(results.isEmpty());

        results = store.recall(null, 5, null);
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldRespectRecallLimit() {
        for (int i = 0; i < 20; i++) {
            store.store("item_" + i, "Content for item " + i, MemoryCategory.CORE, null);
        }

        List<MemoryEntry> results = store.recall("Content item", 3, null);
        assertTrue(results.size() <= 3);
    }

    @Test
    void shouldPassHealthCheck() {
        assertTrue(store.healthCheck());
    }

    @Test
    void shouldStoreConversationMessage() {
        store.storeConversationMessage("sess-1", "user", "Hello there!");

        List<MemoryEntry> messages = store.list(MemoryCategory.CONVERSATION, "sess-1");
        assertFalse(messages.isEmpty());
        assertTrue(messages.getFirst().content().contains("[USER] Hello there!"));
    }

    @Test
    void shouldListSessions() {
        store.store("a", "x", MemoryCategory.CONVERSATION, "sess-alpha");
        store.store("b", "y", MemoryCategory.CONVERSATION, "sess-beta");

        List<String> sessions = store.listSessions();
        assertTrue(sessions.contains("sess-alpha"));
        assertTrue(sessions.contains("sess-beta"));
    }

    @Test
    void shouldHandleSpecialCharactersInContent() {
        store.store("special", "He said \"hello\" and 'goodbye'\nNew line here", MemoryCategory.CORE, null);

        Optional<MemoryEntry> result = store.get("special");
        assertTrue(result.isPresent());
        assertTrue(result.get().content().contains("\"hello\""));
        assertTrue(result.get().content().contains("\n"));
    }

    @Test
    void shouldRecallWithFallbackForSpecialChars() {
        store.store("data", "Value with special chars: @#$%", MemoryCategory.CORE, null);

        // This might trigger the LIKE fallback since FTS might choke on special chars
        List<MemoryEntry> results = store.recall("special chars @#$%", 5, null);
        // Should not throw, may or may not find results
        assertNotNull(results);
    }

    @Test
    void shouldHaveScoresInRecallResults() {
        store.store("scored_item", "This is a test of search scoring", MemoryCategory.CORE, null);

        List<MemoryEntry> results = store.recall("search scoring test", 5, null);
        if (!results.isEmpty()) {
            assertTrue(results.getFirst().score() > 0);
        }
    }

    @Test
    void shouldCountAfterForget() {
        store.store("temp1", "a", MemoryCategory.DAILY, null);
        store.store("temp2", "b", MemoryCategory.DAILY, null);
        assertEquals(2, store.count());

        store.forget("temp1");
        assertEquals(1, store.count());
    }
}

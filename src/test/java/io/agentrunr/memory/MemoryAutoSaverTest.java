package io.agentrunr.memory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MemoryAutoSaverTest {

    @TempDir
    Path tempDir;

    private SQLiteMemoryStore memoryStore;
    private MemoryAutoSaver autoSaver;

    @BeforeEach
    void setUp() {
        String dbPath = tempDir.resolve("autosave-test.db").toString();
        memoryStore = new SQLiteMemoryStore(dbPath, true);
        memoryStore.init();
        autoSaver = new MemoryAutoSaver(memoryStore);
    }

    @AfterEach
    void tearDown() {
        memoryStore.close();
    }

    @Test
    void shouldDetectUserName() {
        int saved = autoSaver.scanAndSave("My name is Alice", null);
        assertTrue(saved > 0);
        assertTrue(memoryStore.get("user_name").isPresent());
        assertEquals("Alice", memoryStore.get("user_name").get().content());
    }

    @Test
    void shouldDetectPreference() {
        int saved = autoSaver.scanAndSave("I prefer dark mode for all my editors", null);
        assertTrue(saved > 0);
    }

    @Test
    void shouldDetectWorkplace() {
        int saved = autoSaver.scanAndSave("I work at Google on the search team", null);
        assertTrue(saved > 0);
        assertTrue(memoryStore.get("workplace").isPresent());
    }

    @Test
    void shouldDetectLocation() {
        int saved = autoSaver.scanAndSave("I live in Amsterdam", null);
        assertTrue(saved > 0);
        assertTrue(memoryStore.get("location").isPresent());
    }

    @Test
    void shouldDetectRememberRequest() {
        assertTrue(autoSaver.containsRememberRequest("Remember that I like Java"));
        assertTrue(autoSaver.containsRememberRequest("Don't forget my birthday is June 5th"));
        assertTrue(autoSaver.containsRememberRequest("Keep in mind I'm left-handed"));
    }

    @Test
    void shouldNotDetectRememberInNormalMessage() {
        assertFalse(autoSaver.containsRememberRequest("What is Java?"));
        assertFalse(autoSaver.containsRememberRequest("Hello world"));
    }

    @Test
    void shouldSaveRememberInstruction() {
        int saved = autoSaver.scanAndSave("Remember that I always use Java 21", null);
        assertTrue(saved > 0);
    }

    @Test
    void shouldNotSaveShortMessages() {
        int saved = autoSaver.scanAndSave("Hi", null);
        assertEquals(0, saved);
    }

    @Test
    void shouldNotSaveNull() {
        int saved = autoSaver.scanAndSave(null, null);
        assertEquals(0, saved);
    }

    @Test
    void shouldNotDuplicateExistingFact() {
        autoSaver.scanAndSave("My name is Alice", null);
        int before = memoryStore.count();

        autoSaver.scanAndSave("My name is Alice", null);
        int after = memoryStore.count();

        assertEquals(before, after);
    }

    @Test
    void shouldDetectAlwaysNeverPatterns() {
        int saved = autoSaver.scanAndSave("Always use tabs for indentation", null);
        assertTrue(saved > 0);
    }
}

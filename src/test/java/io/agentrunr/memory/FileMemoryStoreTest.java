package io.agentrunr.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileMemoryStoreTest {

    @TempDir
    Path tempDir;

    private FileMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new FileMemoryStore(tempDir.toString());
    }

    @Test
    void shouldCreateDirectoryStructure() {
        assertTrue(Files.isDirectory(tempDir.resolve("sessions")));
    }

    @Test
    void shouldAppendMessageToDailyLog() throws IOException {
        store.appendMessage("session-1", "user", "Hello!");

        Path sessionDir = tempDir.resolve("sessions/session-1");
        assertTrue(Files.isDirectory(sessionDir));

        String date = LocalDate.now().toString();
        Path logFile = sessionDir.resolve(date + ".md");
        assertTrue(Files.exists(logFile));

        String content = Files.readString(logFile);
        assertTrue(content.contains("Session: session-1"));
        assertTrue(content.contains("USER"));
        assertTrue(content.contains("Hello!"));
    }

    @Test
    void shouldAppendMultipleMessages() throws IOException {
        store.appendMessage("session-2", "user", "Hi");
        store.appendMessage("session-2", "assistant", "Hello there!");
        store.appendMessage("session-2", "user", "How are you?");

        String date = LocalDate.now().toString();
        Path logFile = tempDir.resolve("sessions/session-2/" + date + ".md");
        String content = Files.readString(logFile);

        assertTrue(content.contains("USER"));
        assertTrue(content.contains("ASSISTANT"));
        assertTrue(content.contains("Hi"));
        assertTrue(content.contains("Hello there!"));
        assertTrue(content.contains("How are you?"));
    }

    @Test
    void shouldSaveAndLoadContext() {
        store.saveContext("session-3", Map.of("user", "Alice", "lang", "en"));

        Map<String, String> loaded = store.loadContext("session-3");
        assertEquals("Alice", loaded.get("user"));
        assertEquals("en", loaded.get("lang"));
    }

    @Test
    void shouldReturnEmptyContextForNewSession() {
        Map<String, String> loaded = store.loadContext("nonexistent");
        assertTrue(loaded.isEmpty());
    }

    @Test
    void shouldReadAndWriteLongTermMemory() {
        store.appendLongTermMemory("## Important fact\nJobRunr is awesome.");

        String memory = store.readLongTermMemory();
        assertTrue(memory.contains("Long-Term Memory"));
        assertTrue(memory.contains("JobRunr is awesome"));
    }

    @Test
    void shouldListSessions() {
        store.appendMessage("alpha", "user", "Hello");
        store.appendMessage("beta", "user", "World");

        var sessions = store.listSessions();
        assertTrue(sessions.contains("alpha"));
        assertTrue(sessions.contains("beta"));
    }

    @Test
    void shouldHandleSpecialCharactersInContext() {
        store.saveContext("session-4", Map.of("note", "He said \"hello\" to me\nNew line"));

        Map<String, String> loaded = store.loadContext("session-4");
        assertEquals("He said \"hello\" to me\nNew line", loaded.get("note"));
    }
}

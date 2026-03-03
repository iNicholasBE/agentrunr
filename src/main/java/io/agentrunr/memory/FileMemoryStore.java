package io.agentrunr.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * File-based memory store inspired by nanobot/PicoClaw's MEMORY.md pattern.
 *
 * <p>Stores conversation history and context per session in markdown files.
 * Each session gets a daily file, and there's a persistent long-term memory file.</p>
 *
 * <p>Directory structure:</p>
 * <pre>
 * {memory.path}/
 *   MEMORY.md              — long-term curated memories
 *   sessions/
 *     {sessionId}/
 *       2026-02-28.md      — daily conversation log
 *       context.json       — persistent context variables
 * </pre>
 */
@Component
public class FileMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(FileMemoryStore.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Path basePath;

    public FileMemoryStore(@Value("${memory.path:./data/memory}") String memoryPath) {
        this.basePath = Path.of(memoryPath);
        ensureDirectory(basePath);
        ensureDirectory(basePath.resolve("sessions"));
        log.info("FileMemoryStore initialized at: {}", basePath.toAbsolutePath());
    }

    /**
     * Appends a conversation entry to the session's daily log.
     *
     * @param sessionId the session identifier
     * @param role      message role (user, assistant, tool)
     * @param content   message content
     */
    public void appendMessage(String sessionId, String role, String content) {
        Path sessionDir = basePath.resolve("sessions").resolve(sessionId);
        ensureDirectory(sessionDir);

        String date = LocalDate.now().format(DATE_FMT);
        String time = LocalDateTime.now().format(TIME_FMT);
        Path dailyLog = sessionDir.resolve(date + ".md");

        String entry = String.format("\n### [%s] %s\n%s\n", time, role.toUpperCase(), content);

        try {
            if (!Files.exists(dailyLog)) {
                Files.writeString(dailyLog, "# Session: " + sessionId + " — " + date + "\n");
            }
            Files.writeString(dailyLog, entry, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to write to memory log: {}", dailyLog, e);
        }
    }

    /**
     * Saves context variables for a session.
     */
    public void saveContext(String sessionId, Map<String, String> context) {
        Path contextFile = basePath.resolve("sessions").resolve(sessionId).resolve("context.json");
        ensureDirectory(contextFile.getParent());

        try {
            StringBuilder json = new StringBuilder("{\n");
            var entries = new ArrayList<>(context.entrySet());
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                json.append(String.format("  \"%s\": \"%s\"", escapeJson(entry.getKey()), escapeJson(entry.getValue())));
                if (i < entries.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("}\n");
            Files.writeString(contextFile, json.toString());
        } catch (IOException e) {
            log.error("Failed to save context: {}", contextFile, e);
        }
    }

    /**
     * Loads context variables for a session, or empty map if none.
     */
    public Map<String, String> loadContext(String sessionId) {
        Path contextFile = basePath.resolve("sessions").resolve(sessionId).resolve("context.json");
        if (!Files.exists(contextFile)) {
            return new HashMap<>();
        }
        try {
            String content = Files.readString(contextFile);
            return parseSimpleJson(content);
        } catch (IOException e) {
            log.error("Failed to load context: {}", contextFile, e);
            return new HashMap<>();
        }
    }

    /**
     * Reads the long-term memory file.
     */
    public String readLongTermMemory() {
        Path memoryFile = basePath.resolve("MEMORY.md");
        if (!Files.exists(memoryFile)) {
            return "";
        }
        try {
            return Files.readString(memoryFile);
        } catch (IOException e) {
            log.error("Failed to read MEMORY.md", e);
            return "";
        }
    }

    /**
     * Appends to the long-term memory file.
     */
    public void appendLongTermMemory(String content) {
        Path memoryFile = basePath.resolve("MEMORY.md");
        try {
            if (!Files.exists(memoryFile)) {
                Files.writeString(memoryFile, "# Long-Term Memory\n\n");
            }
            Files.writeString(memoryFile, content + "\n", StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to write MEMORY.md", e);
        }
    }

    /**
     * Lists all session IDs.
     */
    public List<String> listSessions() {
        Path sessionsDir = basePath.resolve("sessions");
        if (!Files.exists(sessionsDir)) return List.of();
        try (Stream<Path> paths = Files.list(sessionsDir)) {
            return paths.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.error("Failed to list sessions", e);
            return List.of();
        }
    }

    private void ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("Failed to create directory: {}", dir, e);
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    /**
     * Simple JSON parser for flat string maps (no external dependency).
     */
    private Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new HashMap<>();
        String[] lines = json.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("\"")) {
                int keyEnd = line.indexOf("\"", 1);
                if (keyEnd < 0) continue;
                String key = line.substring(1, keyEnd);
                int valStart = line.indexOf("\"", keyEnd + 2);
                if (valStart < 0) continue;
                int valEnd = line.lastIndexOf("\"");
                if (valEnd <= valStart) continue;
                String value = line.substring(valStart + 1, valEnd);
                map.put(key, value.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\"));
            }
        }
        return map;
    }
}

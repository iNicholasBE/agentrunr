package io.agentrunr.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * SQLite-backed memory store with FTS5 full-text search.
 * Inspired by ZeroClaw's SQLite memory backend.
 *
 * <p>Schema:</p>
 * <ul>
 *   <li>{@code memories} — main table with key, content, category, session_id, timestamps</li>
 *   <li>{@code memories_fts} — FTS5 virtual table for keyword search</li>
 * </ul>
 */
@Component
public class SQLiteMemoryStore implements Memory {

    private static final Logger log = LoggerFactory.getLogger(SQLiteMemoryStore.class);

    private final String dbPath;
    private Connection connection;

    @Autowired
    public SQLiteMemoryStore(@Value("${memory.path:./data/memory}") String memoryPath) {
        Path dir = Path.of(memoryPath);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("Failed to create memory directory: {}", dir, e);
        }
        this.dbPath = dir.resolve("brain.db").toString();
    }

    /** Constructor for testing with explicit db path. */
    public SQLiteMemoryStore(String dbPath, boolean isDirect) {
        this.dbPath = dbPath;
    }

    @PostConstruct
    public void init() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            // Enable WAL mode for better concurrent performance
            try (var stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA busy_timeout=5000");
            }
            createSchema();
            log.info("SQLiteMemoryStore initialized at: {}", dbPath);
        } catch (SQLException e) {
            log.error("Failed to initialize SQLite memory store at {}", dbPath, e);
            throw new RuntimeException("Memory store initialization failed", e);
        }
    }

    private void createSchema() throws SQLException {
        try (var stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS memories (
                    id TEXT PRIMARY KEY,
                    key TEXT NOT NULL,
                    content TEXT NOT NULL,
                    category TEXT NOT NULL DEFAULT 'CORE',
                    session_id TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_memories_key ON memories(key)
                """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_memories_category ON memories(category)
                """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_memories_session ON memories(session_id)
                """);

            // FTS5 virtual table for full-text search
            stmt.execute("""
                CREATE VIRTUAL TABLE IF NOT EXISTS memories_fts USING fts5(
                    key,
                    content,
                    content=memories,
                    content_rowid=rowid
                )
                """);

            // Triggers to keep FTS in sync
            stmt.execute("""
                CREATE TRIGGER IF NOT EXISTS memories_ai AFTER INSERT ON memories BEGIN
                    INSERT INTO memories_fts(rowid, key, content)
                    VALUES (new.rowid, new.key, new.content);
                END
                """);

            stmt.execute("""
                CREATE TRIGGER IF NOT EXISTS memories_ad AFTER DELETE ON memories BEGIN
                    INSERT INTO memories_fts(memories_fts, rowid, key, content)
                    VALUES ('delete', old.rowid, old.key, old.content);
                END
                """);

            stmt.execute("""
                CREATE TRIGGER IF NOT EXISTS memories_au AFTER UPDATE ON memories BEGIN
                    INSERT INTO memories_fts(memories_fts, rowid, key, content)
                    VALUES ('delete', old.rowid, old.key, old.content);
                    INSERT INTO memories_fts(rowid, key, content)
                    VALUES (new.rowid, new.key, new.content);
                END
                """);
        }
    }

    @Override
    public void store(String key, String content, MemoryCategory category, String sessionId) {
        String now = Instant.now().toString();
        String id = UUID.randomUUID().toString();

        // Upsert: if key already exists, update content
        String sql = """
            INSERT INTO memories (id, key, content, category, session_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET content = excluded.content, updated_at = excluded.updated_at
            """;

        // Check if key exists first for proper upsert
        try {
            Optional<MemoryEntry> existing = get(key);
            if (existing.isPresent()) {
                // Update existing
                try (var stmt = connection.prepareStatement(
                        "UPDATE memories SET content = ?, category = ?, updated_at = ? WHERE key = ?")) {
                    stmt.setString(1, content);
                    stmt.setString(2, category.name());
                    stmt.setString(3, now);
                    stmt.setString(4, key);
                    stmt.executeUpdate();
                }
            } else {
                // Insert new
                try (var stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, id);
                    stmt.setString(2, key);
                    stmt.setString(3, content);
                    stmt.setString(4, category.name());
                    stmt.setString(5, sessionId);
                    stmt.setString(6, now);
                    stmt.setString(7, now);
                    stmt.executeUpdate();
                }
            }
            log.debug("Stored memory: key='{}', category={}", key, category);
        } catch (SQLException e) {
            log.error("Failed to store memory: key='{}'", key, e);
        }
    }

    @Override
    public List<MemoryEntry> recall(String query, int limit, String sessionId) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<MemoryEntry> results = new ArrayList<>();

        // Use FTS5 for search with BM25 ranking
        String ftsQuery = buildFtsQuery(query);

        String sql;
        if (sessionId != null) {
            sql = """
                SELECT m.id, m.key, m.content, m.category, m.created_at, m.session_id,
                       bm25(memories_fts) AS rank
                FROM memories_fts f
                JOIN memories m ON m.rowid = f.rowid
                WHERE memories_fts MATCH ?
                  AND (m.session_id = ? OR m.session_id IS NULL OR m.category = 'CORE')
                ORDER BY rank
                LIMIT ?
                """;
        } else {
            sql = """
                SELECT m.id, m.key, m.content, m.category, m.created_at, m.session_id,
                       bm25(memories_fts) AS rank
                FROM memories_fts f
                JOIN memories m ON m.rowid = f.rowid
                WHERE memories_fts MATCH ?
                ORDER BY rank
                LIMIT ?
                """;
        }

        try (var stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, ftsQuery);
            if (sessionId != null) {
                stmt.setString(2, sessionId);
                stmt.setInt(3, limit);
            } else {
                stmt.setInt(2, limit);
            }

            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    double rank = Math.abs(rs.getDouble("rank"));
                    // Normalize BM25 score to 0-1 range (BM25 returns negative values, lower = better)
                    double score = 1.0 / (1.0 + rank);
                    results.add(toEntry(rs).withScore(score));
                }
            }
        } catch (SQLException e) {
            log.debug("FTS search failed, falling back to LIKE search: {}", e.getMessage());
            return recallFallback(query, limit, sessionId);
        }

        return results;
    }

    /** Fallback search using LIKE when FTS fails (e.g., syntax errors in query). */
    private List<MemoryEntry> recallFallback(String query, int limit, String sessionId) {
        List<MemoryEntry> results = new ArrayList<>();
        String likePattern = "%" + query.replace("%", "").replace("_", "") + "%";

        String sql;
        if (sessionId != null) {
            sql = """
                SELECT id, key, content, category, created_at, session_id
                FROM memories
                WHERE (key LIKE ? OR content LIKE ?)
                  AND (session_id = ? OR session_id IS NULL OR category = 'CORE')
                ORDER BY updated_at DESC
                LIMIT ?
                """;
        } else {
            sql = """
                SELECT id, key, content, category, created_at, session_id
                FROM memories
                WHERE (key LIKE ? OR content LIKE ?)
                ORDER BY updated_at DESC
                LIMIT ?
                """;
        }

        try (var stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, likePattern);
            stmt.setString(2, likePattern);
            if (sessionId != null) {
                stmt.setString(3, sessionId);
                stmt.setInt(4, limit);
            } else {
                stmt.setInt(3, limit);
            }

            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(toEntry(rs).withScore(0.5));
                }
            }
        } catch (SQLException e) {
            log.error("Fallback search also failed", e);
        }

        return results;
    }

    @Override
    public Optional<MemoryEntry> get(String key) {
        String sql = "SELECT id, key, content, category, created_at, session_id FROM memories WHERE key = ?";

        try (var stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, key);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(toEntry(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get memory: key='{}'", key, e);
        }

        return Optional.empty();
    }

    @Override
    public List<MemoryEntry> list(MemoryCategory category, String sessionId) {
        List<MemoryEntry> results = new ArrayList<>();

        String sql;
        if (sessionId != null) {
            sql = """
                SELECT id, key, content, category, created_at, session_id
                FROM memories
                WHERE category = ? AND (session_id = ? OR session_id IS NULL)
                ORDER BY updated_at DESC
                """;
        } else {
            sql = """
                SELECT id, key, content, category, created_at, session_id
                FROM memories
                WHERE category = ?
                ORDER BY updated_at DESC
                """;
        }

        try (var stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, category.name());
            if (sessionId != null) {
                stmt.setString(2, sessionId);
            }
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(toEntry(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to list memories for category={}", category, e);
        }

        return results;
    }

    @Override
    public boolean forget(String key) {
        String sql = "DELETE FROM memories WHERE key = ?";

        try (var stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, key);
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                log.debug("Forgot memory: key='{}'", key);
                return true;
            }
        } catch (SQLException e) {
            log.error("Failed to forget memory: key='{}'", key, e);
        }

        return false;
    }

    @Override
    public int count() {
        try (var stmt = connection.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM memories")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            log.error("Failed to count memories", e);
        }
        return 0;
    }

    @Override
    public boolean healthCheck() {
        try (var stmt = connection.createStatement();
             var rs = stmt.executeQuery("SELECT 1")) {
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Lists all distinct session IDs in the memory store.
     */
    public List<String> listSessions() {
        List<String> sessions = new ArrayList<>();
        try (var stmt = connection.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT DISTINCT session_id FROM memories WHERE session_id IS NOT NULL ORDER BY session_id")) {
            while (rs.next()) {
                sessions.add(rs.getString(1));
            }
        } catch (SQLException e) {
            log.error("Failed to list sessions", e);
        }
        return sessions;
    }

    /**
     * Stores a conversation message as a CONVERSATION memory.
     */
    public void storeConversationMessage(String sessionId, String role, String content) {
        String key = "msg_" + System.currentTimeMillis() + "_" + role.toLowerCase();
        store(key, "[%s] %s".formatted(role.toUpperCase(), content), MemoryCategory.CONVERSATION, sessionId);
    }

    @PreDestroy
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                log.info("SQLiteMemoryStore closed");
            } catch (SQLException e) {
                log.error("Failed to close SQLite connection", e);
            }
        }
    }

    /** Builds a safe FTS5 query from user input. */
    private String buildFtsQuery(String query) {
        // Escape special FTS5 characters and build OR-style query for each word
        String[] words = query.trim().split("\\s+");
        StringJoiner fts = new StringJoiner(" OR ");
        for (String word : words) {
            // Remove FTS special chars
            String clean = word.replaceAll("[\"'*(){}\\[\\]^~:+\\-]", "").trim();
            if (!clean.isEmpty()) {
                // Use prefix matching for better recall
                fts.add("\"" + clean + "\"*");
            }
        }
        String result = fts.toString();
        return result.isEmpty() ? "\"" + query.replaceAll("[\"']", "") + "\"" : result;
    }

    private MemoryEntry toEntry(ResultSet rs) throws SQLException {
        return new MemoryEntry(
                rs.getString("id"),
                rs.getString("key"),
                rs.getString("content"),
                MemoryCategory.fromString(rs.getString("category")),
                Instant.parse(rs.getString("created_at")),
                rs.getString("session_id")
        );
    }
}

package io.agentrunr.memory;

import java.time.Instant;

/**
 * A single memory entry stored in the memory system.
 *
 * @param id        unique identifier
 * @param key       user-facing key / label
 * @param content   the actual memory content
 * @param category  memory category (CORE, DAILY, CONVERSATION)
 * @param timestamp when the memory was created
 * @param sessionId optional session scope (null for global memories)
 * @param score     search relevance score (0.0-1.0), set during recall
 */
public record MemoryEntry(
        String id,
        String key,
        String content,
        MemoryCategory category,
        Instant timestamp,
        String sessionId,
        double score
) {
    public MemoryEntry(String id, String key, String content, MemoryCategory category, Instant timestamp, String sessionId) {
        this(id, key, content, category, timestamp, sessionId, 0.0);
    }

    public MemoryEntry withScore(double score) {
        return new MemoryEntry(id, key, content, category, timestamp, sessionId, score);
    }
}

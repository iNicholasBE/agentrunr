package io.agentrunr.memory;

import java.util.List;
import java.util.Optional;

/**
 * Memory interface for the agent's persistent memory system.
 * Inspired by ZeroClaw's Memory trait — provides store/recall/get/list/forget/count.
 */
public interface Memory {

    /**
     * Stores a memory entry.
     *
     * @param key       user-facing key/label
     * @param content   the memory content
     * @param category  memory category
     * @param sessionId optional session scope (null for global)
     */
    void store(String key, String content, MemoryCategory category, String sessionId);

    /**
     * Recalls memories matching a search query, ranked by relevance.
     * Uses FTS5 full-text search when available.
     *
     * @param query     search query
     * @param limit     max number of results
     * @param sessionId optional session filter (null for all)
     * @return ranked list of matching memories
     */
    List<MemoryEntry> recall(String query, int limit, String sessionId);

    /**
     * Gets a specific memory by its key.
     *
     * @param key the memory key
     * @return the memory entry, or empty
     */
    Optional<MemoryEntry> get(String key);

    /**
     * Lists all memories in a category, optionally filtered by session.
     *
     * @param category  the category to list
     * @param sessionId optional session filter (null for all)
     * @return list of memories in the category
     */
    List<MemoryEntry> list(MemoryCategory category, String sessionId);

    /**
     * Deletes a memory by key.
     *
     * @param key the memory key to forget
     * @return true if the memory was found and deleted
     */
    boolean forget(String key);

    /**
     * Returns the total number of stored memories.
     */
    int count();

    /**
     * Health check — verifies the memory store is operational.
     */
    boolean healthCheck();
}

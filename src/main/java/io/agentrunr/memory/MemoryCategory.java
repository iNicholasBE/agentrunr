package io.agentrunr.memory;

/**
 * Categories for memory entries, inspired by ZeroClaw's memory system.
 *
 * <ul>
 *   <li>{@code CORE} — Long-term facts, preferences, decisions. Persists across sessions.</li>
 *   <li>{@code DAILY} — Session-specific logs, timestamped. Expires after retention period.</li>
 *   <li>{@code CONVERSATION} — Chat context within a session. Ephemeral.</li>
 * </ul>
 */
public enum MemoryCategory {
    CORE,
    DAILY,
    CONVERSATION;

    public static MemoryCategory fromString(String s) {
        if (s == null || s.isBlank()) return CORE;
        return switch (s.toLowerCase()) {
            case "core" -> CORE;
            case "daily" -> DAILY;
            case "conversation" -> CONVERSATION;
            default -> CORE;
        };
    }
}

package io.agentrunr.tool;

import io.agentrunr.core.AgentContext;
import io.agentrunr.core.AgentResult;
import io.agentrunr.core.ToolRegistry;
import io.agentrunr.memory.Memory;
import io.agentrunr.memory.MemoryCategory;
import io.agentrunr.memory.MemoryEntry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Agent-callable tools for interacting with the memory system.
 * These tools allow the agent to store, recall, forget, and list memories.
 */
@Component
public class MemoryTools {

    private static final Logger log = LoggerFactory.getLogger(MemoryTools.class);

    private final ToolRegistry toolRegistry;
    private final Memory memory;

    public MemoryTools(ToolRegistry toolRegistry, Memory memory) {
        this.toolRegistry = toolRegistry;
        this.memory = memory;
    }

    @PostConstruct
    public void registerTools() {
        toolRegistry.registerAgentTool("memory_store",
                "Store a fact or piece of information in long-term memory. Use for things the user wants remembered.",
                """
                {"type":"object","properties":{"key":{"type":"string","description":"Unique key for this memory"},"content":{"type":"string","description":"The content to remember"},"category":{"type":"string","enum":["CORE","DAILY","CONVERSATION"],"description":"Memory category (default: CORE)"}},"required":["key","content"]}""",
                this::memoryStore);
        toolRegistry.registerAgentTool("memory_recall",
                "Search memory for relevant information. Returns ranked results matching the query.",
                """
                {"type":"object","properties":{"query":{"type":"string","description":"Search query"},"limit":{"type":"integer","description":"Max results (default: 5)"}},"required":["query"]}""",
                this::memoryRecall);
        toolRegistry.registerAgentTool("memory_forget",
                "Remove a memory entry by its key.",
                """
                {"type":"object","properties":{"key":{"type":"string","description":"The memory key to forget"}},"required":["key"]}""",
                this::memoryForget);
        toolRegistry.registerAgentTool("memory_list",
                "List all memories in a category.",
                """
                {"type":"object","properties":{"category":{"type":"string","enum":["CORE","DAILY","CONVERSATION"],"description":"Category to list (default: CORE)"}},"required":[]}""",
                this::memoryList);
        log.info("Registered 4 memory tools");
    }

    /**
     * Stores a memory entry.
     * Args: key (string, required), content (string, required), category (string, optional: core/daily/conversation)
     */
    private AgentResult memoryStore(Map<String, Object> args, AgentContext ctx) {
        String key = stringArg(args, "key", "");
        String content = stringArg(args, "content", "");
        String categoryStr = stringArg(args, "category", "core");

        if (key.isBlank()) {
            return AgentResult.of("Error: 'key' is required.");
        }
        if (content.isBlank()) {
            return AgentResult.of("Error: 'content' is required.");
        }

        MemoryCategory category = MemoryCategory.fromString(categoryStr);
        String sessionId = ctx.get("session_id", null);

        memory.store(key, content, category, sessionId);
        return AgentResult.of("Stored memory: [%s] %s".formatted(category, key));
    }

    /**
     * Recalls memories matching a search query.
     * Args: query (string, required), limit (int, optional, default 5)
     */
    private AgentResult memoryRecall(Map<String, Object> args, AgentContext ctx) {
        String query = stringArg(args, "query", "");
        int limit = intArg(args, "limit", 5);

        if (query.isBlank()) {
            return AgentResult.of("Error: 'query' is required.");
        }

        String sessionId = ctx.get("session_id", null);
        List<MemoryEntry> results = memory.recall(query, limit, sessionId);

        if (results.isEmpty()) {
            return AgentResult.of("No memories found matching: " + query);
        }

        StringJoiner output = new StringJoiner("\n");
        output.add("Found %d memories:".formatted(results.size()));
        for (MemoryEntry entry : results) {
            int scorePercent = (int) (entry.score() * 100);
            output.add("- [%s] %s: %s [%d%%]".formatted(
                    entry.category(), entry.key(), truncate(entry.content(), 200), scorePercent));
        }
        return AgentResult.of(output.toString());
    }

    /**
     * Forgets (deletes) a memory by key.
     * Args: key (string, required)
     */
    private AgentResult memoryForget(Map<String, Object> args, AgentContext ctx) {
        String key = stringArg(args, "key", "");

        if (key.isBlank()) {
            return AgentResult.of("Error: 'key' is required.");
        }

        boolean deleted = memory.forget(key);
        return deleted
                ? AgentResult.of("Forgot memory: " + key)
                : AgentResult.of("Memory not found: " + key);
    }

    /**
     * Lists memories by category.
     * Args: category (string, optional: core/daily/conversation, default core)
     */
    private AgentResult memoryList(Map<String, Object> args, AgentContext ctx) {
        String categoryStr = stringArg(args, "category", "core");
        MemoryCategory category = MemoryCategory.fromString(categoryStr);
        String sessionId = ctx.get("session_id", null);

        List<MemoryEntry> entries = memory.list(category, sessionId);

        if (entries.isEmpty()) {
            return AgentResult.of("No memories in category: " + category);
        }

        StringJoiner output = new StringJoiner("\n");
        output.add("%d memories in [%s]:".formatted(entries.size(), category));
        for (MemoryEntry entry : entries) {
            output.add("- %s: %s".formatted(entry.key(), truncate(entry.content(), 150)));
        }
        return AgentResult.of(output.toString());
    }

    private String stringArg(Map<String, Object> args, String key, String defaultValue) {
        Object val = args.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private int intArg(Map<String, Object> args, String key, int defaultValue) {
        Object val = args.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { /* fall through */ }
        }
        return defaultValue;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}

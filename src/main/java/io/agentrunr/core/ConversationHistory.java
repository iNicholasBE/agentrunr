package io.agentrunr.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Manages conversation history with auto-compaction.
 * Inspired by ZeroClaw's history trimming — keeps the most recent N messages,
 * summarizes older turns into a compaction summary.
 *
 * <p>Key behaviors:</p>
 * <ul>
 *   <li>Preserves the system message (always first)</li>
 *   <li>Keeps the most recent {@code maxMessages} non-system messages</li>
 *   <li>Never splits tool_call/tool_result pairs across boundaries</li>
 *   <li>When trimming, generates a summary of dropped messages</li>
 * </ul>
 */
public class ConversationHistory {

    private static final Logger log = LoggerFactory.getLogger(ConversationHistory.class);
    private static final int DEFAULT_MAX_MESSAGES = 50;

    private final List<ChatMessage> messages;
    private final int maxMessages;

    public ConversationHistory() {
        this(DEFAULT_MAX_MESSAGES);
    }

    public ConversationHistory(int maxMessages) {
        this.messages = new ArrayList<>();
        this.maxMessages = maxMessages;
    }

    public ConversationHistory(List<ChatMessage> initial) {
        this(DEFAULT_MAX_MESSAGES);
        this.messages.addAll(initial);
    }

    public ConversationHistory(List<ChatMessage> initial, int maxMessages) {
        this.messages = new ArrayList<>(initial);
        this.maxMessages = maxMessages;
    }

    public void add(ChatMessage message) {
        messages.add(message);
        compactIfNeeded();
    }

    public void addAll(List<ChatMessage> newMessages) {
        messages.addAll(newMessages);
        compactIfNeeded();
    }

    public List<ChatMessage> getMessages() {
        return List.copyOf(messages);
    }

    public int size() {
        return messages.size();
    }

    public ChatMessage lastMessage() {
        if (messages.isEmpty()) return null;
        return messages.getLast();
    }

    public String lastAssistantContent() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (msg.role() == ChatMessage.Role.ASSISTANT && msg.content() != null) {
                return msg.content();
            }
        }
        return "";
    }

    /**
     * Compacts the history if it exceeds the max message limit.
     * Preserves system messages, keeps the most recent messages,
     * and summarizes dropped messages into a compaction note.
     */
    private void compactIfNeeded() {
        int nonSystemCount = (int) messages.stream()
                .filter(m -> m.role() != ChatMessage.Role.SYSTEM)
                .count();

        if (nonSystemCount <= maxMessages) {
            return;
        }

        log.debug("Compacting conversation history: {} messages -> {}", messages.size(), maxMessages);

        // Separate system messages from conversation messages
        List<ChatMessage> systemMessages = new ArrayList<>();
        List<ChatMessage> conversationMessages = new ArrayList<>();

        for (ChatMessage msg : messages) {
            if (msg.role() == ChatMessage.Role.SYSTEM) {
                systemMessages.add(msg);
            } else {
                conversationMessages.add(msg);
            }
        }

        // Keep the most recent maxMessages conversation messages
        int dropCount = conversationMessages.size() - maxMessages;
        if (dropCount <= 0) return;

        // Find a safe split point — don't orphan tool results
        int splitIndex = findSafeSplitPoint(conversationMessages, dropCount);

        List<ChatMessage> dropped = conversationMessages.subList(0, splitIndex);
        List<ChatMessage> kept = conversationMessages.subList(splitIndex, conversationMessages.size());

        // Create a compaction summary
        String summary = createCompactionSummary(dropped);

        // Rebuild messages
        messages.clear();
        messages.addAll(systemMessages);
        if (!summary.isBlank()) {
            messages.add(ChatMessage.system("[Conversation history compacted — %d earlier messages summarized]\n%s".formatted(dropped.size(), summary)));
        }
        messages.addAll(kept);

        log.debug("Compacted: dropped {} messages, kept {}", dropped.size(), kept.size());
    }

    /**
     * Finds a safe index to split at — ensures we don't split assistant+tool_result pairs.
     */
    private int findSafeSplitPoint(List<ChatMessage> messages, int targetIndex) {
        int index = Math.min(targetIndex, messages.size());

        // Walk forward from target to find a safe boundary
        while (index < messages.size()) {
            ChatMessage msg = messages.get(index);
            // Don't split at a TOOL message (keep it with its preceding ASSISTANT)
            if (msg.role() == ChatMessage.Role.TOOL) {
                index++;
            } else {
                break;
            }
        }

        return Math.min(index, messages.size());
    }

    /**
     * Creates a deterministic summary of dropped messages.
     * Extracts key information without needing an LLM call.
     */
    private String createCompactionSummary(List<ChatMessage> dropped) {
        StringJoiner summary = new StringJoiner("\n");
        int userCount = 0;
        int assistantCount = 0;
        int toolCount = 0;

        StringBuilder keyTopics = new StringBuilder();

        for (ChatMessage msg : dropped) {
            switch (msg.role()) {
                case USER -> {
                    userCount++;
                    // Extract first line as topic hint
                    String firstLine = msg.content().lines().findFirst().orElse("");
                    if (!firstLine.isBlank() && firstLine.length() < 200) {
                        if (!keyTopics.isEmpty()) keyTopics.append("; ");
                        keyTopics.append(firstLine.length() > 80 ? firstLine.substring(0, 80) + "..." : firstLine);
                    }
                }
                case ASSISTANT -> assistantCount++;
                case TOOL -> toolCount++;
                case SYSTEM -> {} // Don't summarize system messages
            }
        }

        summary.add("Dropped %d user messages, %d assistant responses, %d tool calls".formatted(
                userCount, assistantCount, toolCount));
        if (!keyTopics.isEmpty()) {
            summary.add("Topics discussed: " + keyTopics);
        }

        return summary.toString();
    }
}

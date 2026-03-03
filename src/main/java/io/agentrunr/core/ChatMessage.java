package io.agentrunr.core;

/**
 * A message in the conversation history.
 *
 * @param role       the message role (SYSTEM, USER, ASSISTANT, TOOL)
 * @param content    the message content
 * @param name       optional sender name (for multi-agent identification)
 * @param toolCallId optional tool call ID (for TOOL role messages)
 */
public record ChatMessage(
        Role role,
        String content,
        String name,
        String toolCallId
) {
    public enum Role {
        SYSTEM, USER, ASSISTANT, TOOL
    }

    /** Creates a user message. */
    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content, null, null);
    }

    /** Creates a system message. */
    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content, null, null);
    }

    /** Creates an assistant message. */
    public static ChatMessage assistant(String content, String name) {
        return new ChatMessage(Role.ASSISTANT, content, name, null);
    }

    /** Creates a tool result message. */
    public static ChatMessage toolResult(String toolCallId, String content) {
        return new ChatMessage(Role.TOOL, content, null, toolCallId);
    }
}

package io.agentrunr.core;

import java.util.List;
import java.util.Map;

/**
 * The complete response from an agent run.
 * Contains the conversation messages, the final active agent, and context variables.
 *
 * @param messages         the full message history including assistant and tool messages
 * @param activeAgent      the agent that ended the conversation (may differ from starting agent due to handoffs)
 * @param contextVariables the final state of context variables after all tool executions
 */
public record AgentResponse(
        List<ChatMessage> messages,
        Agent activeAgent,
        Map<String, String> contextVariables
) {
    /**
     * Returns the last assistant message content, or empty string if none.
     */
    public String lastMessage() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (msg.role() == ChatMessage.Role.ASSISTANT && msg.content() != null) {
                return msg.content();
            }
        }
        return "";
    }
}

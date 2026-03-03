package io.agentrunr.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatMessageTest {

    @Test
    void shouldCreateUserMessage() {
        var msg = ChatMessage.user("Hello");

        assertEquals(ChatMessage.Role.USER, msg.role());
        assertEquals("Hello", msg.content());
        assertNull(msg.name());
        assertNull(msg.toolCallId());
    }

    @Test
    void shouldCreateSystemMessage() {
        var msg = ChatMessage.system("You are helpful.");

        assertEquals(ChatMessage.Role.SYSTEM, msg.role());
        assertEquals("You are helpful.", msg.content());
    }

    @Test
    void shouldCreateAssistantMessage() {
        var msg = ChatMessage.assistant("I can help!", "Agent A");

        assertEquals(ChatMessage.Role.ASSISTANT, msg.role());
        assertEquals("I can help!", msg.content());
        assertEquals("Agent A", msg.name());
    }

    @Test
    void shouldCreateToolResultMessage() {
        var msg = ChatMessage.toolResult("call_123", "Result data");

        assertEquals(ChatMessage.Role.TOOL, msg.role());
        assertEquals("Result data", msg.content());
        assertEquals("call_123", msg.toolCallId());
    }
}

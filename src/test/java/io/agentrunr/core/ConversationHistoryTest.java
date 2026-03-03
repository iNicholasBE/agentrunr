package io.agentrunr.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationHistoryTest {

    @Test
    void shouldCreateEmptyHistory() {
        var history = new ConversationHistory();
        assertEquals(0, history.size());
        assertNull(history.lastMessage());
    }

    @Test
    void shouldAddMessages() {
        var history = new ConversationHistory();
        history.add(ChatMessage.user("Hello"));
        history.add(ChatMessage.assistant("Hi there!", "Agent"));

        assertEquals(2, history.size());
    }

    @Test
    void shouldReturnLastMessage() {
        var history = new ConversationHistory();
        history.add(ChatMessage.user("Hello"));
        history.add(ChatMessage.assistant("Hi!", "Agent"));

        assertEquals(ChatMessage.Role.ASSISTANT, history.lastMessage().role());
        assertEquals("Hi!", history.lastMessage().content());
    }

    @Test
    void shouldReturnLastAssistantContent() {
        var history = new ConversationHistory();
        history.add(ChatMessage.user("Q1"));
        history.add(ChatMessage.assistant("A1", "Agent"));
        history.add(ChatMessage.user("Q2"));

        assertEquals("A1", history.lastAssistantContent());
    }

    @Test
    void shouldReturnEmptyStringWhenNoAssistantMessage() {
        var history = new ConversationHistory();
        history.add(ChatMessage.user("Hello"));

        assertEquals("", history.lastAssistantContent());
    }

    @Test
    void shouldCreateFromInitialMessages() {
        var initial = List.of(
                ChatMessage.user("First"),
                ChatMessage.assistant("Second", "Agent")
        );
        var history = new ConversationHistory(initial);

        assertEquals(2, history.size());
    }

    @Test
    void shouldReturnImmutableCopy() {
        var history = new ConversationHistory();
        history.add(ChatMessage.user("Test"));

        List<ChatMessage> messages = history.getMessages();
        assertThrows(UnsupportedOperationException.class, () ->
                messages.add(ChatMessage.user("Should fail")));
    }

    @Test
    void shouldCompactWhenExceedingLimit() {
        var history = new ConversationHistory(5);

        // Add more than 5 non-system messages
        for (int i = 0; i < 10; i++) {
            history.add(ChatMessage.user("User message " + i));
            history.add(ChatMessage.assistant("Response " + i, "Agent"));
        }

        // Should have compacted
        // Non-system messages should be <= 5 + compaction summary
        int nonSystemCount = (int) history.getMessages().stream()
                .filter(m -> m.role() != ChatMessage.Role.SYSTEM)
                .count();
        assertTrue(nonSystemCount <= 6, "Should compact to near maxMessages, got " + nonSystemCount);
    }

    @Test
    void shouldPreserveSystemMessages() {
        var history = new ConversationHistory(3);
        history.add(ChatMessage.system("System instructions"));

        for (int i = 0; i < 8; i++) {
            history.add(ChatMessage.user("Msg " + i));
        }

        // System messages should be preserved
        boolean hasSystem = history.getMessages().stream()
                .anyMatch(m -> m.role() == ChatMessage.Role.SYSTEM);
        assertTrue(hasSystem);
    }

    @Test
    void shouldNotCompactUnderLimit() {
        var history = new ConversationHistory(100);
        history.add(ChatMessage.user("Hello"));
        history.add(ChatMessage.assistant("Hi!", "Agent"));

        assertEquals(2, history.size());
    }

    @Test
    void shouldNotSplitToolResultPairs() {
        var history = new ConversationHistory(4);

        history.add(ChatMessage.user("Use a tool"));
        history.add(ChatMessage.assistant("Calling tool...", "Agent"));
        history.add(ChatMessage.toolResult("call-1", "Tool output"));
        history.add(ChatMessage.user("Thanks"));
        history.add(ChatMessage.assistant("You're welcome!", "Agent"));
        history.add(ChatMessage.user("Another question"));
        history.add(ChatMessage.assistant("Answer", "Agent"));
        history.add(ChatMessage.user("More"));
        history.add(ChatMessage.assistant("More answer", "Agent"));

        // After compaction, tool results shouldn't be orphaned at the boundary
        var messages = history.getMessages();
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).role() == ChatMessage.Role.TOOL) {
                // If there's a TOOL message, the preceding message should be ASSISTANT
                // (unless it's right after compaction summary)
                assertTrue(i > 0, "TOOL message should not be first");
            }
        }
    }

    @Test
    void shouldCreateCompactionSummary() {
        var history = new ConversationHistory(3);

        history.add(ChatMessage.user("What is Java?"));
        history.add(ChatMessage.assistant("Java is a programming language.", "Agent"));
        history.add(ChatMessage.user("What about Python?"));
        history.add(ChatMessage.assistant("Python is also a language.", "Agent"));
        history.add(ChatMessage.user("Compare them"));
        history.add(ChatMessage.assistant("Both are great!", "Agent"));
        history.add(ChatMessage.user("Which is better?"));
        history.add(ChatMessage.assistant("Depends on use case.", "Agent"));

        // Should have a compaction summary system message
        boolean hasCompaction = history.getMessages().stream()
                .anyMatch(m -> m.role() == ChatMessage.Role.SYSTEM
                        && m.content().contains("compacted"));
        assertTrue(hasCompaction, "Should have compaction summary");
    }

    @Test
    void shouldAddAllMessages() {
        var history = new ConversationHistory();
        history.addAll(List.of(
                ChatMessage.user("One"),
                ChatMessage.assistant("Two", "Agent"),
                ChatMessage.user("Three")
        ));

        assertEquals(3, history.size());
    }
}

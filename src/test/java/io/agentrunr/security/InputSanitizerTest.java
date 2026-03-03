package io.agentrunr.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputSanitizerTest {

    private final InputSanitizer sanitizer = new InputSanitizer();

    @Test
    void shouldPassThroughNormalText() {
        assertEquals("Hello world", sanitizer.sanitize("Hello world"));
    }

    @Test
    void shouldHandleNull() {
        assertEquals("", sanitizer.sanitize(null));
    }

    @Test
    void shouldRemoveControlCharacters() {
        String input = "Hello\u0000World\u0007Test";
        String result = sanitizer.sanitize(input);
        assertEquals("HelloWorldTest", result);
    }

    @Test
    void shouldPreserveNewlinesAndTabs() {
        String input = "Line 1\nLine 2\tTabbed";
        assertEquals(input, sanitizer.sanitize(input));
    }

    @Test
    void shouldTruncateLongMessages() {
        String longMessage = "A".repeat(15_000);
        String result = sanitizer.sanitize(longMessage);
        assertTrue(result.length() < 15_000);
        assertTrue(result.endsWith("[truncated]"));
    }

    @Test
    void shouldValidateMessageCount() {
        assertDoesNotThrow(() -> sanitizer.validateMessageCount(1));
        assertDoesNotThrow(() -> sanitizer.validateMessageCount(50));
    }

    @Test
    void shouldRejectZeroMessages() {
        assertThrows(IllegalArgumentException.class, () -> sanitizer.validateMessageCount(0));
    }

    @Test
    void shouldRejectTooManyMessages() {
        assertThrows(IllegalArgumentException.class, () -> sanitizer.validateMessageCount(51));
    }
}

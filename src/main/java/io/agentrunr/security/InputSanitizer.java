package io.agentrunr.security;

import org.springframework.stereotype.Component;

/**
 * Sanitizes user input to prevent prompt injection and other attacks.
 */
@Component
public class InputSanitizer {

    private static final int MAX_MESSAGE_LENGTH = 10_000;
    private static final int MAX_MESSAGES_PER_REQUEST = 50;

    /**
     * Sanitizes a single message content string.
     * Truncates overly long messages and removes control characters.
     *
     * @param content the raw message content
     * @return sanitized content
     */
    public String sanitize(String content) {
        if (content == null) return "";

        // Remove null bytes and other control characters (keep newlines, tabs)
        String cleaned = content.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");

        // Truncate
        if (cleaned.length() > MAX_MESSAGE_LENGTH) {
            cleaned = cleaned.substring(0, MAX_MESSAGE_LENGTH) + "... [truncated]";
        }

        return cleaned;
    }

    /**
     * Validates message count per request.
     *
     * @param count number of messages
     * @throws IllegalArgumentException if too many messages
     */
    public void validateMessageCount(int count) {
        if (count > MAX_MESSAGES_PER_REQUEST) {
            throw new IllegalArgumentException(
                    "Too many messages: " + count + " (max " + MAX_MESSAGES_PER_REQUEST + ")");
        }
        if (count == 0) {
            throw new IllegalArgumentException("At least one message is required");
        }
    }
}

package io.agentrunr.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects important facts from conversations and auto-saves them to memory.
 *
 * <p>Inspired by ZeroClaw's auto_save feature. Scans assistant responses for
 * patterns that indicate important information the user has shared (preferences,
 * names, decisions, etc.) and stores them as CORE memories.</p>
 */
@Component
public class MemoryAutoSaver {

    private static final Logger log = LoggerFactory.getLogger(MemoryAutoSaver.class);

    private static final List<Pattern> FACT_PATTERNS = List.of(
            // "My name is X" / "I'm X"
            Pattern.compile("(?:my name is|i'?m called|call me)\\s+([A-Z][a-z]+(?:\\s[A-Z][a-z]+)?)", Pattern.CASE_INSENSITIVE),
            // "I prefer X" / "I like X"
            Pattern.compile("(?:i prefer|i like|i use|i love|my favorite is)\\s+(.{3,50}?)(?:[.,!?]|$)", Pattern.CASE_INSENSITIVE),
            // "I work at/with X"
            Pattern.compile("(?:i work (?:at|with|for|on))\\s+(.{3,50}?)(?:[.,!?]|$)", Pattern.CASE_INSENSITIVE),
            // "I live in X"
            Pattern.compile("(?:i live in|i'm from|i'm based in)\\s+(.{3,50}?)(?:[.,!?]|$)", Pattern.CASE_INSENSITIVE),
            // "My timezone is X" / "I'm in X timezone"
            Pattern.compile("(?:my timezone is|i'm in .* timezone|my time zone)\\s+(.{2,30}?)(?:[.,!?]|$)", Pattern.CASE_INSENSITIVE),
            // "Remember that X" / "Don't forget X"
            Pattern.compile("(?:remember (?:that|this)?|don'?t forget|keep in mind)\\s+(.{5,200}?)(?:[.,!?]|$)", Pattern.CASE_INSENSITIVE),
            // "Always X" / "Never X" (preferences)
            Pattern.compile("(?:always|never)\\s+(.{5,100}?)(?:[.,!?]|$)", Pattern.CASE_INSENSITIVE)
    );

    private final Memory memory;

    public MemoryAutoSaver(Memory memory) {
        this.memory = memory;
    }

    /**
     * Scans a user message for important facts and auto-saves them.
     *
     * @param userMessage the user's message
     * @param sessionId   the current session ID
     * @return number of facts saved
     */
    public int scanAndSave(String userMessage, String sessionId) {
        if (userMessage == null || userMessage.length() < 10) {
            return 0;
        }

        int saved = 0;

        for (Pattern pattern : FACT_PATTERNS) {
            Matcher matcher = pattern.matcher(userMessage);
            while (matcher.find()) {
                String fact = matcher.group(1).trim();
                if (fact.length() < 3 || fact.length() > 200) continue;

                String key = generateKey(pattern, fact);
                String content = fact;

                // Only save if we don't already have this fact
                if (memory.get(key).isEmpty()) {
                    memory.store(key, content, MemoryCategory.CORE, sessionId);
                    log.debug("Auto-saved fact: {} = {}", key, content);
                    saved++;
                }
            }
        }

        return saved;
    }

    /**
     * Checks if a user message contains explicit "remember" instructions.
     */
    public boolean containsRememberRequest(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("remember") || lower.contains("don't forget")
                || lower.contains("keep in mind");
    }

    private String generateKey(Pattern pattern, String fact) {
        String patternStr = pattern.pattern().toLowerCase();
        if (patternStr.contains("name")) return "user_name";
        if (patternStr.contains("prefer") || patternStr.contains("like") || patternStr.contains("love")) {
            return "preference_" + fact.substring(0, Math.min(20, fact.length()))
                    .toLowerCase().replaceAll("[^a-z0-9]", "_");
        }
        if (patternStr.contains("work")) return "workplace";
        if (patternStr.contains("live") || patternStr.contains("from")) return "location";
        if (patternStr.contains("timezone")) return "timezone";
        if (patternStr.contains("remember") || patternStr.contains("forget")) {
            return "user_note_" + System.currentTimeMillis();
        }
        if (patternStr.contains("always") || patternStr.contains("never")) {
            return "rule_" + fact.substring(0, Math.min(20, fact.length()))
                    .toLowerCase().replaceAll("[^a-z0-9]", "_");
        }
        return "fact_" + System.currentTimeMillis();
    }
}

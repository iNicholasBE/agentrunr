package io.agentrunr.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Mutable context variables shared across tool calls within an agent run.
 * Inspired by OpenAI Swarm's context_variables pattern.
 *
 * <p>Context variables allow tools to share state without relying on the LLM
 * to pass data between tool calls. For example, a login tool can set a
 * "user_id" that subsequent tools can access.</p>
 */
public class AgentContext {

    private final Map<String, String> variables;

    public AgentContext() {
        this.variables = new HashMap<>();
    }

    public AgentContext(Map<String, String> initial) {
        this.variables = new HashMap<>(initial);
    }

    /**
     * Gets a context variable value, or the default if not set.
     */
    public String get(String key, String defaultValue) {
        return variables.getOrDefault(key, defaultValue);
    }

    /**
     * Gets a context variable value, or empty string if not set.
     */
    public String get(String key) {
        return variables.getOrDefault(key, "");
    }

    /**
     * Sets a context variable.
     */
    public void set(String key, String value) {
        variables.put(key, value);
    }

    /**
     * Merges additional context variables into this context.
     * Existing values are overwritten.
     */
    public void merge(Map<String, String> additional) {
        if (additional != null) {
            variables.putAll(additional);
        }
    }

    /**
     * Returns an unmodifiable view of the current context variables.
     */
    public Map<String, String> toMap() {
        return Collections.unmodifiableMap(variables);
    }

    /**
     * Returns a mutable copy of the context variables.
     */
    public Map<String, String> toMutableMap() {
        return new HashMap<>(variables);
    }
}

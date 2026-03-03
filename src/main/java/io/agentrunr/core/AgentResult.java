package io.agentrunr.core;

import java.util.Map;

/**
 * The result of a tool function invocation.
 * Inspired by OpenAI Swarm's Result type.
 *
 * <p>A tool can return:</p>
 * <ul>
 *   <li>A string value (the tool's output)</li>
 *   <li>An optional agent to hand off to</li>
 *   <li>Updated context variables</li>
 * </ul>
 *
 * @param value            the string result of the tool call
 * @param handoffAgent     if set, the conversation hands off to this agent
 * @param contextVariables updated context variables to merge
 */
public record AgentResult(
        String value,
        Agent handoffAgent,
        Map<String, String> contextVariables
) {
    /**
     * Creates a simple result with just a value.
     */
    public static AgentResult of(String value) {
        return new AgentResult(value, null, Map.of());
    }

    /**
     * Creates a result that triggers a handoff to another agent.
     */
    public static AgentResult handoff(Agent agent) {
        return new AgentResult("Handing off to " + agent.name(), agent, Map.of());
    }

    /**
     * Creates a result with a value and updated context variables.
     */
    public static AgentResult withContext(String value, Map<String, String> contextVariables) {
        return new AgentResult(value, null, contextVariables);
    }
}

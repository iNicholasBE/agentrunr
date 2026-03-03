package io.agentrunr.core;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * An AI agent definition, inspired by OpenAI Swarm's Agent.
 * An agent has a name, a model, instructions (system prompt), and a list of tools it can use.
 *
 * <p>Instructions can be either a static string or a dynamic function that receives
 * context variables and returns the instructions string.</p>
 *
 * @param name        the agent's display name
 * @param model       the LLM model to use (e.g., "gpt-4.1", "claude-sonnet-4-20250514")
 * @param instructions static system prompt for the agent
 * @param instructionsFn dynamic system prompt generator (takes context variables, returns instructions). Overrides instructions if set.
 * @param toolNames   list of tool bean names this agent can use (resolved from Spring context)
 * @param toolChoice  tool choice strategy: "auto", "required", "none", or a specific tool name
 */
public record Agent(
        String name,
        String model,
        String instructions,
        Function<Map<String, String>, String> instructionsFn,
        List<String> toolNames,
        String toolChoice
) {
    /** Default model when none specified. */
    public static final String DEFAULT_MODEL = "gpt-4.1";

    /**
     * Creates an agent with static instructions.
     */
    public Agent(String name, String model, String instructions, List<String> toolNames) {
        this(name, model, instructions, null, toolNames, "auto");
    }

    /**
     * Creates a minimal agent with just a name and instructions.
     */
    public Agent(String name, String instructions) {
        this(name, DEFAULT_MODEL, instructions, null, List.of(), "auto");
    }

    /**
     * Resolves the instructions string, using the dynamic function if available.
     *
     * @param contextVariables current context variables
     * @return the resolved instructions string
     */
    public String resolveInstructions(Map<String, String> contextVariables) {
        if (instructionsFn != null) {
            return instructionsFn.apply(contextVariables);
        }
        return instructions != null ? instructions : "You are a helpful agent.";
    }

    /**
     * Returns the model, falling back to the default.
     */
    public String resolvedModel() {
        return model != null ? model : DEFAULT_MODEL;
    }
}

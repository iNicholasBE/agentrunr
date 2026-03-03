package io.agentrunr.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentTest {

    @Test
    void shouldCreateAgentWithStaticInstructions() {
        var agent = new Agent("TestAgent", "You are a test agent.");

        assertEquals("TestAgent", agent.name());
        assertEquals("You are a test agent.", agent.instructions());
        assertEquals("gpt-4.1", agent.resolvedModel());
        assertTrue(agent.toolNames().isEmpty());
        assertEquals("auto", agent.toolChoice());
    }

    @Test
    void shouldCreateAgentWithModelAndTools() {
        var agent = new Agent("ToolAgent", "claude-sonnet-4-20250514", "Help users.", List.of("search", "calculator"));

        assertEquals("ToolAgent", agent.name());
        assertEquals("claude-sonnet-4-20250514", agent.resolvedModel());
        assertEquals(List.of("search", "calculator"), agent.toolNames());
    }

    @Test
    void shouldResolveStaticInstructions() {
        var agent = new Agent("Agent", "Static instructions here.");

        assertEquals("Static instructions here.", agent.resolveInstructions(Map.of()));
    }

    @Test
    void shouldResolveDynamicInstructions() {
        var agent = new Agent(
                "DynAgent", "gpt-4.1", null,
                ctx -> "Hello " + ctx.getOrDefault("user", "stranger") + "!",
                List.of(), "auto"
        );

        assertEquals("Hello stranger!", agent.resolveInstructions(Map.of()));
        assertEquals("Hello Alice!", agent.resolveInstructions(Map.of("user", "Alice")));
    }

    @Test
    void shouldFallbackToDefaultInstructions() {
        var agent = new Agent("Bare", "gpt-4.1", null, null, List.of(), "auto");

        assertEquals("You are a helpful agent.", agent.resolveInstructions(Map.of()));
    }

    @Test
    void shouldFallbackToDefaultModel() {
        var agent = new Agent("Agent", null, "Instructions", null, List.of(), "auto");

        assertEquals("gpt-4.1", agent.resolvedModel());
    }
}

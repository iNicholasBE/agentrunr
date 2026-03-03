package io.agentrunr.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentResultTest {

    @Test
    void shouldCreateSimpleResult() {
        var result = AgentResult.of("The weather is sunny.");

        assertEquals("The weather is sunny.", result.value());
        assertNull(result.handoffAgent());
        assertTrue(result.contextVariables().isEmpty());
    }

    @Test
    void shouldCreateHandoffResult() {
        var targetAgent = new Agent("BillingAgent", "Handle billing inquiries.");
        var result = AgentResult.handoff(targetAgent);

        assertEquals("Handing off to BillingAgent", result.value());
        assertEquals(targetAgent, result.handoffAgent());
    }

    @Test
    void shouldCreateResultWithContext() {
        var result = AgentResult.withContext("Logged in.", Map.of("user_id", "42", "role", "admin"));

        assertEquals("Logged in.", result.value());
        assertNull(result.handoffAgent());
        assertEquals("42", result.contextVariables().get("user_id"));
        assertEquals("admin", result.contextVariables().get("role"));
    }
}

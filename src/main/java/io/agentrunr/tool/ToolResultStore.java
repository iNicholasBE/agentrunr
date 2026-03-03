package io.agentrunr.tool;

import io.agentrunr.core.AgentResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory store for tool execution results.
 * Decouples JobRunrToolExecutor from ToolExecutionService to avoid circular deps.
 */
@Component
public class ToolResultStore {

    private final ConcurrentHashMap<String, AgentResult> results = new ConcurrentHashMap<>();

    public void store(String jobTrackingId, AgentResult result) {
        results.put(jobTrackingId, result);
    }

    public AgentResult get(String jobTrackingId) {
        return results.get(jobTrackingId);
    }

    public boolean isComplete(String jobTrackingId) {
        return results.containsKey(jobTrackingId);
    }

    public AgentResult consume(String jobTrackingId) {
        return results.remove(jobTrackingId);
    }
}

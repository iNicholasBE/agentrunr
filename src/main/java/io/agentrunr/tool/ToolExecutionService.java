package io.agentrunr.tool;

import io.agentrunr.core.AgentContext;
import io.agentrunr.core.AgentResult;
import io.agentrunr.core.ToolRegistry;
import org.jobrunr.jobs.annotations.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service that executes tool calls within JobRunr jobs.
 * No circular dependency: only depends on ToolRegistry and ToolResultStore.
 */
@Service
public class ToolExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionService.class);

    private final ToolRegistry toolRegistry;
    private final ToolResultStore resultStore;

    public ToolExecutionService(ToolRegistry toolRegistry, ToolResultStore resultStore) {
        this.toolRegistry = toolRegistry;
        this.resultStore = resultStore;
    }

    @Job(name = "Agent Tool: %1 - %2")
    public void executeToolJob(String jobTrackingId, String toolName, String arguments, Map<String, String> contextVars) {
        log.info("Executing tool '{}' in JobRunr job [{}]", toolName, jobTrackingId);

        AgentContext context = new AgentContext(contextVars);
        AgentResult result;

        try {
            result = toolRegistry.executeTool(toolName, arguments, context);
        } catch (Exception e) {
            log.error("Tool '{}' failed in job [{}]: {}", toolName, jobTrackingId, e.getMessage(), e);
            result = AgentResult.of("Error executing tool '" + toolName + "': " + e.getMessage());
        }

        resultStore.store(jobTrackingId, result);
        log.info("Tool '{}' completed in job [{}]", toolName, jobTrackingId);
    }
}

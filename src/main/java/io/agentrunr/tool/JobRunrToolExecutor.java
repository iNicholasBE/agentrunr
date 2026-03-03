package io.agentrunr.tool;

import io.agentrunr.core.AgentContext;
import io.agentrunr.core.AgentResult;
import org.jobrunr.scheduling.JobScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Executes agent tool calls as JobRunr background jobs.
 */
@Component
public class JobRunrToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(JobRunrToolExecutor.class);

    private final JobScheduler jobScheduler;
    private final ToolResultStore resultStore;
    private final ToolExecutionService toolExecutionService;

    public JobRunrToolExecutor(JobScheduler jobScheduler, ToolResultStore resultStore,
                               ToolExecutionService toolExecutionService) {
        this.jobScheduler = jobScheduler;
        this.resultStore = resultStore;
        this.toolExecutionService = toolExecutionService;
    }

    /**
     * Enqueues a tool call as a JobRunr background job.
     */
    public String enqueueToolCall(String toolName, String arguments, AgentContext context) {
        String jobTrackingId = UUID.randomUUID().toString();
        Map<String, String> contextSnapshot = context.toMutableMap();

        log.info("Enqueueing tool '{}' as JobRunr job [{}]", toolName, jobTrackingId);

        jobScheduler.enqueue(() ->
                toolExecutionService.executeToolJob(jobTrackingId, toolName, arguments, contextSnapshot));

        return jobTrackingId;
    }

    public ToolResultStore getResultStore() {
        return resultStore;
    }
}

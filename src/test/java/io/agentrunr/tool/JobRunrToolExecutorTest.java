package io.agentrunr.tool;

import io.agentrunr.core.AgentContext;
import io.agentrunr.core.AgentResult;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JobRunrToolExecutorTest {

    @Mock
    private JobScheduler jobScheduler;

    @Mock
    private ToolExecutionService toolExecutionService;

    private ToolResultStore resultStore;
    private JobRunrToolExecutor executor;

    @BeforeEach
    void setUp() {
        resultStore = new ToolResultStore();
        executor = new JobRunrToolExecutor(jobScheduler, resultStore, toolExecutionService);
    }

    @Test
    void shouldStoreAndRetrieveResults() {
        resultStore.store("job-1", AgentResult.of("Success!"));

        assertTrue(resultStore.isComplete("job-1"));
        assertEquals("Success!", resultStore.get("job-1").value());
    }

    @Test
    void shouldReturnNullForIncompleteJob() {
        assertFalse(resultStore.isComplete("nonexistent"));
        assertNull(resultStore.get("nonexistent"));
    }

    @Test
    void shouldConsumeResult() {
        resultStore.store("job-2", AgentResult.of("Done"));

        var consumed = resultStore.consume("job-2");
        assertEquals("Done", consumed.value());
        assertFalse(resultStore.isComplete("job-2"));
    }

    @Test
    void shouldEnqueueToolCallAndReturnTrackingId() {
        var context = new AgentContext();
        String trackingId = executor.enqueueToolCall("get_time", "{}", context);

        assertNotNull(trackingId);
        assertFalse(trackingId.isBlank());
    }
}

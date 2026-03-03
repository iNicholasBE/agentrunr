package io.agentrunr.cron;

import io.agentrunr.channel.AgentConfigurer;
import io.agentrunr.channel.ChannelRegistry;
import io.agentrunr.core.AgentRunner;
import org.jobrunr.jobs.JobDetails;
import org.jobrunr.jobs.JobParameter;
import org.jobrunr.jobs.RecurringJob;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.RecurringJobsResult;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CronServiceTest {

    private CronService cronService;
    private JobScheduler jobScheduler;
    private StorageProvider storageProvider;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        jobScheduler = mock(JobScheduler.class);
        storageProvider = mock(StorageProvider.class);
        var agentRunner = mock(AgentRunner.class);
        var agentConfigurer = mock(AgentConfigurer.class);
        var channelRegistry = new ChannelRegistry();
        var cronJob = new CronJob(agentRunner, agentConfigurer, channelRegistry);
        cronService = new CronService(jobScheduler, storageProvider, cronJob, tempDir.toString());
    }

    @Test
    void shouldAddCronJobAndWriteTaskFile() throws IOException {
        var task = cronService.addJob("Report", "Generate daily report", "0 9 * * *");

        assertNotNull(task.id());
        assertEquals("Generate daily report", task.message());
        assertEquals("0 9 * * *", task.cron());

        // Verify task file was written
        Path taskFile = tempDir.resolve(task.id() + ".md");
        assertTrue(Files.exists(taskFile));
        assertEquals("Generate daily report", Files.readString(taskFile));
    }

    @Test
    void shouldAddIntervalJobAndWriteTaskFile() throws IOException {
        var task = cronService.addJob("Poller", "Check status", 300);

        assertNotNull(task.id());
        assertEquals("Poller", task.name());
        assertEquals(300, task.intervalSeconds());

        // Verify task file was written
        Path taskFile = tempDir.resolve(task.id() + ".md");
        assertTrue(Files.exists(taskFile));
        assertEquals("Check status", Files.readString(taskFile));
    }

    @Test
    void shouldAddOneShotJobAndWriteTaskFile() throws IOException {
        var executeAt = Instant.now().plusSeconds(3600);
        var task = cronService.addOneShot("Reminder", "Call dentist", executeAt);

        assertNotNull(task.id());
        assertEquals("Reminder", task.name());
        assertEquals(executeAt, task.executeAt());

        Path taskFile = tempDir.resolve(task.id() + ".md");
        assertTrue(Files.exists(taskFile));
        assertEquals("Call dentist", Files.readString(taskFile));
    }

    @Test
    void shouldListRecurringJobsFromStorageProvider() throws IOException {
        // Write task files that match the recurring jobs
        Files.writeString(tempDir.resolve("task-a.md"), "msg-a");
        Files.writeString(tempDir.resolve("task-b.md"), "msg-b");

        var result = new RecurringJobsResult(List.of(
                makeRecurringJob("agent-cron-task-a", "task-a", "*/5 * * * *"),
                makeRecurringJob("agent-cron-task-b", "task-b", "0 * * * *"),
                makeRecurringJob("agent-heartbeat", "heartbeat", "*/30 * * * *")
        ));
        when(storageProvider.getRecurringJobs()).thenReturn(result);

        var jobs = cronService.listJobs();

        assertEquals(2, jobs.size());
        assertEquals("task-a", jobs.get(0).name());
        assertEquals("msg-a", jobs.get(0).message());
        assertEquals("task-b", jobs.get(1).name());
        assertEquals("msg-b", jobs.get(1).message());
    }

    @Test
    void shouldRemoveJobAndDeleteFile() throws IOException {
        Files.writeString(tempDir.resolve("to-remove.md"), "some content");
        when(storageProvider.deleteRecurringJob("agent-cron-to-remove")).thenReturn(1);

        assertTrue(cronService.removeJob("to-remove"));
        assertFalse(Files.exists(tempDir.resolve("to-remove.md")));
        verify(storageProvider).deleteRecurringJob("agent-cron-to-remove");
    }

    @Test
    void shouldReturnFalseWhenRemovingNonexistentJob() {
        when(storageProvider.deleteRecurringJob("agent-cron-nonexistent")).thenReturn(0);

        assertFalse(cronService.removeJob("nonexistent"));
    }

    @Test
    void shouldGetTaskById() throws IOException {
        Files.writeString(tempDir.resolve("abc123.md"), "the prompt");

        var result = new RecurringJobsResult(List.of(
                makeRecurringJob("agent-cron-abc123", "abc123", "0 * * * *")
        ));
        when(storageProvider.getRecurringJobs()).thenReturn(result);

        var found = cronService.getTask("abc123");

        assertNotNull(found);
        assertEquals("abc123", found.name());
        assertEquals("the prompt", found.message());
    }

    @Test
    void shouldReturnNullForUnknownTaskId() {
        when(storageProvider.getRecurringJobs()).thenReturn(new RecurringJobsResult());

        assertNull(cronService.getTask("nonexistent"));
    }

    @Test
    void shouldThrowWhenTriggeringNonexistentTask() {
        when(storageProvider.getRecurringJobs()).thenReturn(new RecurringJobsResult());

        assertThrows(IllegalArgumentException.class, () -> cronService.triggerNow("nonexistent"));
    }

    @Test
    void shouldSlugifyTaskNames() {
        assertEquals("my-task", CronService.slugify("My Task"));
        assertEquals("check-weather", CronService.slugify("Check Weather!"));
        assertEquals("hello-world", CronService.slugify("  Hello   World  "));
    }

    private RecurringJob makeRecurringJob(String id, String taskName, String cron) {
        var details = new JobDetails(
                CronJob.class.getName(),
                null,
                "executeTask",
                List.of(new JobParameter(String.class, taskName))
        );
        return new RecurringJob(id, details, cron, "UTC", RecurringJob.CreatedBy.API);
    }
}

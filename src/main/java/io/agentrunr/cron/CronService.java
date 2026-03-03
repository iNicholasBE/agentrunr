package io.agentrunr.cron;

import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Manages scheduled agent tasks using file-based task definitions.
 * Task prompts are stored as .md files in the tasks directory.
 * JobRunr handles scheduling — the recurring job reads the file on each execution.
 */
@Service
public class CronService {

    private static final Logger log = LoggerFactory.getLogger(CronService.class);
    static final String JOB_PREFIX = "agent-cron-";

    private final JobScheduler jobScheduler;
    private final StorageProvider storageProvider;
    private final CronJob cronJob;
    private final Path tasksDir;

    public CronService(JobScheduler jobScheduler, StorageProvider storageProvider, CronJob cronJob,
                       @Value("${agent.tasks.path:./workspace/tasks}") String tasksPath) {
        this.jobScheduler = jobScheduler;
        this.storageProvider = storageProvider;
        this.cronJob = cronJob;
        this.tasksDir = Path.of(tasksPath);
        try {
            Files.createDirectories(this.tasksDir);
        } catch (IOException e) {
            log.error("Failed to create tasks directory: {}", tasksDir, e);
        }
    }

    /**
     * Adds a recurring job with a cron expression.
     * Writes the prompt to a .md file and registers a JobRunr recurring job.
     */
    public ScheduledTask addJob(String name, String message, String cronExpression) {
        String taskName = slugify(name);
        writeTaskFile(taskName, message);

        String jobId = JOB_PREFIX + taskName;
        jobScheduler.<CronJob>scheduleRecurrently(jobId, cronExpression, x -> x.executeTask(taskName));

        log.info("Added cron task '{}' ({}): {}", taskName, cronExpression, message);
        return ScheduledTask.cron(taskName, name, message, cronExpression);
    }

    /**
     * Adds a recurring job with an interval in seconds.
     */
    public ScheduledTask addJob(String name, String message, int intervalSeconds) {
        int minutes = Math.max(1, intervalSeconds / 60);
        String cron = "*/%d * * * *".formatted(minutes);

        String taskName = slugify(name);
        writeTaskFile(taskName, message);

        String jobId = JOB_PREFIX + taskName;
        jobScheduler.<CronJob>scheduleRecurrently(jobId, cron, x -> x.executeTask(taskName));

        log.info("Added interval task '{}' (every {}s → cron '{}'): {}", taskName, intervalSeconds, cron, message);
        return ScheduledTask.interval(taskName, name, message, intervalSeconds);
    }

    /**
     * Adds a one-shot job that runs at a specific time.
     * One-shot jobs don't get task files (they're ephemeral).
     */
    public ScheduledTask addOneShot(String name, String message, Instant executeAt) {
        String taskName = slugify(name);
        writeTaskFile(taskName, message);

        String jobId = JOB_PREFIX + taskName;
        jobScheduler.<CronJob>schedule(executeAt, x -> x.executeTask(taskName));

        log.info("Added one-shot task '{}' at {}: {}", taskName, executeAt, message);
        return ScheduledTask.oneShot(taskName, name, message, executeAt);
    }

    /**
     * Lists all recurring agent tasks from JobRunr's storage.
     */
    public List<ScheduledTask> listJobs() {
        return storageProvider.getRecurringJobs().stream()
                .filter(job -> job.getId().startsWith(JOB_PREFIX))
                .map(job -> ScheduledTask.fromRecurringJob(job, JOB_PREFIX, tasksDir))
                .toList();
    }

    /**
     * Returns a task by ID, or null if not found.
     */
    public ScheduledTask getTask(String taskId) {
        String jobId = taskId.startsWith(JOB_PREFIX) ? taskId : JOB_PREFIX + taskId;
        return storageProvider.getRecurringJobs().stream()
                .filter(job -> job.getId().equals(jobId))
                .map(job -> ScheduledTask.fromRecurringJob(job, JOB_PREFIX, tasksDir))
                .findFirst()
                .orElse(null);
    }

    /**
     * Removes a scheduled job and its task file.
     */
    public boolean removeJob(String taskId) {
        String jobId = taskId.startsWith(JOB_PREFIX) ? taskId : JOB_PREFIX + taskId;
        String taskName = taskId.startsWith(JOB_PREFIX) ? taskId.substring(JOB_PREFIX.length()) : taskId;

        try {
            int deleted = storageProvider.deleteRecurringJob(jobId);
            deleteTaskFile(taskName);
            if (deleted > 0) {
                log.info("Removed task '{}'", taskName);
                return true;
            }
        } catch (Exception e) {
            log.debug("Could not delete recurring job '{}': {}", jobId, e.getMessage());
        }
        return false;
    }

    /**
     * Triggers a scheduled task immediately (in addition to its schedule).
     */
    public void triggerNow(String taskId) {
        ScheduledTask task = getTask(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        log.info("Triggering task '{}' immediately", task.name());
        cronJob.executeTask(task.id());
    }

    Path getTasksDir() {
        return tasksDir;
    }

    private void writeTaskFile(String taskName, String message) {
        Path file = tasksDir.resolve(taskName + ".md");
        try {
            Files.writeString(file, message);
            log.debug("Wrote task file: {}", file);
        } catch (IOException e) {
            log.error("Failed to write task file: {}", file, e);
            throw new RuntimeException("Failed to write task file: " + file, e);
        }
    }

    private void deleteTaskFile(String taskName) {
        Path file = tasksDir.resolve(taskName + ".md");
        try {
            Files.deleteIfExists(file);
            log.debug("Deleted task file: {}", file);
        } catch (IOException e) {
            log.warn("Failed to delete task file: {}", file, e);
        }
    }

    static String slugify(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}

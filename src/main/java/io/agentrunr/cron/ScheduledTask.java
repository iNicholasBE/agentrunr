package io.agentrunr.cron;

import org.jobrunr.jobs.RecurringJob;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Represents a scheduled agent task.
 *
 * @param id           unique job identifier
 * @param name         human-readable name
 * @param message      the message to send to the agent when triggered
 * @param cron         cron expression (null for one-shot or interval)
 * @param intervalSeconds interval in seconds (null for cron or one-shot)
 * @param executeAt    execution time for one-shot jobs (null for recurring)
 * @param createdAt    when the task was created
 */
public record ScheduledTask(
        String id,
        String name,
        String message,
        String cron,
        Integer intervalSeconds,
        Instant executeAt,
        Instant createdAt
) {
    /**
     * Creates a cron-based scheduled task.
     */
    public static ScheduledTask cron(String id, String name, String message, String cronExpression) {
        return new ScheduledTask(id, name, message, cronExpression, null, null, Instant.now());
    }

    /**
     * Creates an interval-based scheduled task.
     */
    public static ScheduledTask interval(String id, String name, String message, int intervalSeconds) {
        return new ScheduledTask(id, name, message, null, intervalSeconds, null, Instant.now());
    }

    /**
     * Creates a one-shot scheduled task.
     */
    public static ScheduledTask oneShot(String id, String name, String message, Instant executeAt) {
        return new ScheduledTask(id, name, message, null, null, executeAt, Instant.now());
    }

    /**
     * Creates a ScheduledTask from a JobRunr RecurringJob.
     * Reads the task prompt from the corresponding .md file in tasksDir.
     */
    public static ScheduledTask fromRecurringJob(RecurringJob job, String idPrefix, Path tasksDir) {
        String id = job.getId();
        if (id.startsWith(idPrefix)) {
            id = id.substring(idPrefix.length());
        }

        // The task name is the first (and only) parameter of executeTask(taskName)
        Object[] params = job.getJobDetails().getJobParameterValues();
        String taskName = params.length > 0 ? String.valueOf(params[0]) : id;

        // Read the prompt from the task file
        String message = "";
        Path taskFile = tasksDir.resolve(taskName + ".md");
        if (Files.exists(taskFile)) {
            try {
                message = Files.readString(taskFile);
            } catch (IOException e) {
                message = "(failed to read task file)";
            }
        }

        return new ScheduledTask(
                id, taskName, message,
                job.getScheduleExpression(), null, null,
                job.getCreatedAt()
        );
    }

    /**
     * Returns a human-readable description of the schedule.
     */
    public String scheduleDescription() {
        if (cron != null) return "cron: " + cron;
        if (intervalSeconds != null) return "every " + intervalSeconds + "s";
        if (executeAt != null) return "at " + executeAt;
        return "unknown";
    }
}

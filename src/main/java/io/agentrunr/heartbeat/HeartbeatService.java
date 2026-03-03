package io.agentrunr.heartbeat;

import org.jobrunr.scheduling.JobScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Manages the heartbeat recurring job via JobRunr.
 * On application startup, registers a recurring job that periodically checks
 * HEARTBEAT.md for tasks and processes them through the agent.
 */
@Service
public class HeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);
    private static final String HEARTBEAT_JOB_ID = "agent-heartbeat";

    private final JobScheduler jobScheduler;
    private final HeartbeatJob heartbeatJob;

    @Value("${agent.heartbeat.interval-minutes:30}")
    private int intervalMinutes;

    @Value("${agent.heartbeat.file:./HEARTBEAT.md}")
    private String heartbeatFile;

    @Value("${agent.heartbeat.enabled:true}")
    private boolean enabled;

    public HeartbeatService(JobScheduler jobScheduler, HeartbeatJob heartbeatJob) {
        this.jobScheduler = jobScheduler;
        this.heartbeatJob = heartbeatJob;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) {
            log.info("Heartbeat disabled via configuration");
            return;
        }

        log.info("Registering heartbeat job: every {} minutes, file: {}", intervalMinutes, heartbeatFile);
        String cronExpression = buildCronExpression(intervalMinutes);

        jobScheduler.<HeartbeatJob>scheduleRecurrently(HEARTBEAT_JOB_ID,
                cronExpression,
                x -> x.execute(heartbeatFile));

        log.info("Heartbeat job registered with cron: {}", cronExpression);
    }

    /**
     * Triggers a heartbeat check immediately (outside the schedule).
     */
    public void triggerNow() {
        log.info("Triggering immediate heartbeat check");
        heartbeatJob.execute(heartbeatFile);
    }

    /**
     * Stops the heartbeat recurring job.
     */
    public void stop() {
        jobScheduler.deleteRecurringJob(HEARTBEAT_JOB_ID);
        log.info("Heartbeat job stopped");
    }

    /**
     * Builds a cron expression for the given interval in minutes.
     * For intervals that divide evenly into 60, uses minute-based cron.
     * For longer intervals, uses hour-based cron.
     */
    static String buildCronExpression(int intervalMinutes) {
        if (intervalMinutes <= 0) throw new IllegalArgumentException("Interval must be positive");
        if (intervalMinutes < 60) {
            return "*/%d * * * *".formatted(intervalMinutes);
        }
        int hours = intervalMinutes / 60;
        int remainingMinutes = intervalMinutes % 60;
        if (remainingMinutes == 0) {
            return "0 */%d * * *".formatted(hours);
        }
        // For non-standard intervals, approximate to nearest minute-based
        return "*/%d * * * *".formatted(intervalMinutes);
    }

    public int getIntervalMinutes() {
        return intervalMinutes;
    }

    public String getHeartbeatFile() {
        return heartbeatFile;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

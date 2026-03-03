package io.agentrunr.cron;

import io.agentrunr.security.InputSanitizer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST API for managing scheduled agent tasks.
 * Supports cron, interval, and one-shot scheduling.
 */
@RestController
@RequestMapping("/api/cron")
public class CronController {

    private final CronService cronService;
    private final InputSanitizer inputSanitizer;

    public CronController(CronService cronService, InputSanitizer inputSanitizer) {
        this.cronService = cronService;
        this.inputSanitizer = inputSanitizer;
    }

    /**
     * Creates a new scheduled task.
     * Accepts one of: cron expression, intervalSeconds, or executeAt.
     */
    @PostMapping
    public ResponseEntity<ScheduledTask> addJob(@RequestBody CreateTaskRequest request) {
        String name = inputSanitizer.sanitize(request.name());
        String message = inputSanitizer.sanitize(request.message());

        if (name.isBlank() || message.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        ScheduledTask task;
        if (request.cron() != null && !request.cron().isBlank()) {
            task = cronService.addJob(name, message, request.cron());
        } else if (request.intervalSeconds() != null && request.intervalSeconds() > 0) {
            task = cronService.addJob(name, message, request.intervalSeconds());
        } else if (request.executeAt() != null) {
            task = cronService.addOneShot(name, message, request.executeAt());
        } else {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(task);
    }

    /**
     * Lists all scheduled tasks.
     */
    @GetMapping
    public ResponseEntity<List<ScheduledTask>> listJobs() {
        return ResponseEntity.ok(cronService.listJobs());
    }

    /**
     * Removes a scheduled task.
     */
    @DeleteMapping("/{jobId}")
    public ResponseEntity<Map<String, String>> removeJob(@PathVariable String jobId) {
        boolean removed = cronService.removeJob(jobId);
        if (!removed) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("status", "removed", "id", jobId));
    }

    /**
     * Triggers a scheduled task immediately.
     */
    @PostMapping("/{jobId}/run")
    public ResponseEntity<Map<String, String>> triggerJob(@PathVariable String jobId) {
        try {
            cronService.triggerNow(jobId);
            return ResponseEntity.ok(Map.of("status", "triggered", "id", jobId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Request body for creating a scheduled task.
     */
    public record CreateTaskRequest(
            String name,
            String message,
            String cron,
            Integer intervalSeconds,
            Instant executeAt
    ) {}
}

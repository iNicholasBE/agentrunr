package io.agentrunr.cron;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ScheduledTaskTest {

    @Test
    void shouldCreateCronTask() {
        var task = ScheduledTask.cron("abc", "Daily Report", "Generate report", "0 9 * * *");

        assertEquals("abc", task.id());
        assertEquals("Daily Report", task.name());
        assertEquals("Generate report", task.message());
        assertEquals("0 9 * * *", task.cron());
        assertNull(task.intervalSeconds());
        assertNull(task.executeAt());
        assertNotNull(task.createdAt());
        assertEquals("cron: 0 9 * * *", task.scheduleDescription());
    }

    @Test
    void shouldCreateIntervalTask() {
        var task = ScheduledTask.interval("def", "Poller", "Check status", 300);

        assertEquals("Poller", task.name());
        assertEquals(300, task.intervalSeconds());
        assertNull(task.cron());
        assertEquals("every 300s", task.scheduleDescription());
    }

    @Test
    void shouldCreateOneShotTask() {
        var executeAt = Instant.parse("2026-03-01T10:00:00Z");
        var task = ScheduledTask.oneShot("ghi", "Reminder", "Call dentist", executeAt);

        assertEquals("Reminder", task.name());
        assertEquals(executeAt, task.executeAt());
        assertNull(task.cron());
        assertNull(task.intervalSeconds());
        assertTrue(task.scheduleDescription().contains("2026-03-01"));
    }
}

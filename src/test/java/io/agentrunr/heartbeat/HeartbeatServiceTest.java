package io.agentrunr.heartbeat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeartbeatServiceTest {

    @Test
    void shouldBuildCronForMinuteIntervals() {
        assertEquals("*/5 * * * *", HeartbeatService.buildCronExpression(5));
        assertEquals("*/30 * * * *", HeartbeatService.buildCronExpression(30));
        assertEquals("*/1 * * * *", HeartbeatService.buildCronExpression(1));
    }

    @Test
    void shouldBuildCronForHourIntervals() {
        assertEquals("0 */1 * * *", HeartbeatService.buildCronExpression(60));
        assertEquals("0 */2 * * *", HeartbeatService.buildCronExpression(120));
    }

    @Test
    void shouldBuildCronForSubHourIntervals() {
        assertEquals("*/45 * * * *", HeartbeatService.buildCronExpression(45));
    }

    @Test
    void shouldRejectZeroOrNegativeInterval() {
        assertThrows(IllegalArgumentException.class, () -> HeartbeatService.buildCronExpression(0));
        assertThrows(IllegalArgumentException.class, () -> HeartbeatService.buildCronExpression(-1));
    }

    @Test
    void shouldBuildCronForNonStandardHourIntervals() {
        // 90 minutes = non-standard, falls back to minute-based
        assertEquals("*/90 * * * *", HeartbeatService.buildCronExpression(90));
    }
}

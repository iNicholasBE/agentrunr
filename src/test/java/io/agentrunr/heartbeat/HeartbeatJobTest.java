package io.agentrunr.heartbeat;

import io.agentrunr.channel.AgentConfigurer;
import io.agentrunr.channel.ChannelRegistry;
import io.agentrunr.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HeartbeatJobTest {

    @TempDir
    Path tempDir;

    private AgentRunner agentRunner;
    private AgentConfigurer agentConfigurer;
    private ChannelRegistry channelRegistry;
    private HeartbeatJob heartbeatJob;

    @BeforeEach
    void setUp() {
        agentRunner = mock(AgentRunner.class);
        agentConfigurer = mock(AgentConfigurer.class);
        channelRegistry = new ChannelRegistry();
        heartbeatJob = new HeartbeatJob(agentRunner, agentConfigurer, channelRegistry);

        when(agentConfigurer.getDefaultAgent()).thenReturn(new Agent("Test", "Test agent"));
    }

    @Test
    void shouldSkipWhenFileNotFound() {
        heartbeatJob.execute(tempDir.resolve("nonexistent.md").toString());
        verifyNoInteractions(agentRunner);
    }

    @Test
    void shouldSkipWhenNoTasks() throws IOException {
        Path heartbeatFile = tempDir.resolve("HEARTBEAT.md");
        Files.writeString(heartbeatFile, """
                # Heartbeat
                - [x] Completed task
                Some notes here.
                """);

        heartbeatJob.execute(heartbeatFile.toString());
        verifyNoInteractions(agentRunner);
    }

    @Test
    void shouldProcessTasks() throws IOException {
        Path heartbeatFile = tempDir.resolve("HEARTBEAT.md");
        Files.writeString(heartbeatFile, """
                # Heartbeat Tasks
                - [ ] Check server status
                - [x] Already done
                - [ ] Send daily report
                """);

        var response = new AgentResponse(
                List.of(ChatMessage.assistant("Checked status: all good", "Test")),
                new Agent("Test", "Test agent"),
                Map.of()
        );
        when(agentRunner.run(any(Agent.class), anyList())).thenReturn(response);

        heartbeatJob.execute(heartbeatFile.toString());

        verify(agentRunner).run(any(Agent.class), anyList());
        // Result should be routed to rest channel (default)
        assertTrue(channelRegistry.getRestChannel().hasPendingMessages());
    }

    @Test
    void shouldExtractUncompletedTasks() {
        String content = """
                # Heartbeat
                - [ ] Task one
                - [x] Done task
                * [ ] Task two
                Regular text
                - [ ] Task three
                """;

        String tasks = heartbeatJob.extractTasks(content);

        assertTrue(tasks.contains("Task one"));
        assertTrue(tasks.contains("Task two"));
        assertTrue(tasks.contains("Task three"));
        assertFalse(tasks.contains("Done task"));
        assertFalse(tasks.contains("Regular text"));
    }

    @Test
    void shouldReturnEmptyForNoTasks() {
        assertEquals("", heartbeatJob.extractTasks(""));
        assertEquals("", heartbeatJob.extractTasks(null));
        assertEquals("", heartbeatJob.extractTasks("Just some text\nno tasks here"));
    }

    @Test
    void shouldHandleAgentError() throws IOException {
        Path heartbeatFile = tempDir.resolve("HEARTBEAT.md");
        Files.writeString(heartbeatFile, "- [ ] Do something");

        when(agentRunner.run(any(Agent.class), anyList())).thenThrow(new RuntimeException("LLM error"));

        heartbeatJob.execute(heartbeatFile.toString());

        // Should not throw, error goes to channel
        assertTrue(channelRegistry.getRestChannel().hasPendingMessages());
        var messages = channelRegistry.getRestChannel().drainMessages();
        assertTrue(messages.getFirst().contains("Heartbeat Error"));
    }
}

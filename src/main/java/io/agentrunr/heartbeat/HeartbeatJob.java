package io.agentrunr.heartbeat;

import io.agentrunr.channel.ChannelRegistry;
import io.agentrunr.channel.AgentConfigurer;
import io.agentrunr.core.*;
import org.jobrunr.jobs.annotations.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The actual heartbeat job that runs as a recurring JobRunr job.
 * Reads HEARTBEAT.md, processes any tasks found, and routes results
 * to the most recently active channel.
 */
@Component
public class HeartbeatJob {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatJob.class);

    private final AgentRunner agentRunner;
    private final AgentConfigurer agentConfigurer;
    private final ChannelRegistry channelRegistry;

    public HeartbeatJob(AgentRunner agentRunner, AgentConfigurer agentConfigurer,
                        ChannelRegistry channelRegistry) {
        this.agentRunner = agentRunner;
        this.agentConfigurer = agentConfigurer;
        this.channelRegistry = channelRegistry;
    }

    @Job(name = "Heartbeat check — %0")
    public void execute(String heartbeatFilePath) {
        Path path = Path.of(heartbeatFilePath);

        if (!Files.exists(path)) {
            log.debug("Heartbeat file not found at {}, skipping", heartbeatFilePath);
            return;
        }

        String content;
        try {
            content = Files.readString(path);
        } catch (IOException e) {
            log.error("Failed to read heartbeat file: {}", heartbeatFilePath, e);
            return;
        }

        String tasks = extractTasks(content);
        if (tasks.isBlank()) {
            log.debug("No tasks in heartbeat file, HEARTBEAT_OK");
            return;
        }

        log.info("Heartbeat found tasks, processing: {}...", tasks.substring(0, Math.min(100, tasks.length())));

        try {
            Agent agent = agentConfigurer.getDefaultAgent();
            String prompt = """
                    The following tasks were found in the heartbeat file. Process them and report results.

                    Tasks:
                    %s
                    """.formatted(tasks);

            AgentResponse response = agentRunner.run(agent, List.of(ChatMessage.user(prompt)));
            String result = response.lastMessage();

            if (!result.isBlank()) {
                channelRegistry.sendToLastActive("[Heartbeat] " + result);
            }
        } catch (Exception e) {
            log.error("Error processing heartbeat tasks", e);
            channelRegistry.sendToLastActive("[Heartbeat Error] " + e.getMessage());
        }
    }

    /**
     * Extracts actionable tasks from HEARTBEAT.md content.
     * Tasks are lines starting with "- [ ]" (unchecked markdown checkboxes).
     */
    String extractTasks(String content) {
        if (content == null || content.isBlank()) return "";

        var tasks = new StringBuilder();
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            // Unchecked checkboxes are tasks
            if (trimmed.startsWith("- [ ]") || trimmed.startsWith("* [ ]")) {
                tasks.append(trimmed).append("\n");
            }
        }
        return tasks.toString().trim();
    }
}

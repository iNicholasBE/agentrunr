package io.agentrunr.cron;

import io.agentrunr.channel.AgentConfigurer;
import io.agentrunr.channel.ChannelRegistry;
import io.agentrunr.core.*;
import org.jobrunr.jobs.annotations.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Executes scheduled tasks through the agent loop.
 * Reads the task prompt from a markdown file in the tasks directory,
 * sends it to the agent, and routes the result to the last active channel.
 */
@Component
public class CronJob {

    private static final Logger log = LoggerFactory.getLogger(CronJob.class);

    private final AgentRunner agentRunner;
    private final AgentConfigurer agentConfigurer;
    private final ChannelRegistry channelRegistry;

    @Value("${agent.tasks.path:./workspace/tasks}")
    private String tasksPath;

    public CronJob(AgentRunner agentRunner, AgentConfigurer agentConfigurer,
                   ChannelRegistry channelRegistry) {
        this.agentRunner = agentRunner;
        this.agentConfigurer = agentConfigurer;
        this.channelRegistry = channelRegistry;
    }

    /**
     * Executes a scheduled task by reading its prompt from a file.
     * The task file is located at {tasksPath}/{taskName}.md.
     */
    @Job(name = "Scheduled task: %0")
    public void executeTask(String taskName) {
        Path taskFile = Path.of(tasksPath, taskName + ".md");

        if (!Files.exists(taskFile)) {
            log.warn("Task file not found: {}, skipping", taskFile);
            return;
        }

        String message;
        try {
            message = Files.readString(taskFile);
        } catch (IOException e) {
            log.error("Failed to read task file: {}", taskFile, e);
            return;
        }

        if (message.isBlank()) {
            log.debug("Task file is empty: {}, skipping", taskFile);
            return;
        }

        log.info("Executing scheduled task '{}': {}...", taskName,
                message.substring(0, Math.min(100, message.length())));

        try {
            Agent agent = agentConfigurer.getDefaultAgent();
            AgentResponse response = agentRunner.run(agent, List.of(ChatMessage.user(message)));
            String result = response.lastMessage();

            if (!result.isBlank()) {
                channelRegistry.sendToLastActive("[Scheduled: %s] %s".formatted(taskName, result));
            }
            log.info("Scheduled task '{}' completed", taskName);
        } catch (Exception e) {
            log.error("Error executing scheduled task '{}'", taskName, e);
            channelRegistry.sendToLastActive("[Scheduled Error: %s] %s".formatted(taskName, e.getMessage()));
        }
    }
}

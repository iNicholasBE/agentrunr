package io.agentrunr.config;

import io.agentrunr.core.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

import java.util.List;

/**
 * Auto-discovers MCP tools provided by Spring AI's MCP client starter
 * and registers them in the agent's ToolRegistry.
 *
 * <p>When MCP servers are configured in application.yml, Spring AI automatically
 * creates ToolCallbackProvider beans. This configuration picks them up and makes
 * them available to agents alongside native @Tool methods.</p>
 */
@Configuration
@ConditionalOnBean(ToolCallbackProvider.class)
public class McpConfig {

    private static final Logger log = LoggerFactory.getLogger(McpConfig.class);

    private final ToolRegistry toolRegistry;
    private final List<ToolCallbackProvider> toolCallbackProviders;

    public McpConfig(ToolRegistry toolRegistry, List<ToolCallbackProvider> toolCallbackProviders) {
        this.toolRegistry = toolRegistry;
        this.toolCallbackProviders = toolCallbackProviders;
    }

    @PostConstruct
    public void registerMcpTools() {
        int count = 0;
        for (ToolCallbackProvider provider : toolCallbackProviders) {
            for (ToolCallback callback : provider.getToolCallbacks()) {
                String toolName = callback.getToolDefinition().name();
                toolRegistry.registerFunctionCallback(toolName, callback);
                count++;
                log.debug("Registered MCP tool: {}", toolName);
            }
        }
        if (count > 0) {
            log.info("Registered {} MCP tools from {} providers", count, toolCallbackProviders.size());
        }
    }
}

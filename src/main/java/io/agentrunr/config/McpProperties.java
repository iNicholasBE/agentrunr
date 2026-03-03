package io.agentrunr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Configuration properties for MCP (Model Context Protocol) servers.
 *
 * <p>Binds to {@code agent.mcp.servers} in application.yml:</p>
 * <pre>
 * agent:
 *   mcp:
 *     servers:
 *       - name: personal-calendar
 *         url: ${PERSONAL_CALENDAR_MCP_URL:}
 *         password: ${PERSONAL_CALENDAR_MCP_PASSWORD:}
 *         enabled: ${PERSONAL_CALENDAR_MCP_ENABLED:false}
 *       - name: hubspot
 *         url: ${HUBSPOT_MCP_URL:}
 *         headers:
 *           Authorization: "Bearer ${HUBSPOT_MCP_TOKEN:}"
 *         enabled: ${HUBSPOT_MCP_ENABLED:false}
 *       - name: filesystem
 *         transport: stdio
 *         command: npx
 *         args:
 *           - "-y"
 *           - "@modelcontextprotocol/server-filesystem"
 *           - "/tmp"
 *         enabled: true
 * </pre>
 */
@ConfigurationProperties(prefix = "agent.mcp")
public record McpProperties(List<McpServerConfig> servers) {

    public McpProperties {
        if (servers == null) {
            servers = List.of();
        }
    }

    /**
     * Configuration for a single MCP server.
     *
     * @param name      unique name for this server (used for logging and tool prefixing)
     * @param url       SSE server URL (for SSE transport). Should end with /sse or / will be appended
     * @param password  shorthand for a "Password" header (common for n8n gateways)
     * @param headers   custom HTTP headers (e.g., Authorization: Bearer ...)
     * @param enabled   whether this server is active
     * @param transport transport type: "sse" (default) or "stdio"
     * @param command   command to run (for stdio transport)
     * @param args      command arguments (for stdio transport)
     * @param env       environment variables (for stdio transport)
     * @param requestTimeoutSeconds  timeout for tool calls (default 30)
     */
    public record McpServerConfig(
            String name,
            String url,
            String password,
            Map<String, String> headers,
            boolean enabled,
            String transport,
            String command,
            List<String> args,
            Map<String, String> env,
            Integer requestTimeoutSeconds
    ) {
        public McpServerConfig {
            if (transport == null || transport.isBlank()) {
                transport = "sse";
            }
            if (headers == null) {
                headers = Map.of();
            }
            if (args == null) {
                args = List.of();
            }
            if (env == null) {
                env = Map.of();
            }
            if (requestTimeoutSeconds == null) {
                requestTimeoutSeconds = 30;
            }
        }

        /**
         * Returns true if this is an SSE-based server.
         */
        public boolean isSse() {
            return "sse".equalsIgnoreCase(transport);
        }

        /**
         * Returns true if this is a stdio-based server.
         */
        public boolean isStdio() {
            return "stdio".equalsIgnoreCase(transport);
        }

        /**
         * Builds the merged headers map including the password shorthand.
         */
        public Map<String, String> resolvedHeaders() {
            var merged = new java.util.HashMap<>(headers);
            if (password != null && !password.isBlank()) {
                merged.putIfAbsent("Password", password);
            }
            return Map.copyOf(merged);
        }
    }
}

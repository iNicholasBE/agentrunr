package io.agentrunr.tool;

import io.agentrunr.channel.AdminController;
import io.agentrunr.core.AgentContext;
import io.agentrunr.core.AgentResult;
import io.agentrunr.core.ToolRegistry;
import io.agentrunr.setup.CredentialStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

/**
 * Registers built-in tools for shell execution, file operations, and web access.
 * All file operations are sandboxed to the configured workspace directory when
 * restrict-to-workspace is enabled.
 */
@Component
public class BuiltInTools {

    private static final Logger log = LoggerFactory.getLogger(BuiltInTools.class);

    private final ToolRegistry toolRegistry;
    private final CredentialStore credentialStore;
    private final ConfigurableApplicationContext applicationContext;
    private final PathValidator pathValidator;

    @Value("${agent.tools.shell-timeout-seconds:30}")
    private int shellTimeoutSeconds;

    @Value("${agent.tools.max-output-size:65536}")
    private int maxOutputSize;

    @Value("${agent.tools.restrict-to-workspace:true}")
    private boolean restrictToWorkspace;

    @Value("${agent.tools.workspace-dir:./workspace-defaults}")
    private String workspaceDir;

    @Value("${agent.tools.brave-api-key:}")
    private String braveApiKey;

    public BuiltInTools(ToolRegistry toolRegistry, CredentialStore credentialStore,
                        ConfigurableApplicationContext applicationContext) {
        this.toolRegistry = toolRegistry;
        this.credentialStore = credentialStore;
        this.applicationContext = applicationContext;
        this.pathValidator = new PathValidator();
    }

    @PostConstruct
    public void registerTools() {
        // Override workspace from CredentialStore if configured during onboarding
        String storedWorkspace = credentialStore.getWorkspacePath();
        if (storedWorkspace != null && !storedWorkspace.isBlank()) {
            this.workspaceDir = storedWorkspace;
        }

        toolRegistry.registerAgentTool("shell_exec",
                "Execute a shell command and return stdout/stderr. Use for system tasks, checking status, running scripts.",
                """
                {"type":"object","properties":{"command":{"type":"string","description":"Shell command to execute"},"timeout_seconds":{"type":"integer","description":"Timeout in seconds (default: 30)"}},"required":["command"]}""",
                this::shellExec);
        toolRegistry.registerAgentTool("file_read",
                "Read the contents of a file.",
                """
                {"type":"object","properties":{"path":{"type":"string","description":"File path to read"}},"required":["path"]}""",
                this::fileRead);
        toolRegistry.registerAgentTool("file_write",
                "Write content to a file. Creates parent directories if needed.",
                """
                {"type":"object","properties":{"path":{"type":"string","description":"File path to write"},"content":{"type":"string","description":"Content to write"}},"required":["path","content"]}""",
                this::fileWrite);
        toolRegistry.registerAgentTool("file_list",
                "List files and directories at a given path.",
                """
                {"type":"object","properties":{"path":{"type":"string","description":"Directory path to list (default: workspace root)"}},"required":[]}""",
                this::fileList);
        toolRegistry.registerAgentTool("web_search",
                "Search the web using Brave Search API. Returns titles, URLs, and snippets.",
                """
                {"type":"object","properties":{"query":{"type":"string","description":"Search query"}},"required":["query"]}""",
                this::webSearch);
        toolRegistry.registerAgentTool("web_fetch",
                "Fetch and extract readable content from a URL.",
                """
                {"type":"object","properties":{"url":{"type":"string","description":"URL to fetch"}},"required":["url"]}""",
                this::webFetch);
        toolRegistry.registerAgentTool("restart_application",
                "Restart the AgentRunr application. Use when configuration changes require a restart, such as new MCP servers or updated settings.",
                """
                {"type":"object","properties":{},"required":[]}""",
                this::restartApplication);
        log.info("Registered 7 built-in tools (workspace: {}, restricted: {})", workspaceDir, restrictToWorkspace);
    }

    private AgentResult shellExec(Map<String, Object> args, AgentContext ctx) {
        String command = stringArg(args, "command", "");
        if (command.isBlank()) {
            return AgentResult.of("Error: 'command' is required.");
        }

        // Security: block dangerous patterns
        if (containsDangerousCommand(command)) {
            return AgentResult.of("Error: Command blocked for security reasons.");
        }

        log.info("Executing shell command: {}", command);

        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            if (restrictToWorkspace) {
                Path wsPath = Path.of(workspaceDir).toAbsolutePath().normalize();
                Files.createDirectories(wsPath);
                pb.directory(wsPath.toFile());
            }
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean completed = process.waitFor(shellTimeoutSeconds, TimeUnit.SECONDS);

            String output;
            if (!completed) {
                process.destroyForcibly();
                output = "[Timeout after %ds]".formatted(shellTimeoutSeconds);
            } else {
                output = new String(process.getInputStream().readAllBytes());
            }

            // Truncate if needed
            if (output.length() > maxOutputSize) {
                output = output.substring(0, maxOutputSize) + "\n... [truncated at %d chars]".formatted(maxOutputSize);
            }

            int exitCode = completed ? process.exitValue() : -1;
            return AgentResult.of("Exit code: %d\n%s".formatted(exitCode, output));
        } catch (IOException | InterruptedException e) {
            log.error("Shell execution error", e);
            return AgentResult.of("Error executing command: " + e.getMessage());
        }
    }

    private AgentResult fileRead(Map<String, Object> args, AgentContext ctx) {
        String filePath = stringArg(args, "path", "");
        if (filePath.isBlank()) {
            return AgentResult.of("Error: 'path' is required.");
        }

        Path resolvedPath = resolvePath(filePath);
        if (resolvedPath == null) {
            return AgentResult.of("Error: Path '%s' is outside the workspace or invalid.".formatted(filePath));
        }

        if (!Files.exists(resolvedPath)) {
            return AgentResult.of("Error: File not found: " + resolvedPath);
        }

        if (!Files.isRegularFile(resolvedPath)) {
            return AgentResult.of("Error: Not a regular file: " + resolvedPath);
        }

        try {
            long size = Files.size(resolvedPath);
            if (size > maxOutputSize) {
                return AgentResult.of("Error: File too large (%d bytes, max %d).".formatted(size, maxOutputSize));
            }
            String content = Files.readString(resolvedPath);
            return AgentResult.of(content);
        } catch (IOException e) {
            return AgentResult.of("Error reading file: " + e.getMessage());
        }
    }

    private AgentResult fileWrite(Map<String, Object> args, AgentContext ctx) {
        String filePath = stringArg(args, "path", "");
        String content = stringArg(args, "content", "");

        if (filePath.isBlank()) {
            return AgentResult.of("Error: 'path' is required.");
        }

        Path resolvedPath = resolvePath(filePath);
        if (resolvedPath == null) {
            return AgentResult.of("Error: Path '%s' is outside the workspace or invalid.".formatted(filePath));
        }

        try {
            Files.createDirectories(resolvedPath.getParent());
            Files.writeString(resolvedPath, content);
            return AgentResult.of("Written %d bytes to %s".formatted(content.length(), resolvedPath));
        } catch (IOException e) {
            return AgentResult.of("Error writing file: " + e.getMessage());
        }
    }

    private AgentResult fileList(Map<String, Object> args, AgentContext ctx) {
        String dirPath = stringArg(args, "path", ".");

        Path resolvedPath = resolvePath(dirPath);
        if (resolvedPath == null) {
            return AgentResult.of("Error: Path '%s' is outside the workspace or invalid.".formatted(dirPath));
        }

        if (!Files.isDirectory(resolvedPath)) {
            return AgentResult.of("Error: Not a directory: " + resolvedPath);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(resolvedPath)) {
            var listing = new StringJoiner("\n");
            for (Path entry : stream) {
                String type = Files.isDirectory(entry) ? "DIR " : "FILE";
                long size = Files.isRegularFile(entry) ? Files.size(entry) : 0;
                listing.add("%s %8d %s".formatted(type, size, entry.getFileName()));
            }
            String result = listing.toString();
            return AgentResult.of(result.isEmpty() ? "(empty directory)" : result);
        } catch (IOException e) {
            return AgentResult.of("Error listing directory: " + e.getMessage());
        }
    }

    private AgentResult webSearch(Map<String, Object> args, AgentContext ctx) {
        String query = stringArg(args, "query", "");
        if (query.isBlank()) {
            return AgentResult.of("Error: 'query' is required.");
        }

        String resolvedKey = credentialStore.getApiKey("brave_api_key");
        if (resolvedKey == null || resolvedKey.isBlank()) {
            resolvedKey = braveApiKey;
        }
        if (resolvedKey != null && !resolvedKey.isBlank()) {
            return searchViaBrave(query, resolvedKey);
        }
        return AgentResult.of("Web search unavailable: BRAVE_API_KEY not configured. Query was: " + query);
    }

    private AgentResult webFetch(Map<String, Object> args, AgentContext ctx) {
        String url = stringArg(args, "url", "");
        if (url.isBlank()) {
            return AgentResult.of("Error: 'url' is required.");
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return AgentResult.of("Error: URL must start with http:// or https://");
        }

        try {
            var client = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();

            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .GET()
                    .build();

            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            String body = response.body();
            if (body.length() > maxOutputSize) {
                body = body.substring(0, maxOutputSize) + "\n... [truncated]";
            }

            return AgentResult.of("HTTP %d\n%s".formatted(response.statusCode(), body));
        } catch (Exception e) {
            return AgentResult.of("Error fetching URL: " + e.getMessage());
        }
    }

    private AgentResult restartApplication(Map<String, Object> args, AgentContext ctx) {
        log.info("Application restart requested via agent tool");
        AdminController.scheduleRestart(applicationContext);
        return AgentResult.of("Restarting AgentRunr... The application will be back shortly.");
    }

    private AgentResult searchViaBrave(String query, String apiKey) {
        try {
            String encodedQuery = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
            String url = "https://api.search.brave.com/res/v1/web/search?q=" + encodedQuery + "&count=5";

            var client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();

            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Accept", "application/json")
                    .header("X-Subscription-Token", apiKey)
                    .timeout(java.time.Duration.ofSeconds(10))
                    .GET()
                    .build();

            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return AgentResult.of("Brave search error: HTTP " + response.statusCode());
            }

            return AgentResult.of(response.body());
        } catch (Exception e) {
            return AgentResult.of("Error searching: " + e.getMessage());
        }
    }

    /**
     * Resolves a path relative to the workspace, preventing directory traversal.
     * Returns null if the path escapes the workspace (when restricted).
     */
    Path resolvePath(String filePath) {
        return pathValidator.resolve(filePath, workspaceDir, restrictToWorkspace);
    }

    /**
     * Checks for dangerous shell command patterns.
     */
    static boolean containsDangerousCommand(String command) {
        String lower = command.toLowerCase().replaceAll("\\s+", " ");
        // Block rm -rf /, fork bombs, disk wipes
        if (lower.contains("rm -rf /") && !lower.contains("rm -rf ./")) return true;
        if (lower.contains(":(){ :|:& };:")) return true;
        if (lower.contains("mkfs.")) return true;
        if (lower.contains("dd if=/dev/zero of=/dev/")) return true;
        if (lower.contains("> /dev/sda")) return true;
        if (lower.contains("chmod -r 777 /")) return true;
        return false;
    }

    private String stringArg(Map<String, Object> args, String key, String defaultValue) {
        Object val = args.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    /**
     * Validates and resolves file paths to prevent directory traversal.
     */
    static class PathValidator {

        Path resolve(String filePath, String workspaceDir, boolean restricted) {
            if (filePath == null || filePath.isBlank()) return null;

            // Block obvious traversal attempts
            if (filePath.contains("\0")) return null;

            Path wsPath = Path.of(workspaceDir).toAbsolutePath().normalize();
            Path resolved;

            if (Path.of(filePath).isAbsolute()) {
                resolved = Path.of(filePath).normalize();
            } else {
                resolved = wsPath.resolve(filePath).normalize();
            }

            if (restricted && !resolved.startsWith(wsPath)) {
                return null;
            }

            return resolved;
        }
    }
}

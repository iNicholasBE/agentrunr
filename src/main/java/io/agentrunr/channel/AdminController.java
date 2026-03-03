package io.agentrunr.channel;

import io.agentrunr.AgentRunrApplication;
import io.agentrunr.config.ClaudeCodeOAuthProvider;
import io.agentrunr.config.McpClientManager;
import io.agentrunr.config.ModelRouter;
import io.agentrunr.memory.FileMemoryStore;
import io.agentrunr.memory.Memory;
import io.agentrunr.memory.MemoryCategory;
import io.agentrunr.setup.CredentialStore;
import org.jobrunr.jobs.RecurringJob;
import org.jobrunr.storage.StorageProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin API endpoints for the web UI (provider status, sessions, etc.)
 */
@RestController
@RequestMapping("/api")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final ModelRouter modelRouter;
    private final FileMemoryStore memoryStore;
    private final Memory memory;
    private final ClaudeCodeOAuthProvider claudeCodeOAuthProvider;
    private final CredentialStore credentialStore;
    private final McpClientManager mcpClientManager;
    private final StorageProvider storageProvider;
    private final ConfigurableApplicationContext applicationContext;

    public AdminController(
            ModelRouter modelRouter,
            FileMemoryStore memoryStore,
            Memory memory,
            CredentialStore credentialStore,
            McpClientManager mcpClientManager,
            StorageProvider storageProvider,
            ConfigurableApplicationContext applicationContext,
            @Autowired(required = false) ClaudeCodeOAuthProvider claudeCodeOAuthProvider
    ) {
        this.modelRouter = modelRouter;
        this.memoryStore = memoryStore;
        this.memory = memory;
        this.credentialStore = credentialStore;
        this.mcpClientManager = mcpClientManager;
        this.storageProvider = storageProvider;
        this.applicationContext = applicationContext;
        this.claudeCodeOAuthProvider = claudeCodeOAuthProvider;
    }

    /**
     * Returns the status of configured AI providers.
     */
    @GetMapping("/providers")
    public ResponseEntity<Map<String, ProviderInfo>> getProviders() {
        Map<String, ProviderInfo> providers = new HashMap<>();

        // Check each provider by trying to resolve a known model
        providers.put("openai", checkProvider("openai", "gpt-4.1"));
        providers.put("ollama", checkProvider("ollama", "llama3.2"));
        providers.put("anthropic", checkProvider("anthropic", "claude-sonnet-4-20250514"));
        providers.put("mistral", checkProvider("mistral", "mistral-medium-latest"));

        // Claude Code OAuth status
        if (claudeCodeOAuthProvider != null) {
            var tokenInfo = claudeCodeOAuthProvider.getTokenInfo();
            if (tokenInfo.isPresent() && tokenInfo.get().valid()) {
                var info = tokenInfo.get();
                String label = info.subscriptionType() != null
                        ? "OAuth (" + info.subscriptionType() + ")"
                        : "OAuth";
                providers.put("claudeCodeOauth", new ProviderInfo(true, label));
            } else {
                providers.put("claudeCodeOauth", new ProviderInfo(false, null));
            }
        } else {
            providers.put("claudeCodeOauth", new ProviderInfo(false, null));
        }

        return ResponseEntity.ok(providers);
    }

    /**
     * Returns per-provider configuration status with masked API keys.
     */
    @GetMapping("/settings/providers")
    public ResponseEntity<Map<String, Map<String, Object>>> getProviderSettings() {
        Map<String, Map<String, Object>> result = new HashMap<>();

        for (var entry : Map.of(
                "openai", "gpt-4.1",
                "anthropic", "claude-sonnet-4-20250514",
                "mistral", "mistral-medium-latest"
        ).entrySet()) {
            String provider = entry.getKey();
            Map<String, Object> info = new HashMap<>();
            info.put("configured", credentialStore.getApiKey(provider) != null);
            info.put("maskedKey", credentialStore.getMaskedApiKey(provider));
            info.put("available", checkProvider(provider, entry.getValue()).available());
            result.put(provider, info);
        }

        // Ollama is URL-based, not key-based
        Map<String, Object> ollamaInfo = new HashMap<>();
        ollamaInfo.put("configured", checkProvider("ollama", "llama3.2").available());
        ollamaInfo.put("maskedKey", null);
        ollamaInfo.put("available", checkProvider("ollama", "llama3.2").available());
        result.put("ollama", ollamaInfo);

        return ResponseEntity.ok(result);
    }

    /**
     * Saves provider API keys to the encrypted credential store.
     * Requires an app restart for the new keys to take effect.
     */
    @PutMapping("/settings/providers")
    public ResponseEntity<Map<String, Object>> saveProviderSettings(@RequestBody Map<String, String> body) {
        try {
            for (var entry : body.entrySet()) {
                String provider = entry.getKey();
                String key = entry.getValue();
                if (List.of("openai", "anthropic", "mistral").contains(provider)) {
                    credentialStore.setApiKey(provider, (key != null && !key.isBlank()) ? key.trim() : null);
                }
            }
            credentialStore.save();
            log.info("Provider API keys updated via settings");
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Failed to save provider keys", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Lists all memory sessions.
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<String>> getSessions() {
        return ResponseEntity.ok(memoryStore.listSessions());
    }

    /**
     * Returns memory system status: total count, health, per-category counts.
     */
    @GetMapping("/memory/status")
    public ResponseEntity<Map<String, Object>> getMemoryStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("healthy", memory.healthCheck());
        status.put("totalCount", memory.count());
        status.put("coreCount", memory.list(MemoryCategory.CORE, null).size());
        status.put("dailyCount", memory.list(MemoryCategory.DAILY, null).size());
        status.put("conversationCount", memory.list(MemoryCategory.CONVERSATION, null).size());
        return ResponseEntity.ok(status);
    }

    /**
     * Returns job statistics and recurring job list for the Jobs dashboard view.
     */
    @GetMapping("/jobs/stats")
    public ResponseEntity<Map<String, Object>> getJobStats() {
        var jobStats = storageProvider.getJobStats();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", jobStats.getTotal());
        stats.put("succeeded", jobStats.getSucceeded());
        stats.put("allTimeSucceeded", jobStats.getAllTimeSucceeded());
        stats.put("failed", jobStats.getFailed());
        stats.put("scheduled", jobStats.getScheduled());
        stats.put("processing", jobStats.getProcessing());
        stats.put("enqueued", jobStats.getEnqueued());
        stats.put("recurring", jobStats.getRecurringJobs());

        var recurringJobs = storageProvider.getRecurringJobs().stream()
                .map(job -> {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("id", job.getId());
                    entry.put("name", formatJobName(job));
                    entry.put("schedule", job.getScheduleExpression());
                    entry.put("nextRun", job.getNextRun() != null ? job.getNextRun().toString() : null);
                    entry.put("createdAt", job.getCreatedAt() != null ? job.getCreatedAt().toString() : null);
                    return entry;
                })
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("stats", stats);
        result.put("recurringJobs", recurringJobs);
        return ResponseEntity.ok(result);
    }

    private String formatJobName(RecurringJob job) {
        String id = job.getId();
        if ("agent-heartbeat".equals(id)) return "Heartbeat";
        if (id.startsWith("agent-cron-")) return id.substring("agent-cron-".length());
        return id;
    }

    private ProviderInfo checkProvider(String provider, String defaultModel) {
        try {
            var resolved = modelRouter.resolve(provider + ":" + defaultModel);
            if (resolved.chatModel() != null) {
                return new ProviderInfo(true, defaultModel);
            }
        } catch (Exception e) {
            // Provider not available
        }
        return new ProviderInfo(false, null);
    }

    /**
     * Returns Claude Code OAuth token status.
     */
    @GetMapping("/claude-code-oauth")
    public ResponseEntity<Map<String, Object>> getClaudeCodeOAuthStatus() {
        Map<String, Object> status = new HashMap<>();
        if (claudeCodeOAuthProvider == null) {
            status.put("available", false);
            status.put("reason", "ClaudeCodeOAuthProvider not configured");
            return ResponseEntity.ok(status);
        }

        var tokenInfo = claudeCodeOAuthProvider.getTokenInfo();
        if (tokenInfo.isEmpty()) {
            status.put("available", false);
            status.put("reason", "No Claude Code credentials found. Run 'claude auth login'.");
            return ResponseEntity.ok(status);
        }

        var info = tokenInfo.get();
        status.put("available", true);
        status.put("valid", info.valid());
        status.put("subscriptionType", info.subscriptionType());
        status.put("rateLimitTier", info.rateLimitTier());
        status.put("scopes", info.scopes());
        status.put("expiresAt", info.expiresAt() != null ? info.expiresAt().toString() : null);
        return ResponseEntity.ok(status);
    }

    /**
     * Refreshes the cached Claude Code OAuth token.
     */
    @PostMapping("/claude-code-oauth/refresh")
    public ResponseEntity<Map<String, Object>> refreshClaudeCodeOAuth() {
        if (claudeCodeOAuthProvider == null) {
            return ResponseEntity.ok(Map.of("success", false, "reason", "Not configured"));
        }
        claudeCodeOAuthProvider.invalidateCache();
        var token = claudeCodeOAuthProvider.getAccessToken();
        return ResponseEntity.ok(Map.of("success", token.isPresent()));
    }

    /**
     * Returns current Telegram bot settings (token masked).
     */
    @GetMapping("/telegram/settings")
    public ResponseEntity<Map<String, Object>> getTelegramSettings() {
        Map<String, Object> result = new HashMap<>();
        String token = credentialStore.getApiKey("telegram_token");
        String allowedUsers = credentialStore.getApiKey("telegram_allowed_users");

        result.put("configured", token != null && !token.isBlank());
        result.put("tokenMasked", maskToken(token));
        result.put("allowedUsers", allowedUsers != null ? allowedUsers : "");
        return ResponseEntity.ok(result);
    }

    /**
     * Saves Telegram bot settings to the encrypted credential store.
     */
    @PutMapping("/telegram/settings")
    public ResponseEntity<Map<String, Object>> saveTelegramSettings(@RequestBody Map<String, String> body) {
        try {
            String token = body.get("token");
            String allowedUsers = body.get("allowedUsers");

            if (token != null && !token.isBlank()) {
                credentialStore.setApiKey("telegram_token", token);
            }
            if (allowedUsers != null) {
                credentialStore.setApiKey("telegram_allowed_users", allowedUsers.trim());
            }
            credentialStore.save();

            return ResponseEntity.ok(Map.of("success", true));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Returns status of all configured MCP servers.
     */
    @GetMapping("/mcp/servers")
    public ResponseEntity<List<McpClientManager.McpServerStatus>> getMcpServers() {
        return ResponseEntity.ok(mcpClientManager.getStatuses());
    }

    /**
     * Saves a dynamic MCP server configuration.
     */
    @PostMapping("/mcp/servers")
    public ResponseEntity<Map<String, Object>> addMcpServer(@RequestBody Map<String, String> body) {
        try {
            String name = body.get("name");
            String url = body.get("url");
            if (name == null || name.isBlank() || url == null || url.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Name and URL are required"));
            }
            mcpClientManager.saveDynamicServer(
                    name.trim(), url.trim(),
                    body.get("authHeader"), body.get("authValue"));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Removes a dynamic MCP server configuration.
     */
    @DeleteMapping("/mcp/servers/{name}")
    public ResponseEntity<Map<String, Object>> removeMcpServer(@PathVariable String name) {
        try {
            mcpClientManager.removeDynamicServer(name);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Returns status of built-in tools and integrations.
     */
    @GetMapping("/settings/tools")
    public ResponseEntity<Map<String, Object>> getToolSettings() {
        Map<String, Object> tools = new HashMap<>();

        // Web Search (Brave)
        boolean braveConfigured = credentialStore.getApiKey("brave_api_key") != null;
        tools.put("webSearch", Map.of(
                "enabled", braveConfigured,
                "configured", braveConfigured
        ));

        // Web Fetch (always available)
        tools.put("webFetch", Map.of(
                "enabled", true,
                "configured", true
        ));

        // Browser (Playwright MCP)
        boolean browserConfigured = mcpClientManager.getConfiguredServerByName("playwright-browser") != null;
        boolean browserConnected = mcpClientManager.isConnected("playwright-browser");
        tools.put("browser", Map.of(
                "enabled", browserConnected,
                "configured", browserConfigured
        ));

        return ResponseEntity.ok(tools);
    }

    /**
     * Saves tool settings (brave key, playwright toggle).
     */
    @PutMapping("/settings/tools")
    public ResponseEntity<Map<String, Object>> saveToolSettings(@RequestBody Map<String, Object> body) {
        try {
            // Handle Brave API key
            if (body.containsKey("braveApiKey")) {
                Object val = body.get("braveApiKey");
                String key = val != null ? val.toString().trim() : "";
                if (!key.isEmpty()) {
                    credentialStore.setApiKey("brave_api_key", key);
                } else {
                    credentialStore.setApiKey("brave_api_key", null);
                }
                credentialStore.save();
            }

            // Handle Playwright toggle
            if (body.containsKey("browser")) {
                boolean enable = Boolean.TRUE.equals(body.get("browser"));
                if (enable) {
                    mcpClientManager.enableServer("playwright-browser");
                } else {
                    mcpClientManager.disableServer("playwright-browser");
                }
            }

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Triggers a graceful in-process application restart.
     * Returns 200 immediately, then closes the current context and starts a new one.
     * No external process manager needed.
     */
    @PostMapping("/restart")
    public ResponseEntity<Map<String, Object>> restart() {
        log.info("Application restart requested via API");
        scheduleRestart(applicationContext);
        return ResponseEntity.ok(Map.of("success", true, "message", "Restarting AgentRunr..."));
    }

    public static void scheduleRestart(ConfigurableApplicationContext context) {
        Thread restartThread = new Thread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            log.info("Closing context for restart...");
            context.close();
            log.info("Starting new application context...");
            SpringApplication.run(AgentRunrApplication.class, AgentRunrApplication.getSavedArgs());
        }, "agentrunr-restart");
        restartThread.setDaemon(false);
        restartThread.start();
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    record ProviderInfo(boolean available, String model) {}
}

package io.agentrunr.setup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * REST controller for the web-based setup flow.
 * Provides endpoints to check configuration status and save setup data.
 */
@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private static final Logger log = LoggerFactory.getLogger(SetupController.class);
    private final CredentialStore credentialStore;
    private final WorkspaceInitializer workspaceInitializer;

    public SetupController(CredentialStore credentialStore, WorkspaceInitializer workspaceInitializer) {
        this.credentialStore = credentialStore;
        this.workspaceInitializer = workspaceInitializer;
    }

    /**
     * Returns current setup status — which providers are configured.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        String defaultWorkspace = Path.of(System.getProperty("user.home"), "agentrunr-workspace").toString();
        return ResponseEntity.ok(Map.of(
                "configured", credentialStore.isConfigured(),
                "providers", credentialStore.getProviderStatus(),
                "workspacePath", credentialStore.getWorkspacePath(),
                "defaultWorkspacePath", defaultWorkspace
        ));
    }

    /**
     * Save setup data from the web setup form.
     */
    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> save(@RequestBody Map<String, String> keys) {
        try {
            // Workspace path
            if (keys.containsKey("workspacePath") && !keys.get("workspacePath").isBlank()) {
                String workspace = keys.get("workspacePath").trim();
                if (workspace.startsWith("~")) {
                    workspace = workspace.replaceFirst("^~", System.getProperty("user.home"));
                }
                Path wsPath = Path.of(workspace).toAbsolutePath().normalize();
                Files.createDirectories(wsPath);
                credentialStore.setApiKey("workspace_path", wsPath.toString());
            }

            // API keys
            if (keys.containsKey("openai")) {
                credentialStore.setApiKey("openai", keys.get("openai"));
            }
            if (keys.containsKey("anthropic")) {
                credentialStore.setApiKey("anthropic", keys.get("anthropic"));
            }
            if (keys.containsKey("mistral")) {
                credentialStore.setApiKey("mistral", keys.get("mistral"));
            }
            if (keys.containsKey("braveApiKey")) {
                credentialStore.setApiKey("brave_api_key", keys.get("braveApiKey"));
            }

            // Telegram
            if (keys.containsKey("telegramToken")) {
                credentialStore.setApiKey("telegram_token", keys.get("telegramToken"));
            }

            // Default model
            if (keys.containsKey("defaultModel") && !keys.get("defaultModel").isBlank()) {
                credentialStore.setApiKey("agent_model", keys.get("defaultModel"));
            }

            // Initialize workspace with default files
            String workspacePath = credentialStore.getWorkspacePath();
            workspaceInitializer.initializeWorkspace(workspacePath);

            credentialStore.save();

            log.info("Setup saved via web UI, workspace initialized at {}", workspacePath);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "providers", credentialStore.getProviderStatus()
            ));
        } catch (Exception e) {
            log.error("Failed to save credentials", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
}

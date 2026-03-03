package io.agentrunr.setup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Initializes the user's workspace with default files (SOUL.md, IDENTITY.md, HEARTBEAT.md)
 * during the onboarding flow. Only writes files that don't already exist — never overwrites
 * user customizations.
 */
@Component
public class WorkspaceInitializer {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceInitializer.class);

    private static final String[] DEFAULT_FILES = {"SOUL.md", "IDENTITY.md", "HEARTBEAT.md", "mcp-servers.json"};

    private final CredentialStore credentialStore;

    public WorkspaceInitializer(CredentialStore credentialStore) {
        this.credentialStore = credentialStore;
    }

    /**
     * Initialize the workspace directory with default files and mark setup as completed.
     * Only copies files that don't already exist.
     */
    public void initializeWorkspace(String workspacePath) throws IOException {
        Path wsPath = Path.of(workspacePath);
        Files.createDirectories(wsPath);
        Files.createDirectories(wsPath.resolve("tasks"));
        Files.createDirectories(wsPath.resolve("memory"));

        for (String fileName : DEFAULT_FILES) {
            Path target = wsPath.resolve(fileName);
            if (Files.exists(target)) {
                log.debug("Workspace file already exists, skipping: {}", target);
                continue;
            }
            copyDefaultFile(fileName, target);
        }

        credentialStore.setApiKey("setup_completed", "true");
        log.info("Workspace initialized at {}", wsPath);
    }

    private void copyDefaultFile(String fileName, Path target) throws IOException {
        var resource = new ClassPathResource("defaults/" + fileName);
        if (!resource.exists()) {
            log.warn("Default template not found on classpath: defaults/{}", fileName);
            return;
        }
        try (InputStream in = resource.getInputStream()) {
            Files.copy(in, target);
            log.info("Copied default {} to {}", fileName, target);
        }
    }
}

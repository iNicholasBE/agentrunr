package io.agentrunr.channel;

import io.agentrunr.core.Agent;
import io.agentrunr.setup.CredentialStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Configures the default agent from application properties.
 * Persists settings to CredentialStore so they survive restarts.
 */
@Component
public class AgentConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AgentConfigurer.class);

    private final CredentialStore credentialStore;

    private volatile String agentName;
    private volatile String agentModel;
    private volatile String fallbackModel;
    private volatile String agentInstructions;
    private volatile int maxTurns;

    public AgentConfigurer(
            CredentialStore credentialStore,
            @Value("${agent.name:Assistant}") String agentName,
            @Value("${agent.model:gpt-4.1}") String agentModel,
            @Value("${agent.fallback-model:}") String fallbackModel,
            @Value("${agent.instructions:You are a helpful assistant.}") String agentInstructions
    ) {
        this.credentialStore = credentialStore;

        // Load persisted settings, falling back to application.yml defaults
        this.agentName = loadOrDefault("agent_name", agentName);
        this.agentModel = loadOrDefault("agent_model", agentModel);
        this.fallbackModel = loadOrDefault("agent_fallback_model", fallbackModel);
        this.agentInstructions = loadOrDefault("agent_instructions", agentInstructions);
        String storedTurns = credentialStore.getApiKey("agent_max_turns");
        this.maxTurns = (storedTurns != null) ? Integer.parseInt(storedTurns) : 10;

        log.info("Agent configured: name={}, model={}, fallback={}", this.agentName, this.agentModel, this.fallbackModel);
    }

    /**
     * Returns the default agent configured via application properties or runtime settings.
     */
    public Agent getDefaultAgent() {
        return new Agent(agentName, agentModel, agentInstructions, List.of());
    }

    /**
     * Updates agent settings at runtime and persists to CredentialStore.
     */
    public void update(String name, String model, String fallbackModel, String instructions, int maxTurns) {
        if (name != null && !name.isBlank()) this.agentName = name;
        if (model != null && !model.isBlank()) this.agentModel = model;
        this.fallbackModel = fallbackModel;
        if (instructions != null && !instructions.isBlank()) this.agentInstructions = instructions;
        if (maxTurns > 0) this.maxTurns = maxTurns;
        log.info("Agent settings updated: name={}, model={}, fallback={}", this.agentName, this.agentModel, this.fallbackModel);

        // Persist to CredentialStore
        credentialStore.setApiKey("agent_name", this.agentName);
        credentialStore.setApiKey("agent_model", this.agentModel);
        credentialStore.setApiKey("agent_fallback_model", this.fallbackModel);
        credentialStore.setApiKey("agent_instructions", this.agentInstructions);
        credentialStore.setApiKey("agent_max_turns", String.valueOf(this.maxTurns));
        try {
            credentialStore.save();
        } catch (IOException e) {
            log.error("Failed to persist agent settings: {}", e.getMessage());
        }
    }

    public String getFallbackModel() {
        return fallbackModel;
    }

    public int getMaxTurns() {
        return maxTurns;
    }

    private String loadOrDefault(String key, String defaultValue) {
        String stored = credentialStore.getApiKey(key);
        return (stored != null && !stored.isBlank()) ? stored : defaultValue;
    }
}

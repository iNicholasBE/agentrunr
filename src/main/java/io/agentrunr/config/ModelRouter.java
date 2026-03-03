package io.agentrunr.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Routes model requests to the appropriate ChatModel provider.
 *
 * <p>Agents can specify a model string like:</p>
 * <ul>
 *   <li>{@code "gpt-4.1"} — routes to OpenAI (default)</li>
 *   <li>{@code "openai:gpt-4.1-mini"} — explicit OpenAI</li>
 *   <li>{@code "ollama:llama3"} — routes to local Ollama</li>
 *   <li>{@code "anthropic:claude-sonnet-4-20250514"} — routes to Anthropic</li>
 *   <li>{@code "mistral:mistral-medium-latest"} — routes to Mistral AI</li>
 * </ul>
 *
 * <p>Providers are optional — only configured providers are available.
 * At least one provider must be configured.</p>
 */
@Component
public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private final Map<String, ChatModel> providers = new HashMap<>();
    private final ChatModel defaultModel;

    @Autowired
    public ModelRouter(
            @Autowired(required = false) @Qualifier("openAiChatModel") ChatModel openAiChatModel,
            @Autowired(required = false) @Qualifier("ollamaChatModel") ChatModel ollamaChatModel,
            @Autowired(required = false) @Qualifier("anthropicChatModel") ChatModel anthropicChatModel,
            @Autowired(required = false) @Qualifier("mistralAiChatModel") ChatModel mistralAiChatModel
    ) {
        if (openAiChatModel != null) providers.put("openai", openAiChatModel);
        if (ollamaChatModel != null) providers.put("ollama", ollamaChatModel);
        if (anthropicChatModel != null) providers.put("anthropic", anthropicChatModel);
        if (mistralAiChatModel != null) providers.put("mistral", mistralAiChatModel);

        // Pick default: OpenAI > Anthropic > Mistral > Ollama
        this.defaultModel = openAiChatModel != null ? openAiChatModel
                : anthropicChatModel != null ? anthropicChatModel
                : mistralAiChatModel != null ? mistralAiChatModel
                : ollamaChatModel;

        if (this.defaultModel == null) {
            throw new IllegalStateException("At least one AI provider must be configured (OpenAI, Ollama, Anthropic, or Mistral)");
        }

        log.info("ModelRouter initialized with providers: {} (default: {})",
                providers.keySet(),
                providers.entrySet().stream()
                        .filter(e -> e.getValue() == defaultModel)
                        .map(Map.Entry::getKey)
                        .findFirst().orElse("unknown"));
    }

    /**
     * Resolves a model string to a ChatModel and extracted model name.
     *
     * @param modelSpec model specification (e.g., "ollama:llama3", "gpt-4.1")
     * @return resolved model info
     */
    public ResolvedModel resolve(String modelSpec) {
        if (modelSpec == null || modelSpec.isBlank()) {
            return new ResolvedModel(defaultModel, "gpt-4.1", "openai");
        }

        // Check for provider prefix
        int colonIdx = modelSpec.indexOf(':');
        if (colonIdx > 0) {
            String provider = modelSpec.substring(0, colonIdx).toLowerCase();
            String modelName = modelSpec.substring(colonIdx + 1);
            ChatModel chatModel = providers.get(provider);
            if (chatModel != null) {
                return new ResolvedModel(chatModel, modelName, provider);
            }
            log.warn("Unknown provider '{}', falling back to default", provider);
        }

        // Auto-detect provider from model name
        String lower = modelSpec.toLowerCase();
        if (lower.startsWith("gpt-") || lower.startsWith("o1") || lower.startsWith("o3") || lower.startsWith("o4") || lower.startsWith("o5")) {
            return new ResolvedModel(providers.get("openai"), modelSpec, "openai");
        }
        if (lower.startsWith("claude")) {
            return new ResolvedModel(providers.get("anthropic"), modelSpec, "anthropic");
        }
        if (lower.startsWith("mistral") || lower.startsWith("ministral") || lower.startsWith("magistral") || lower.startsWith("codestral") || lower.startsWith("devstral")) {
            return new ResolvedModel(providers.get("mistral"), modelSpec, "mistral");
        }
        if (lower.startsWith("llama") || lower.startsWith("gemma") || lower.startsWith("qwen") || lower.startsWith("deepseek") || lower.startsWith("phi")) {
            return new ResolvedModel(providers.get("ollama"), modelSpec, "ollama");
        }

        // Default
        return new ResolvedModel(defaultModel, modelSpec, "openai");
    }

    /**
     * Returns the default ChatModel.
     */
    public ChatModel getDefault() {
        return defaultModel;
    }

    /**
     * Result of model resolution.
     *
     * @param chatModel the Spring AI ChatModel to use
     * @param modelName the model name to pass in options
     * @param provider  the provider name
     */
    public record ResolvedModel(ChatModel chatModel, String modelName, String provider) {}
}

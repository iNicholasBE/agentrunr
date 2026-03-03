package io.agentrunr.config;

import io.agentrunr.setup.CredentialStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Manual OpenAI bean configuration that reads the API key from the CredentialStore
 * (encrypted on disk) rather than relying on Spring AI's auto-configuration
 * which only reads from application.yml / env vars.
 */
@Configuration
public class OpenAiConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenAiConfig.class);

    @Bean
    @ConditionalOnMissingBean(name = "openAiChatModel")
    public OpenAiChatModel openAiChatModel(
            CredentialStore credentialStore,
            @Value("${spring.ai.openai.chat.options.model:gpt-4.1}") String model) {

        String apiKey = credentialStore.getApiKey("openai");
        if (apiKey == null || apiKey.isBlank()) {
            log.info("No OpenAI API key found — OpenAI provider will not be available");
            return null;
        }

        log.info("Creating OpenAI chat model from credential store (model: {})", model);
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(new SimpleApiKey(apiKey))
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(model).build())
                .build();
    }
}

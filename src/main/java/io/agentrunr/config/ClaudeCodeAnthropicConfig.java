package io.agentrunr.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;

/**
 * Auto-configures Anthropic ChatModel using Claude Code OAuth tokens.
 *
 * <p>Automatically activates when a valid Claude Code OAuth token is found
 * in the system keychain (macOS) or credentials file (Linux). No manual
 * configuration or env vars required — just run {@code claude auth login}.</p>
 *
 * <p>Claude Code OAuth tokens ({@code sk-ant-oat01-*}) require different
 * authentication than standard API keys:</p>
 * <ul>
 *   <li>{@code Authorization: Bearer <token>} instead of {@code x-api-key}</li>
 *   <li>The {@code oauth-2025-04-20} anthropic-beta flag is mandatory</li>
 * </ul>
 *
 * <p>Since Spring AI's {@code AnthropicApi} only supports {@code x-api-key},
 * we inject custom interceptors (RestClient + WebClient) that rewrite the
 * auth headers on every request.</p>
 */
@Configuration
public class ClaudeCodeAnthropicConfig {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCodeAnthropicConfig.class);

    private static final String OAUTH_BETA_FLAG = "oauth-2025-04-20";
    private static final String ANTHROPIC_BETA_HEADER = "anthropic-beta";

    @Bean("anthropicChatModel")
    @Primary
    @ConditionalOnMissingBean(name = "anthropicChatModel")
    public AnthropicChatModel anthropicChatModel(
            ClaudeCodeOAuthProvider oauthProvider,
            @Autowired(required = false) org.springframework.ai.model.tool.ToolCallingManager toolCallingManager
    ) {
        String token = oauthProvider.getAccessToken().orElse(null);
        if (token == null) {
            log.info("No Claude Code OAuth token found — Anthropic provider will not be available via OAuth. " +
                     "Run 'claude auth login' to enable.");
            return null;
        }

        String model = System.getenv().getOrDefault("CLAUDE_CODE_MODEL", "claude-sonnet-4-20250514");

        // RestClient interceptor: rewrites x-api-key → Authorization: Bearer
        var restClientBuilder = RestClient.builder()
                .requestInterceptor(new OAuthHeaderInterceptor(token));

        // WebClient filter: same rewrite for streaming (Flux) calls
        var webClientBuilder = WebClient.builder()
                .filter(oauthExchangeFilter(token));

        var api = AnthropicApi.builder()
                .apiKey("oauth-placeholder") // replaced by interceptor
                .anthropicBetaFeatures(OAUTH_BETA_FLAG)
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build();

        var options = AnthropicChatOptions.builder()
                .model(model)
                .maxTokens(4096)
                .build();

        var builder = AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(options);

        if (toolCallingManager != null) {
            builder.toolCallingManager(toolCallingManager);
        }

        log.info("Anthropic ChatModel configured via Claude Code OAuth (model: {})", model);
        return builder.build();
    }

    /**
     * RestClient interceptor that replaces {@code x-api-key} with
     * {@code Authorization: Bearer} and ensures the OAuth beta flag is present.
     */
    private static class OAuthHeaderInterceptor implements ClientHttpRequestInterceptor {

        private final String bearerToken;

        OAuthHeaderInterceptor(String bearerToken) {
            this.bearerToken = bearerToken;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                ClientHttpRequestExecution execution) throws IOException {
            HttpHeaders headers = request.getHeaders();
            headers.remove("x-api-key");
            headers.setBearerAuth(bearerToken);
            ensureOAuthBeta(headers);
            return execution.execute(request, body);
        }
    }

    /**
     * WebClient filter that does the same header rewrite for reactive/streaming calls.
     */
    private static ExchangeFilterFunction oauthExchangeFilter(String bearerToken) {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            ClientRequest modified = ClientRequest.from(clientRequest)
                    .headers(headers -> {
                        headers.remove("x-api-key");
                        headers.setBearerAuth(bearerToken);
                        ensureOAuthBeta(headers);
                    })
                    .build();
            return Mono.just(modified);
        });
    }

    /**
     * Ensures the {@code anthropic-beta} header contains the OAuth flag.
     */
    private static void ensureOAuthBeta(HttpHeaders headers) {
        var existing = headers.getFirst(ANTHROPIC_BETA_HEADER);
        if (existing == null || existing.isBlank()) {
            headers.set(ANTHROPIC_BETA_HEADER, OAUTH_BETA_FLAG);
        } else if (!existing.contains(OAUTH_BETA_FLAG)) {
            headers.set(ANTHROPIC_BETA_HEADER, existing + "," + OAUTH_BETA_FLAG);
        }
    }
}

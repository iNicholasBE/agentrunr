package io.agentrunr.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.Optional;

/**
 * Extracts Claude Code OAuth tokens from the system keychain.
 *
 * <p>Claude Code stores OAuth credentials in the macOS Keychain (or platform-specific
 * credential store) under the service name "Claude Code-credentials". This provider
 * reads those credentials and provides a valid access token for Anthropic API calls.</p>
 *
 * <p>The token format is {@code sk-ant-oat01-*} and can be used as a standard
 * Anthropic API key (x-api-key header) for inference calls.</p>
 *
 * <h3>Prerequisites</h3>
 * <ul>
 *   <li>Claude Code CLI must be installed and authenticated ({@code claude auth login})</li>
 *   <li>macOS: uses {@code security find-generic-password} to read from Keychain</li>
 *   <li>Linux: reads from {@code ~/.claude/.credentials.json} (if present)</li>
 * </ul>
 *
 * <h3>Token Refresh</h3>
 * <p>Tokens expire (see {@code expiresAt}). When expired, Claude Code must be used
 * to refresh them (e.g., run any {@code claude} command). This provider checks expiry
 * and logs warnings when tokens are close to expiring.</p>
 */
@Component
public class ClaudeCodeOAuthProvider {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCodeOAuthProvider.class);
    private static final String KEYCHAIN_SERVICE = "Claude Code-credentials";
    private static final String LINUX_CREDENTIALS_PATH = System.getProperty("user.home") + "/.claude/.credentials.json";
    private static final long EXPIRY_WARNING_MS = 30 * 60 * 1000L; // 30 minutes

    private final ObjectMapper objectMapper;
    private volatile CachedToken cachedToken;

    public ClaudeCodeOAuthProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Returns a valid Claude Code OAuth access token, or empty if unavailable.
     */
    public Optional<String> getAccessToken() {
        // Return cached token if still valid
        if (cachedToken != null && cachedToken.isValid()) {
            if (cachedToken.isExpiringSoon()) {
                log.warn("Claude Code OAuth token expires in {} minutes. Run 'claude' to refresh.",
                        cachedToken.minutesUntilExpiry());
            }
            return Optional.of(cachedToken.accessToken());
        }

        // Try to read fresh credentials
        return readCredentials().map(creds -> {
            if (creds.claudeAiOauth() == null || creds.claudeAiOauth().accessToken() == null) {
                log.warn("Claude Code credentials found but no OAuth token present");
                return null;
            }

            var oauth = creds.claudeAiOauth();
            long expiresAt = oauth.expiresAt();

            if (expiresAt > 0 && Instant.now().toEpochMilli() >= expiresAt) {
                log.warn("Claude Code OAuth token has expired. Run 'claude' to refresh.");
                return null;
            }

            cachedToken = new CachedToken(oauth.accessToken(), expiresAt);
            log.info("Claude Code OAuth token loaded (expires: {}, subscription: {}, tier: {})",
                    expiresAt > 0 ? Instant.ofEpochMilli(expiresAt) : "unknown",
                    oauth.subscriptionType() != null ? oauth.subscriptionType() : "unknown",
                    oauth.rateLimitTier() != null ? oauth.rateLimitTier() : "unknown");

            return oauth.accessToken();
        });
    }

    /**
     * Returns token metadata (subscription type, rate limit tier, scopes) if available.
     */
    public Optional<TokenInfo> getTokenInfo() {
        return readCredentials()
                .filter(c -> c.claudeAiOauth() != null)
                .map(c -> {
                    var oauth = c.claudeAiOauth();
                    return new TokenInfo(
                            oauth.subscriptionType(),
                            oauth.rateLimitTier(),
                            oauth.scopes(),
                            oauth.expiresAt() > 0 ? Instant.ofEpochMilli(oauth.expiresAt()) : null,
                            oauth.expiresAt() > 0 && Instant.now().toEpochMilli() < oauth.expiresAt()
                    );
                });
    }

    /**
     * Forces a cache refresh on next access.
     */
    public void invalidateCache() {
        cachedToken = null;
    }

    private Optional<ClaudeCredentials> readCredentials() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String json = null;

        if (os.contains("mac")) {
            json = readFromMacKeychain();
        } else if (os.contains("linux")) {
            json = readFromLinuxFile();
        } else {
            log.debug("Claude Code OAuth: unsupported OS '{}', trying keychain anyway", os);
            json = readFromMacKeychain();
        }

        if (json == null || json.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(json, ClaudeCredentials.class));
        } catch (Exception e) {
            log.warn("Failed to parse Claude Code credentials: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String readFromMacKeychain() {
        try {
            var pb = new ProcessBuilder("security", "find-generic-password", "-s", KEYCHAIN_SERVICE, "-w");
            pb.redirectErrorStream(true);
            var process = pb.start();
            String output;
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                var sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                output = sb.toString();
            }
            int exitCode = process.waitFor();
            if (exitCode != 0 || output == null || output.isBlank()) {
                log.debug("No Claude Code credentials in keychain (exit code: {})", exitCode);
                return null;
            }
            return output;
        } catch (Exception e) {
            log.debug("Failed to read from macOS keychain: {}", e.getMessage());
            return null;
        }
    }

    private String readFromLinuxFile() {
        try {
            var path = java.nio.file.Path.of(LINUX_CREDENTIALS_PATH);
            if (java.nio.file.Files.exists(path)) {
                return java.nio.file.Files.readString(path);
            }
            log.debug("No Claude Code credentials file at {}", LINUX_CREDENTIALS_PATH);
            return null;
        } catch (Exception e) {
            log.debug("Failed to read credentials file: {}", e.getMessage());
            return null;
        }
    }

    // --- Records ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ClaudeCredentials(OAuthToken claudeAiOauth) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OAuthToken(
            String accessToken,
            String refreshToken,
            long expiresAt,
            String[] scopes,
            String subscriptionType,
            String rateLimitTier
    ) {
    }

    public record TokenInfo(
            String subscriptionType,
            String rateLimitTier,
            String[] scopes,
            Instant expiresAt,
            boolean valid
    ) {
    }

    private record CachedToken(String accessToken, long expiresAt) {
        boolean isValid() {
            return expiresAt <= 0 || Instant.now().toEpochMilli() < expiresAt;
        }

        boolean isExpiringSoon() {
            return expiresAt > 0 && (expiresAt - Instant.now().toEpochMilli()) < EXPIRY_WARNING_MS;
        }

        long minutesUntilExpiry() {
            if (expiresAt <= 0) return -1;
            return Math.max(0, (expiresAt - Instant.now().toEpochMilli()) / 60000);
        }
    }
}

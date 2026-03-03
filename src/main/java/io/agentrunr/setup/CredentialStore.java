package io.agentrunr.setup;

import io.agentrunr.config.ClaudeCodeOAuthProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encrypted credential store for AI provider API keys.
 * Keys are AES-256-GCM encrypted and stored in ~/.agentrunr/credentials.enc.
 * The encryption key is derived from machine identity (hostname + username).
 */
@Component
public class CredentialStore {

    private static final Logger log = LoggerFactory.getLogger(CredentialStore.class);
    private static final Path CREDENTIALS_PATH = Path.of(System.getProperty("user.home"), ".agentrunr", "credentials.enc");
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int SALT_LENGTH = 16;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATIONS = 100_000;

    private final Map<String, String> credentials = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;
    private final ClaudeCodeOAuthProvider claudeCodeOAuthProvider;

    public CredentialStore(ClaudeCodeOAuthProvider claudeCodeOAuthProvider) {
        this.claudeCodeOAuthProvider = claudeCodeOAuthProvider;
        loadIfExists();
    }

    /**
     * Returns true if any API keys are configured (either in store or env vars).
     */
    public boolean isConfigured() {
        return hasKey("openai") || hasKey("anthropic") || hasKey("mistral")
                || hasEnvKey("OPENAI_API_KEY") || hasEnvKey("ANTHROPIC_API_KEY") || hasEnvKey("MISTRAL_API_KEY")
                || claudeCodeOAuthProvider.getAccessToken().isPresent();
    }

    /**
     * Returns the API key for a provider, checking store first, then env vars.
     */
    public String getApiKey(String provider) {
        String stored = credentials.get(provider);
        if (stored != null && !stored.isBlank()) {
            return stored;
        }
        return switch (provider) {
            case "openai" -> getEnvOrNull("OPENAI_API_KEY");
            case "anthropic" -> getEnvOrNull("ANTHROPIC_API_KEY");
            case "mistral" -> getEnvOrNull("MISTRAL_API_KEY");
            case "telegram_token" -> getEnvOrNull("TELEGRAM_BOT_TOKEN");
            case "telegram_allowed_users" -> getEnvOrNull("TELEGRAM_ALLOWED_USERS");
            case "brave_api_key" -> getEnvOrNull("BRAVE_API_KEY");
            case "workspace_path" -> getEnvOrNull("TOOLS_WORKSPACE");
            default -> null;
        };
    }

    /**
     * Returns the configured workspace path, with priority:
     * CredentialStore > TOOLS_WORKSPACE env var > default ~/agentrunr-workspace.
     */
    public String getWorkspacePath() {
        String stored = getApiKey("workspace_path");
        if (stored != null && !stored.isBlank()) {
            return stored.startsWith("~") ? stored.replaceFirst("^~", System.getProperty("user.home")) : stored;
        }
        return Path.of(System.getProperty("user.home"), "agentrunr-workspace").toString();
    }

    public boolean hasKey(String provider) {
        String key = credentials.get(provider);
        return key != null && !key.isBlank();
    }

    /**
     * Returns a masked version of the API key for display (e.g. "sk-...a1b2"),
     * or null if no key is stored.
     */
    public String getMaskedApiKey(String provider) {
        String key = getApiKey(provider);
        if (key == null || key.isBlank()) return null;
        if (key.length() <= 8) return "***";
        return key.substring(0, 3) + "..." + key.substring(key.length() - 4);
    }

    public void setApiKey(String provider, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            credentials.remove(provider);
        } else {
            credentials.put(provider, apiKey.trim());
        }
    }

    public void save() throws IOException {
        try {
            Path parent = CREDENTIALS_PATH.getParent();
            if (!Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            var props = new Properties();
            credentials.forEach(props::setProperty);

            var sb = new StringBuilder();
            for (var entry : props.entrySet()) {
                sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
            }

            byte[] plaintext = sb.toString().getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = encrypt(plaintext);
            Files.write(CREDENTIALS_PATH, encrypted);

            log.info("Credentials saved to {}", CREDENTIALS_PATH);
        } catch (Exception e) {
            throw new IOException("Failed to save credentials", e);
        }
    }

    public Map<String, Boolean> getProviderStatus() {
        var status = new java.util.HashMap<String, Boolean>();
        status.put("openai", getApiKey("openai") != null);
        status.put("anthropic", getApiKey("anthropic") != null);
        status.put("mistral", getApiKey("mistral") != null);
        status.put("claudeCodeOauth", claudeCodeOAuthProvider.getAccessToken().isPresent());
        status.put("telegram", getApiKey("telegram_token") != null);
        status.put("braveSearch", getApiKey("brave_api_key") != null);
        return status;
    }

    private void loadIfExists() {
        if (loaded) return;
        try {
            if (Files.exists(CREDENTIALS_PATH)) {
                byte[] encrypted = Files.readAllBytes(CREDENTIALS_PATH);
                byte[] plaintext = decrypt(encrypted);
                String content = new String(plaintext, StandardCharsets.UTF_8);

                for (String line : content.split("\n")) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        credentials.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                    }
                }
                log.info("Loaded {} credentials from {}", credentials.size(), CREDENTIALS_PATH);
            }
        } catch (Exception e) {
            log.warn("Failed to load credentials from {}: {}", CREDENTIALS_PATH, e.getMessage());
        }
        loaded = true;
    }

    private SecretKey deriveKey(byte[] salt) throws Exception {
        String machineId = InetAddress.getLocalHost().getHostName() + ":" + System.getProperty("user.name");
        KeySpec spec = new PBEKeySpec(machineId.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    private byte[] encrypt(byte[] plaintext) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        byte[] iv = new byte[GCM_IV_LENGTH];
        random.nextBytes(iv);

        SecretKey key = deriveKey(salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);

        // Format: salt (16) + iv (12) + ciphertext
        byte[] result = new byte[salt.length + iv.length + ciphertext.length];
        System.arraycopy(salt, 0, result, 0, salt.length);
        System.arraycopy(iv, 0, result, salt.length, iv.length);
        System.arraycopy(ciphertext, 0, result, salt.length + iv.length, ciphertext.length);
        return result;
    }

    private byte[] decrypt(byte[] data) throws Exception {
        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(data, 0, salt, 0, SALT_LENGTH);
        System.arraycopy(data, SALT_LENGTH, iv, 0, GCM_IV_LENGTH);

        byte[] ciphertext = new byte[data.length - SALT_LENGTH - GCM_IV_LENGTH];
        System.arraycopy(data, SALT_LENGTH + GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

        SecretKey key = deriveKey(salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return cipher.doFinal(ciphertext);
    }

    private String getEnvOrNull(String name) {
        String val = System.getenv(name);
        if (val != null && !val.isBlank() && !val.equals("sk-placeholder")) {
            return val;
        }
        return null;
    }

    private boolean hasEnvKey(String name) {
        return getEnvOrNull(name) != null;
    }
}

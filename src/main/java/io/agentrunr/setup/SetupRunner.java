package io.agentrunr.setup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.Console;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Interactive CLI setup that runs on first start or with --setup flag.
 * Prompts for workspace, API keys, Telegram, and default model, then stores them encrypted.
 */
@Component
public class SetupRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SetupRunner.class);
    private final CredentialStore credentialStore;
    private final Environment environment;

    public SetupRunner(CredentialStore credentialStore, Environment environment) {
        this.credentialStore = credentialStore;
        this.environment = environment;
    }

    @Override
    public void run(String... args) throws Exception {
        boolean forceSetup = Arrays.asList(args).contains("--setup");

        if (!forceSetup && credentialStore.isConfigured()) {
            return;
        }

        Console console = System.console();
        if (console == null) {
            if (!credentialStore.isConfigured()) {
                log.info("No AI providers configured. Visit http://localhost:{}/setup to configure.",
                        environment.getProperty("server.port", "8090"));
            }
            return;
        }

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║  Welcome to AgentRunr!                       ║");
        System.out.println("║  Let's get you set up.                       ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println();

        // --- 1. Workspace folder ---
        String defaultWorkspace = Path.of(System.getProperty("user.home"), "agentrunr-workspace").toString();
        String workspace = console.readLine("Workspace folder [%s]: ", defaultWorkspace);
        if (workspace == null || workspace.isBlank()) {
            workspace = defaultWorkspace;
        }
        workspace = workspace.trim();
        if (workspace.startsWith("~")) {
            workspace = workspace.replaceFirst("^~", System.getProperty("user.home"));
        }
        Path wsPath = Path.of(workspace).toAbsolutePath().normalize();
        Files.createDirectories(wsPath);
        credentialStore.setApiKey("workspace_path", wsPath.toString());
        System.out.println("  Workspace: " + wsPath);
        System.out.println();

        // --- 2. AI Provider API keys ---
        System.out.println("Configure at least one AI provider:");
        System.out.println();

        // Track which providers are configured for model selection later
        List<String[]> configuredModels = new ArrayList<>();

        // OpenAI
        String openaiKey = console.readLine("  OpenAI API key (or Enter to skip): ");
        if (openaiKey != null && !openaiKey.isBlank()) {
            credentialStore.setApiKey("openai", openaiKey.trim());
            configuredModels.add(new String[]{"openai:gpt-4.1", "OpenAI (gpt-4.1)"});
            System.out.println("    OpenAI saved");
        }

        // Anthropic
        String anthropicKey = console.readLine("  Anthropic API key (or Enter to skip): ");
        if (anthropicKey != null && !anthropicKey.isBlank()) {
            credentialStore.setApiKey("anthropic", anthropicKey.trim());
            configuredModels.add(new String[]{"anthropic:claude-sonnet-4-20250514", "Anthropic (claude-sonnet-4)"});
            System.out.println("    Anthropic saved");
        }

        // Mistral
        String mistralKey = console.readLine("  Mistral AI API key (or Enter to skip): ");
        if (mistralKey != null && !mistralKey.isBlank()) {
            credentialStore.setApiKey("mistral", mistralKey.trim());
            configuredModels.add(new String[]{"mistral:mistral-medium-latest", "Mistral (mistral-medium-latest)"});
            System.out.println("    Mistral saved");
        }

        System.out.println();

        // --- 3. Default model selection ---
        if (!configuredModels.isEmpty()) {
            System.out.println("Select your default model:");
            for (int i = 0; i < configuredModels.size(); i++) {
                System.out.println("  " + (i + 1) + ". " + configuredModels.get(i)[1]);
            }
            String modelChoice = console.readLine("Choice [1]: ");
            int choice = 1;
            if (modelChoice != null && !modelChoice.isBlank()) {
                try {
                    choice = Integer.parseInt(modelChoice.trim());
                } catch (NumberFormatException ignored) {
                }
            }
            if (choice < 1 || choice > configuredModels.size()) choice = 1;
            String selectedModel = configuredModels.get(choice - 1)[0];
            credentialStore.setApiKey("agent_model", selectedModel);
            System.out.println("    Default model: " + configuredModels.get(choice - 1)[1]);
            System.out.println();
        }

        // --- 4. Telegram bot token ---
        String telegramToken = console.readLine("Telegram Bot token (or Enter to skip): ");
        if (telegramToken != null && !telegramToken.isBlank()) {
            credentialStore.setApiKey("telegram_token", telegramToken.trim());
            System.out.println("    Telegram bot saved");
            System.out.println();
        }

        // --- Save ---
        if (credentialStore.isConfigured()) {
            credentialStore.save();
            System.out.println("Credentials encrypted and saved to ~/.agentrunr/credentials.enc");
            System.out.println("Starting AgentRunr...");
        } else {
            System.out.println();
            System.out.println("No API keys configured. You can set them later:");
            System.out.println("   Run with --setup flag");
            System.out.println("   Visit http://localhost:" +
                    environment.getProperty("server.port", "8090") + "/setup");
            System.out.println("   Set OPENAI_API_KEY or ANTHROPIC_API_KEY env vars");
        }
        System.out.println();
    }
}

package io.agentrunr.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ModelRouterTest {

    private final ChatModel openai = mock(ChatModel.class);
    private final ChatModel ollama = mock(ChatModel.class);
    private final ChatModel anthropic = mock(ChatModel.class);
    private final ChatModel mistral = mock(ChatModel.class);

    @Test
    void shouldRegisterAllProviders() {
        var router = new ModelRouter(openai, ollama, anthropic, mistral);
        assertEquals(openai, router.resolve("openai:gpt-4.1").chatModel());
        assertEquals(ollama, router.resolve("ollama:llama3").chatModel());
        assertEquals(anthropic, router.resolve("anthropic:claude-sonnet-4-20250514").chatModel());
        assertEquals(mistral, router.resolve("mistral:mistral-medium-latest").chatModel());
    }

    @Test
    void shouldDefaultToOpenAi() {
        var router = new ModelRouter(openai, ollama, anthropic, mistral);
        assertEquals(openai, router.getDefault());
    }

    @Test
    void shouldDefaultToAnthropicWhenNoOpenAi() {
        var router = new ModelRouter(null, ollama, anthropic, mistral);
        assertEquals(anthropic, router.getDefault());
    }

    @Test
    void shouldDefaultToMistralWhenNoOpenAiOrAnthropic() {
        var router = new ModelRouter(null, ollama, null, mistral);
        assertEquals(mistral, router.getDefault());
    }

    @Test
    void shouldDefaultToOllamaWhenOnlyOllama() {
        var router = new ModelRouter(null, ollama, null, null);
        assertEquals(ollama, router.getDefault());
    }

    @Test
    void shouldThrowWhenNoProviders() {
        assertThrows(IllegalStateException.class, () -> new ModelRouter(null, null, null, null));
    }

    @Test
    void shouldAutoDetectMistralModels() {
        var router = new ModelRouter(openai, ollama, anthropic, mistral);
        assertEquals("mistral", router.resolve("mistral-medium-latest").provider());
        assertEquals("mistral", router.resolve("ministral-8b").provider());
        assertEquals("mistral", router.resolve("magistral-medium").provider());
        assertEquals("mistral", router.resolve("codestral-latest").provider());
        assertEquals("mistral", router.resolve("devstral-small").provider());
    }

    @Test
    void shouldAutoDetectOtherProviders() {
        var router = new ModelRouter(openai, ollama, anthropic, mistral);
        assertEquals("openai", router.resolve("gpt-4.1").provider());
        assertEquals("anthropic", router.resolve("claude-sonnet-4-20250514").provider());
        assertEquals("ollama", router.resolve("llama3.2").provider());
    }

    @Test
    void shouldNotRouteMistralToOllama() {
        var router = new ModelRouter(openai, ollama, anthropic, mistral);
        // Mistral models should route to mistral, not ollama
        assertNotEquals(ollama, router.resolve("mistral-medium-latest").chatModel());
        assertEquals(mistral, router.resolve("mistral-medium-latest").chatModel());
    }

    @Test
    void shouldHandleExplicitProviderPrefix() {
        var router = new ModelRouter(openai, ollama, anthropic, mistral);
        var resolved = router.resolve("mistral:mistral-large-latest");
        assertEquals(mistral, resolved.chatModel());
        assertEquals("mistral-large-latest", resolved.modelName());
        assertEquals("mistral", resolved.provider());
    }

    @Test
    void shouldFallbackToDefaultForUnknownModel() {
        var router = new ModelRouter(openai, ollama, anthropic, mistral);
        var resolved = router.resolve("unknown-model-xyz");
        assertEquals(openai, resolved.chatModel());
        assertEquals("openai", resolved.provider());
    }

    @Test
    void shouldFallbackToDefaultForBlankSpec() {
        var router = new ModelRouter(openai, ollama, anthropic, mistral);
        var resolved = router.resolve("");
        assertEquals(openai, resolved.chatModel());
    }

    @Test
    void shouldFallbackToDefaultForNullSpec() {
        var router = new ModelRouter(openai, ollama, anthropic, mistral);
        var resolved = router.resolve(null);
        assertEquals(openai, resolved.chatModel());
    }
}

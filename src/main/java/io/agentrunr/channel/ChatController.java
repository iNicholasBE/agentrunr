package io.agentrunr.channel;

import io.agentrunr.core.*;
import io.agentrunr.memory.FileMemoryStore;
import io.agentrunr.memory.MemoryAutoSaver;
import io.agentrunr.memory.SQLiteMemoryStore;
import io.agentrunr.security.InputSanitizer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API channel for the agent.
 * Provides a simple chat endpoint that accepts messages and returns agent responses.
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final AgentRunner agentRunner;
    private final AgentConfigurer agentConfigurer;
    private final InputSanitizer inputSanitizer;
    private final FileMemoryStore memoryStore;
    private final SQLiteMemoryStore sqliteMemory;
    private final MemoryAutoSaver memoryAutoSaver;

    public ChatController(AgentRunner agentRunner, AgentConfigurer agentConfigurer,
                          InputSanitizer inputSanitizer, FileMemoryStore memoryStore,
                          SQLiteMemoryStore sqliteMemory, MemoryAutoSaver memoryAutoSaver) {
        this.agentRunner = agentRunner;
        this.agentConfigurer = agentConfigurer;
        this.inputSanitizer = inputSanitizer;
        this.memoryStore = memoryStore;
        this.sqliteMemory = sqliteMemory;
        this.memoryAutoSaver = memoryAutoSaver;
    }

    /**
     * Chat endpoint. Sends a message to the default agent and returns the response.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponseDto> chat(@RequestBody ChatRequestDto request) {
        Agent defaultAgent = agentConfigurer.getDefaultAgent();
        // Allow overriding model per request
        Agent agent = (request.model() != null && !request.model().isBlank())
                ? new Agent(defaultAgent.name(), request.model(), defaultAgent.instructions(), defaultAgent.toolNames())
                : defaultAgent;

        inputSanitizer.validateMessageCount(request.messages().size());

        String sessionId = (request.sessionId() != null && !request.sessionId().isBlank())
                ? request.sessionId()
                : UUID.randomUUID().toString();

        List<ChatMessage> messages = request.messages().stream()
                .map(m -> new ChatMessage(
                        ChatMessage.Role.valueOf(m.role().toUpperCase()),
                        inputSanitizer.sanitize(m.content()),
                        null,
                        null
                ))
                .toList();

        // Log the latest user message to both file and SQLite memory
        if (!messages.isEmpty()) {
            ChatMessage lastUser = messages.getLast();
            if (lastUser.role() == ChatMessage.Role.USER) {
                memoryStore.appendMessage(sessionId, "user", lastUser.content());
                sqliteMemory.storeConversationMessage(sessionId, "user", lastUser.content());
                // Auto-save important facts from user messages
                memoryAutoSaver.scanAndSave(lastUser.content(), sessionId);
            }
        }

        // Load persisted context and merge with request context
        AgentContext context = new AgentContext(memoryStore.loadContext(sessionId));
        if (request.contextVariables() != null) {
            context.merge(request.contextVariables());
        }
        // Set session_id so memory tools can scope to this session
        context.set("session_id", sessionId);

        AgentResponse response = agentRunner.run(agent, messages, context, request.maxTurns() > 0 ? request.maxTurns() : 10);

        // Persist assistant response to both file and SQLite memory
        memoryStore.appendMessage(sessionId, "assistant", response.lastMessage());
        memoryStore.saveContext(sessionId, response.contextVariables());
        sqliteMemory.storeConversationMessage(sessionId, "assistant", response.lastMessage());

        return ResponseEntity.ok(new ChatResponseDto(
                response.lastMessage(),
                response.activeAgent().name(),
                response.contextVariables(),
                sessionId
        ));
    }

    /**
     * Streaming chat endpoint. Returns SSE events with tokens as they arrive.
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody ChatRequestDto request) {
        Agent defaultAgent = agentConfigurer.getDefaultAgent();
        Agent agent = (request.model() != null && !request.model().isBlank())
                ? new Agent(defaultAgent.name(), request.model(), defaultAgent.instructions(), defaultAgent.toolNames())
                : defaultAgent;

        inputSanitizer.validateMessageCount(request.messages().size());

        String sessionId = (request.sessionId() != null && !request.sessionId().isBlank())
                ? request.sessionId()
                : UUID.randomUUID().toString();

        List<ChatMessage> messages = request.messages().stream()
                .map(m -> new ChatMessage(
                        ChatMessage.Role.valueOf(m.role().toUpperCase()),
                        inputSanitizer.sanitize(m.content()),
                        null,
                        null
                ))
                .toList();

        // Log the latest user message to both stores
        if (!messages.isEmpty()) {
            ChatMessage lastUser = messages.getLast();
            if (lastUser.role() == ChatMessage.Role.USER) {
                memoryStore.appendMessage(sessionId, "user", lastUser.content());
                sqliteMemory.storeConversationMessage(sessionId, "user", lastUser.content());
                memoryAutoSaver.scanAndSave(lastUser.content(), sessionId);
            }
        }

        // Load persisted context and merge with request context
        AgentContext context = new AgentContext(memoryStore.loadContext(sessionId));
        if (request.contextVariables() != null) {
            context.merge(request.contextVariables());
        }
        context.set("session_id", sessionId);

        // Emit session ID as first event, then stream tokens, then persist
        Flux<ServerSentEvent<String>> sessionEvent = Flux.just(
                ServerSentEvent.<String>builder().event("session").data(sessionId).build()
        );
        Flux<String> tokenStream = agentRunner.runStreaming(agent, messages, context,
                request.maxTurns() > 0 ? request.maxTurns() : 10);

        // Collect full response for memory persistence
        StringBuilder fullResponse = new StringBuilder();
        Flux<ServerSentEvent<String>> persistingStream = tokenStream
                .map(token -> {
                    fullResponse.append(token);
                    return ServerSentEvent.<String>builder().data(token).build();
                })
                .doOnComplete(() -> {
                    memoryStore.appendMessage(sessionId, "assistant", fullResponse.toString());
                    memoryStore.saveContext(sessionId, context.toMap());
                    sqliteMemory.storeConversationMessage(sessionId, "assistant", fullResponse.toString());
                });

        return sessionEvent.concatWith(persistingStream);
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "agentrunr"));
    }

    /**
     * Update agent settings at runtime.
     */
    @PutMapping("/settings")
    public ResponseEntity<Map<String, String>> updateSettings(@RequestBody SettingsDto settings) {
        agentConfigurer.update(settings.agentName(), settings.model(), settings.fallbackModel(), settings.instructions(), settings.maxTurns());
        return ResponseEntity.ok(Map.of("status", "saved"));
    }

    /**
     * Get current agent settings.
     */
    @GetMapping("/settings")
    public ResponseEntity<SettingsDto> getSettings() {
        Agent agent = agentConfigurer.getDefaultAgent();
        return ResponseEntity.ok(new SettingsDto(agent.name(), agent.resolvedModel(), agentConfigurer.getFallbackModel(), agent.instructions(), agentConfigurer.getMaxTurns()));
    }

    public record SettingsDto(String agentName, String model, String fallbackModel, String instructions, int maxTurns) {}

    // --- DTOs ---

    public record ChatRequestDto(
            List<MessageDto> messages,
            Map<String, String> contextVariables,
            int maxTurns,
            String model,
            String sessionId
    ) {}

    public record MessageDto(String role, String content) {}

    public record ChatResponseDto(String response, String agent, Map<String, String> contextVariables, String sessionId) {}
}

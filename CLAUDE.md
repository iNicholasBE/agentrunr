# CLAUDE.md — Agent Onboarding Guide for AgentRunr

## What Is This?

AgentRunr is a Java-native AI agent runtime — a port of OpenAI Swarm's agent orchestration pattern to Java using Spring Boot + Spring AI + JobRunr. Think "enterprise-safe Java agent orchestration with persistent memory and MCP integration."

**Repo:** https://github.com/iNicholasBE/agentrunr

## Tech Stack

| Component | Version | Notes |
|-----------|---------|-------|
| Java | 21 | Records, pattern matching, text blocks |
| Spring Boot | 3.4.3 | |
| Spring AI | 1.0.0 (GA) | Maven Central, no milestone repos needed |
| JobRunr | 8.4.2 | Background job scheduling |
| MCP SDK | 0.10.0 | `io.modelcontextprotocol.sdk:mcp` |
| SQLite | — | Memory (brain.db) + JobRunr (jobrunr.db) |
| Maven | 3.9+ | |

## Build & Run

```bash
# Ensure Java 21+ is on PATH
export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || echo $JAVA_HOME)
export PATH=$JAVA_HOME/bin:$PATH

# Build + test
mvn clean verify

# Run (port 8090 by default)
mvn spring-boot:run
```

**You MUST export JAVA_HOME and PATH before running mvn.** The system default Java is not 21.

## Project Structure

```
io.agentrunr
├── AgentRunrApplication.java          # Main entry point
├── core/                              # Agent orchestration (Swarm port)
│   ├── Agent.java                     # Agent record (name, model, instructions, tools)
│   ├── AgentRunner.java               # Core loop: send → tool calls → recurse
│   ├── AgentContext.java              # Conversation context with variables
│   ├── AgentResult.java               # Result record
│   ├── AgentResponse.java             # Response wrapper
│   ├── ChatMessage.java               # Message record
│   ├── SystemPromptBuilder.java       # Assembles identity + memory + tools + safety
│   └── ToolRegistry.java             # Central tool registration (3 tiers)
├── config/
│   ├── ModelRouter.java               # Routes to OpenAI/Ollama/Anthropic per request
│   ├── McpProperties.java            # Config binding for agent.mcp.servers list
│   ├── McpClientManager.java         # MCP lifecycle: connect, health, tool registration
│   ├── McpConfig.java                # Auto-discovers MCP ToolCallbackProviders
│   ├── ClaudeCodeOAuthProvider.java   # (DO NOT USE — Anthropic terms prohibit it)
│   └── ClaudeCodeAnthropicConfig.java # (DO NOT USE — same reason)
├── setup/
│   ├── CredentialStore.java           # AES-256-GCM encrypted key storage (~/.agentrunr/)
│   ├── SetupRunner.java              # CLI first-run prompts (or --setup flag)
│   ├── SetupController.java          # REST API for web setup
│   ├── SetupInterceptor.java         # Redirects to /setup if unconfigured
│   └── SetupWebConfig.java           # Web config for setup flow
├── channel/
│   ├── Channel.java                   # Channel interface (NOT sealed — extensible)
│   ├── ChatController.java           # REST /api/chat + SSE streaming
│   ├── TelegramChannel.java          # Telegram long-polling integration
│   ├── AdminController.java          # Settings, providers, sessions, MCP API
│   ├── AgentConfigurer.java          # Runtime agent configuration
│   └── ChannelRegistry.java          # Multi-channel management
├── heartbeat/
│   ├── HeartbeatService.java         # JobRunr-powered periodic task checking
│   └── HeartbeatJob.java             # Reads HEARTBEAT.md, triggers agent
├── cron/
│   ├── CronService.java              # JobRunr cron scheduling for agent tasks
│   ├── CronJob.java                  # @Job executed by JobRunr
│   ├── CronTools.java                # @Tool methods for cron management
│   └── ScheduledTask.java            # Task record (cron/interval/one-shot)
├── tool/
│   ├── BuiltInTools.java             # shell_exec, file_read/write/list, web_search, web_fetch
│   ├── SampleTools.java              # Example tools (weather, time, calculate)
│   ├── JobRunrToolExecutor.java      # Executes tools via JobRunr @Job
│   └── ToolExecutionService.java     # Tool execution orchestration
├── memory/
│   ├── Memory.java                    # Memory interface (store/recall/get/list/forget/count)
│   ├── MemoryCategory.java           # CORE / DAILY / CONVERSATION enum
│   ├── MemoryEntry.java              # Record: id, key, content, category, timestamp, score
│   ├── SQLiteMemoryStore.java        # Primary store: FTS5, BM25 ranking, brain.db
│   ├── FileMemoryStore.java          # Secondary: daily logs, context.json, MEMORY.md
│   ├── MemoryAutoSaver.java          # Passive fact extraction from user messages
│   └── MemoryTools.java              # Agent-callable: memory_store/recall/forget/list
└── security/
    ├── SecurityConfig.java            # Spring Security config
    ├── ApiKeyFilter.java             # API key authentication
    └── InputSanitizer.java           # Input validation/sanitization
```

## Key Architecture Decisions

1. **Spring AI over LangChain4j** — Native Spring integration, built-in @Tool + MCP support
2. **ModelRouter with @Nullable injection** — Each provider bean is optional (OpenAI, Anthropic, Mistral, Ollama); router selects per-request
3. **Channel interface is NOT sealed** — Designed for extensibility (add Discord, Slack, etc.)
4. **CredentialStore over env vars** — Interactive auth setup like Claude Code; AES-256-GCM encrypted on disk; takes priority over env vars
5. **Generic MCP server config** — `agent.mcp.servers` list in application.yml supports SSE + stdio transports, custom headers, password shorthand. `McpClientManager` handles lifecycle, health, and ToolRegistry integration
6. **Heartbeat + Cron as killer feature** — JobRunr provides persistent distributed scheduling, unlike in-memory cron
7. **Dual-layer memory** — SQLite (FTS5 search) + file system (human-readable logs) for redundancy and different access patterns
8. **System prompt assembly** — `SystemPromptBuilder` reads identity files + memory context every turn, making the agent always context-aware

## Memory System

### Architecture

Two storage backends working together:

**SQLiteMemoryStore** (primary) — `./data/memory/brain.db`
- FTS5 virtual table for full-text search with BM25 ranking
- Three categories: `CORE` (permanent), `DAILY` (session-scoped), `CONVERSATION` (ephemeral)
- Upsert semantics: storing to an existing key updates it
- Session-aware: memories can be global (null sessionId) or session-scoped
- WAL journal mode for concurrent access

**FileMemoryStore** (secondary) — `./data/memory/`
- `MEMORY.md` — curated long-term notes
- `sessions/{id}/yyyy-MM-dd.md` — daily conversation logs in markdown
- `sessions/{id}/context.json` — session context variables

### Memory Interface

```java
void store(String key, String content, MemoryCategory category, String sessionId)
List<MemoryEntry> recall(String query, int limit, String sessionId)  // FTS5 ranked
Optional<MemoryEntry> get(String key)           // exact key lookup
List<MemoryEntry> list(MemoryCategory category, String sessionId)
boolean forget(String key)
int count()
boolean healthCheck()
```

### MemoryAutoSaver

Passively scans user messages using regex patterns and stores extracted facts as CORE memories:
- Names: `"my name is X"` → `user_name`
- Preferences: `"I prefer X"` → `preference_{slug}`
- Location: `"I live in X"` → `location`
- Workplace: `"I work at X"` → `workplace`
- Timezone: `"my timezone is X"` → `timezone`
- Explicit: `"remember that X"` → `user_note_{timestamp}`
- Rules: `"always/never X"` → `rule_{slug}`

Only saves if key doesn't already exist. Fact length must be 3–200 chars.

### MemoryTools (Agent-Callable)

Registered in ToolRegistry via `@PostConstruct`:
- `memory_store` — key, content, category params
- `memory_recall` — query, limit params (returns FTS5 ranked results)
- `memory_forget` — key param
- `memory_list` — category param

### How Memory Flows Through the System

1. **User message arrives** (ChatController/TelegramChannel)
2. Message logged to both FileMemoryStore and SQLiteMemoryStore
3. `MemoryAutoSaver.scanAndSave()` extracts facts from user message
4. `SystemPromptBuilder` runs `memory.recall(userMessage)` for relevant memories
5. `SystemPromptBuilder` runs `memory.list(CORE)` for all permanent facts
6. Both are injected into the system prompt for the LLM
7. Agent response logged to both stores
8. Context variables persisted to `context.json`

## System Prompt Assembly

`SystemPromptBuilder` assembles the system prompt every turn in this order:

1. **Identity** — reads `SOUL.md`, `IDENTITY.md`, `USER.md`, `AGENTS.md` from the user's workspace
2. **Instructions** — the agent's configured base instructions
3. **Agent name** — `"Your name is {name}."`
4. **Relevant Memories** — FTS5 recall based on user's current message (score > 0.1)
5. **Core Facts** — all CORE-category memories (up to 10)
6. **Available Tools** — all tool names from ToolRegistry
7. **Safety Guidelines** — no secret leaking, dangerous command caution, etc.
8. **Runtime** — current timestamp + OS name

Identity files are cached after first load. Call `refreshIdentity()` to force reload.

Files larger than 20,000 chars are truncated with `[truncated]` suffix.

## MCP Architecture

### Configuration

MCP servers are configured in `application.yml` under `agent.mcp.servers`:

```yaml
agent:
  mcp:
    servers:
      # SSE transport with password shorthand
      - name: my-calendar
        url: ${MY_CALENDAR_MCP_URL:}
        password: ${MY_CALENDAR_MCP_PASSWORD:}
        enabled: ${MY_CALENDAR_MCP_ENABLED:false}

      # SSE transport with custom headers
      - name: my-crm
        url: ${MY_CRM_MCP_URL:}
        headers:
          Authorization: "Bearer ${MY_CRM_MCP_TOKEN:}"
        enabled: ${MY_CRM_MCP_ENABLED:false}

      # Stdio transport (local process)
      - name: playwright-browser
        transport: stdio
        command: npx
        args: ["-y", "@playwright/mcp", "--headless"]
        enabled: ${PLAYWRIGHT_ENABLED:false}
```

### Key Classes

- **`McpProperties`** — `@ConfigurationProperties` record binding. `McpServerConfig` nested record with `isSse()`, `isStdio()`, `resolvedHeaders()` helpers. Defaults: transport=sse, timeout=30s.
- **`McpClientManager`** — Manages lifecycle (connect/health/reconnect/shutdown). Stores `ManagedServer` instances in `ConcurrentHashMap`. Registers tools into `ToolRegistry` as functionCallbacks.
- **`McpConfig`** — Auto-discovers any Spring AI `ToolCallbackProvider` beans and registers their tools. Complements `McpClientManager`.

### How Tools Flow

1. `McpClientManager.init()` connects each enabled server via SSE or stdio transport
2. `SyncMcpToolCallbackProvider(client)` creates tool callbacks
3. Each tool registered via `toolRegistry.registerFunctionCallback(name, callback)`
4. `AgentRunner` resolves tools via `ToolRegistry.getToolCallbacks()`
5. Tool execution: `ToolCallback.call(jsonArgs)` → MCP SDK → remote server
6. Priority: AgentTools > Spring @Tool > MCP function callbacks

### Dynamic Servers

Added at runtime via `POST /api/mcp/servers`. Persisted in `CredentialStore` as:
- `mcp_servers` — comma-separated list of names
- `mcp_{name}_url`, `mcp_{name}_auth_header`, `mcp_{name}_auth_value`

### MCP Gotchas

1. **URI resolution:** `URI.resolve("/sse")` with leading slash resets to root. `McpClientManager.parseSseUri()` handles this — URLs ending with `/sse` split into base + relative endpoint
2. **SDK 0.10.0 constructors deprecated:** All public constructors of `HttpClientSseClientTransport` are deprecated. Use `HttpClientSseClientTransport.builder(baseUri)` with `.requestBuilder()` for custom headers
3. **Credentials via env vars only** — Never hardcode in application.yml
4. **Stdio transport** uses `ServerParameters.builder(command).args(...)` + `StdioClientTransport`

## ToolRegistry (3 Tiers)

```java
Map<String, ToolCallback> toolCallbacks      // Spring @Tool beans
Map<String, ToolCallback> functionCallbacks   // MCP server tools
Map<String, AgentTool>    agentTools          // Built-in + custom tools
```

**Execution priority:** agentTools → toolCallbacks → functionCallbacks

**Registration sources:**
- `BuiltInTools` → `registerAgentTool()` (shell_exec, file_*, web_*)
- `MemoryTools` → `registerAgentTool()` (memory_store/recall/forget/list)
- `CronTools` → `registerAgentTool()` (schedule_task, list/cancel)
- Spring `@Tool` beans → `registerToolCallback()` (auto-discovered)
- `McpClientManager` → `registerFunctionCallback()` (from MCP servers)
- `McpConfig` → `registerFunctionCallback()` (from Spring AI providers)

## Scheduling System

### Heartbeat

`HeartbeatService` registers a recurring JobRunr job (`"agent-heartbeat"`) that reads `HEARTBEAT.md` and processes unchecked tasks (`- [ ]` lines). Results sent via `ChannelRegistry.sendToLastActive()`.

Config: `agent.heartbeat.enabled`, `agent.heartbeat.interval-minutes`, `agent.heartbeat.file`

### Cron

`CronService` manages an in-memory registry of scheduled tasks backed by JobRunr:
- Cron expressions, interval-based, or one-shot scheduling
- `CronTools` registers three agent-callable tools: `schedule_task`, `list_scheduled_tasks`, `cancel_scheduled_task`
- `CronJob` is the `@Job` that runs the agent with the stored message prompt

## Spring AI 1.0.0 GA API Notes

These changed from M6 → GA. Don't use old patterns:

- `FunctionCallback` → `ToolCallback`
- `callback.getName()` → `callback.getToolDefinition().name()`
- Artifact IDs renamed:
  - `spring-ai-openai-spring-boot-starter` → `spring-ai-starter-model-openai`
  - `spring-ai-ollama-spring-boot-starter` → `spring-ai-starter-model-ollama`
  - `spring-ai-anthropic-spring-boot-starter` → `spring-ai-starter-model-anthropic`
  - `spring-ai-mcp-client-spring-boot-starter` → `spring-ai-starter-mcp-client`
  - `spring-ai-mistralai-spring-boot-starter` → `spring-ai-starter-model-mistral-ai`

## Configuration

All secrets via env vars (see `application.yml`):
- `OPENAI_API_KEY` — OpenAI
- `ANTHROPIC_API_KEY` + `ANTHROPIC_ENABLED=true` — Anthropic
- `OLLAMA_BASE_URL` + `OLLAMA_ENABLED=true` — Ollama
- `TELEGRAM_BOT_TOKEN` + `TELEGRAM_ENABLED=true` — Telegram bot
- `MISTRAL_API_KEY` + `MISTRAL_ENABLED=true` — Mistral AI
- `AGENT_API_KEY` — API authentication
- `BRAVE_API_KEY` — Web search tool
- `AGENT_MEMORY_PATH` — Memory storage directory (default: `./data/memory`)

Or use the interactive setup: run with `--setup` flag or visit `/setup` in browser.

## Tests

251 tests across 29 test classes. All must pass before committing:
```bash
mvn clean verify
```

**Test gotchas:**
- Tests using `@WebMvcTest` need `@MockBean` for `SQLiteMemoryStore`
- `FileMemoryStore` uses `${memory.path}` (not `${agent.memory.path}`)
- `AgentRunner` constructor takes `@Nullable SystemPromptBuilder`
- `ChatController` depends on both `FileMemoryStore` and `SQLiteMemoryStore`
- `AdminController` depends on `Memory` interface (not concrete class)

## What's NOT Done Yet (Roadmap)

- [ ] Semantic memory (Spring AI vector store + embeddings)
- [ ] Autonomy levels (readonly/supervised/full modes)
- [ ] Observability (Micrometer → Prometheus)
- [ ] Discord + Slack channels
- [ ] Config hot-reload + health diagnostics
- [ ] Encryption at rest for memory/workspace data
- [ ] End-to-end testing with real API keys

## Don'ts

- **DO NOT use Claude Code OAuth tokens** — Anthropic terms (updated 2026-02-19) explicitly prohibit use in other products/services. The `ClaudeCodeOAuthProvider` and `ClaudeCodeAnthropicConfig` exist but should not be enabled.
- **DO NOT hardcode secrets** in application.yml or commit them
- **DO NOT use `FunctionCallback`** — it's the old Spring AI M6 API, use `ToolCallback`
- **DO NOT use Spring AI milestone repos** — 1.0.0 is GA in Maven Central

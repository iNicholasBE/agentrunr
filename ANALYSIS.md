# AgentRunr — Analysis & Build Plan

## Vision
A Java-native AI agent runtime inspired by OpenAI Swarm (agent core) and the *Claw ecosystem (nanobot, PicoClaw, ZeroClaw) for infrastructure patterns. Uses **Spring Boot + Spring AI + JobRunr** to demonstrate how JobRunr can power production-grade AI agent workloads.

## Source Analysis

### OpenAI Swarm (Agent Core — ~250 lines)
The entire Swarm framework is 3 files:

**types.py** — Data model:
- `Agent`: name, model, instructions (str or callable), functions (list of callables), tool_choice, parallel_tool_calls
- `Response`: messages, agent (optional handoff target), context_variables
- `Result`: value (string), agent (optional handoff), context_variables

**core.py** — The runtime (~200 lines of logic):
- `Swarm.run(agent, messages, context_variables)` — the main loop:
  1. Build system message from agent.instructions
  2. Convert agent.functions to JSON tool schemas (via function_to_json)
  3. Call LLM (chat completions API) with messages + tools
  4. If no tool calls → return response (done)
  5. If tool calls → dispatch each:
     - Look up function by name
     - Inject context_variables if function accepts them
     - Call function, get Result
     - If Result.agent is set → handoff (switch active agent)
  6. Append tool results to history, loop back to step 2
  7. Max turns guard prevents infinite loops

**util.py** — Helpers:
- `function_to_json(func)` — Python introspection to generate JSON schema from function signature + type hints
- `merge_chunk` / `merge_fields` — streaming response assembly
- `debug_print` — timestamped debug output

**Key insight:** Swarm is stateless between `run()` calls. All state lives in the messages array. This maps perfectly to JobRunr's model where each job execution is independent.

### Spring AI Equivalents (no porting needed)
- `function_to_json` → Spring AI's `@Tool` annotation does this automatically via reflection
- `ChatCompletions API` → Spring AI's `ChatClient` (provider-agnostic)
- `tool_choice` → Spring AI's `ToolCallingChatOptions`
- MCP → `spring-ai-mcp-client-spring-boot-starter` auto-discovers MCP tools

### What We Actually Need to Port
Only the **agent loop logic** from core.py:
1. Agent definition (instructions + tools + model)
2. The run loop (LLM call → tool dispatch → handoff → repeat)
3. Context variables (shared state across tool calls)
4. Agent handoff mechanism

### Infrastructure Patterns (from nanobot/PicoClaw)
- **Memory:** File-based markdown (MEMORY.md pattern)
- **Config:** JSON/YAML agent definitions
- **Channels:** Modular I/O (start with REST + Telegram)
- **Scheduled tasks:** Cron/recurring agent runs

## Architecture

```
agentrunr/
├── src/main/java/io/jobrunr/agent/
│   ├── AgentApplication.java              # Spring Boot entry point
│   ├── core/
│   │   ├── Agent.java                     # Agent definition (record)
│   │   ├── AgentResult.java               # Tool return type (record)
│   │   ├── AgentResponse.java             # Run response (record)
│   │   ├── AgentRunner.java               # The Swarm run loop
│   │   └── AgentContext.java              # Context variables
│   ├── tool/
│   │   ├── ToolRegistry.java              # Discovers @Tool beans + MCP tools
│   │   └── JobRunrToolExecutor.java       # Executes tools as JobRunr jobs
│   ├── memory/
│   │   └── FileMemoryStore.java           # Simple file-based memory
│   ├── channel/
│   │   ├── Channel.java                   # Interface
│   │   ├── RestChannel.java              # REST API channel
│   │   └── TelegramChannel.java          # Telegram bot
│   ├── config/
│   │   └── AgentConfig.java              # YAML-driven agent config
│   └── security/
│       ├── SecurityConfig.java            # Spring Security setup
│       └── ApiKeyFilter.java             # API key auth
├── src/main/resources/
│   ├── application.yml
│   └── agents/                            # Agent definition files
│       └── default-agent.yml
├── src/test/java/io/jobrunr/agent/
│   ├── core/
│   │   ├── AgentRunnerTest.java
│   │   ├── AgentHandoffTest.java
│   │   └── ContextVariablesTest.java
│   ├── tool/
│   │   ├── ToolRegistryTest.java
│   │   └── JobRunrToolExecutorTest.java
│   └── integration/
│       ├── AgentIntegrationTest.java
│       └── McpIntegrationTest.java
├── pom.xml
├── ANALYSIS.md                            # This file
├── PROGRESS.md                            # Build progress log
└── README.md
```

## Build Phases

### Phase 1: Core Agent Loop (MVP)
- [ ] Project scaffold (Spring Boot 3.4 + Spring AI 1.0 + JobRunr)
- [ ] Agent record (name, model, instructions, tools)
- [ ] AgentRunner — the run loop using Spring AI ChatClient
- [ ] Tool calling via Spring AI @Tool
- [ ] Agent handoff support
- [ ] Context variables
- [ ] Unit tests for all core components
- [ ] REST API endpoint (/api/chat)

### Phase 2: JobRunr Integration
- [ ] Tool calls execute as JobRunr background jobs
- [ ] Job result callback → feeds back into agent loop
- [ ] Scheduled agent runs (recurring jobs)
- [ ] JobRunr dashboard accessible
- [ ] Tests for async tool execution

### Phase 3: MCP Support
- [ ] Spring AI MCP client configuration
- [ ] MCP tools auto-discovered alongside @Tool methods
- [ ] MCP server connectivity tests

### Phase 4: Memory & Channels
- [ ] File-based memory store
- [ ] Telegram channel integration
- [ ] Memory persistence across sessions

### Phase 5: Security & Polish
- [ ] API key authentication
- [ ] Rate limiting
- [ ] Input sanitization
- [ ] Comprehensive integration tests
- [ ] Documentation

## Security Considerations
- API keys stored in environment variables, never in code
- Input sanitization on all user messages
- Tool execution sandboxing via JobRunr (separate thread pool)
- Rate limiting on API endpoints
- CORS configuration
- No arbitrary code execution tools in default config

## Dependencies
- Spring Boot 3.4.x
- Spring AI 1.0.x (GA)
- spring-ai-openai-spring-boot-starter (or anthropic)
- spring-ai-mcp-client-spring-boot-starter
- JobRunr 7.x (spring-boot-3-starter)
- Spring Security
- Spring Web
- JUnit 5 + Mockito + Spring Boot Test
- Testcontainers (for integration tests)

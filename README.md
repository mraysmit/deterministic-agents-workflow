# deterministic-agents-workflow — Vert.x 5 Agent + MCP Server (Java)

A **multi-module Vert.x 5 project** demonstrating the hybrid deterministic-workflow / LLM-agent pattern with a built-in [Model Context Protocol](https://modelcontextprotocol.io/) (MCP) server.

- **Deterministic processor** handles known failures via pluggable handlers.
- **Agent boundary** only triggers when the failure is unknown/ambiguous.
- The agent's decisions come from a **real LLM** via function-calling. There is
  no offline or rule-based fallback — see [A note on stubs](#a-note-on-stubs).
- **Tools are self-describing** with JSON Schema metadata aligned with MCP.
- **Built-in MCP server** exposes tools via Streamable HTTP (2025-03-26) and legacy HTTP+SSE (2024-11-05) transports.

## Modules

| Module | ArtifactId | Purpose |
|---|---|---|
| `mcp-server` | `mcp-server` | Standalone MCP server verticle — Streamable HTTP + legacy SSE transports, `Tool` / `ToolRegistry` interfaces. Zero agent coupling. |
| `agent-core` | `agent-core` | Reusable agent infrastructure — LLM interfaces, memory, agent runner, deterministic processor, config records, HTTP API verticle. |
| `agent-app` | `agent-app` | Trade-failure domain logic — handlers, tools, factories, YAML-driven bootstrap (`MainVerticle`). Produces the fat JAR. |

```
deterministic-agents-workflow/
├── mcp-server/         # dev.mars.mcp
├── agent-core/         # dev.mars.agent  (core abstractions)
├── agent-app/          # dev.mars.agent  (domain + bootstrap)
└── pom.xml             # parent POM (dev.mars:deterministic-agents-workflow)
```

## Requirements
- Java 21+
- Maven 3.9+

## Build + Run
```bash
mvn -q clean test
mvn -q package -DskipTests
java -jar agent-app/target/agent-app-0.1.0-SNAPSHOT-fat.jar
```

Running the application requires an API key — there is no offline mode:

```bash
export OPENAI_API_KEY=sk-...
```

`mvn test` does **not** need one. The suite is split into two tiers: hermetic
tests run always, and the tests that need a live model skip themselves unless
`OPENAI_API_KEY` is set. See [Test Coverage](#test-coverage).

- Agent HTTP API: **http://localhost:8080** (override with `http.port` in `pipeline.yaml`)
- MCP server: **http://localhost:3001** (override with `mcp.port` in `pipeline.yaml`)

## Health Check
```bash
curl http://localhost:8080/health
```

## Try it

Examples 1 is deterministic and repeatable. Everything from 2 onwards runs
through the model, so the step counts and tool choices described below are
**typical outcomes, not guarantees** — the agent decides at runtime and will
not repeat itself exactly. Each needs `OPENAI_API_KEY` set.

### 1) Known deterministic workflow (no agent)
```bash
curl -s -X POST http://localhost:8080/trade/failures \
  -H 'content-type: application/json' \
  -d '{"tradeId":"T-100","reason":"Missing ISIN"}' | jq
```

### 2) Unknown failure (agent classification kicks in)
```bash
curl -s -X POST http://localhost:8080/trade/failures \
  -H 'content-type: application/json' \
  -d '{"tradeId":"T-200","reason":"LEI not found in registry"}' | jq
```

### 3) Sanctions screening — adaptive false-positive analysis
```bash
curl -s -X POST http://localhost:8080/trade/failures \
  -H 'content-type: application/json' \
  -d '{"tradeId":"T-800","reason":"OFAC screening flag on counterparty"}' | jq
```
`LookupTool` returns screening data for this trade ID — jurisdiction, sector,
entity structure, client history, name-match quality. What the agent makes of
it is up to the model: typically it weighs those factors, classifies, and picks
a notification channel proportionate to the assessed risk.

### 4) Multi-leg cascade — structural reasoning
```bash
curl -s -X POST http://localhost:8080/trade/failures \
  -H 'content-type: application/json' \
  -d '{"tradeId":"T-900","reason":"Linked trade cascade failure on swap leg"}' | jq
```
The fixture describes a $50M swap structure with 4 linked trades, an SSI/BIC
format error as root cause, and $42,500/bp unhedged DV01. The interesting
question — whether the model classifies on impact rather than on the triviality
of the root cause — is genuinely open on each run.

### 5) Counterparty credit event — cross-domain portfolio analysis
```bash
curl -s -X POST http://localhost:8080/trade/failures \
  -H 'content-type: application/json' \
  -d '{"tradeId":"T-1000","reason":"Counterparty credit downgrade to CCC+"}' | jq
```
The fixture holds a 5-notch downgrade across 4 positions ($19.8M gross, $5.1M
net after ISDA netting and CSA collateral), including an IRS position at
-$1.2M MtM that provides netting benefit. Whether the model spots that
unwinding it would *increase* net exposure is the thing worth watching.

### 6) Settlement amount mismatch — FX root-cause hypothesis
```bash
curl -s -X POST http://localhost:8080/trade/failures \
  -H 'content-type: application/json' \
  -d '{"tradeId":"T-500","reason":"Settlement amount mismatch detected"}' | jq
```

### 7) Duplicate trade detection — risk exposure analysis
```bash
curl -s -X POST http://localhost:8080/trade/failures \
  -H 'content-type: application/json' \
  -d '{"tradeId":"T-600","reason":"Possible duplicate trade execution"}' | jq
```

### 8) Regulatory deadline — compliance urgency
```bash
curl -s -X POST http://localhost:8080/trade/failures \
  -H 'content-type: application/json' \
  -d '{"tradeId":"T-700","reason":"Regulatory T+1 deadline at risk"}' | jq
```

### 9) MCP — Streamable HTTP transport (2025-03-26)

```bash
# Initialize a session
curl -s -X POST http://localhost:3001/mcp \
  -H 'content-type: application/json' \
  -H 'Accept: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"curl","version":"1.0"}}}'

# List tools (include Mcp-Session-Id from the initialize response)
curl -s -X POST http://localhost:3001/mcp \
  -H 'content-type: application/json' \
  -H 'Accept: application/json' \
  -H 'Mcp-Session-Id: <sessionId>' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'

# Invoke a tool
curl -s -X POST http://localhost:3001/mcp \
  -H 'content-type: application/json' \
  -H 'Accept: application/json' \
  -H 'Mcp-Session-Id: <sessionId>' \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"case.raiseTicket","arguments":{"tradeId":"T-300","category":"ReferenceData","summary":"MCP test"}}}'
```

### 10) MCP — Legacy SSE transport (2024-11-05)
```bash
# Open SSE connection and note the sessionId from the endpoint event
curl -N http://localhost:3001/sse &

# Send tools/list (replace <sessionId> with value from endpoint event)
curl -s -X POST "http://localhost:3001/message?sessionId=<sessionId>" \
  -H 'content-type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

---

## Architecture

```
HTTP POST ──► HttpApiVerticle ──► DeterministicFailureProcessorVerticle
                                        │
                         ┌──────────────┴──────────────┐
                  Known reason?                  Unknown reason?
                         │                              │
                  FailureHandler                 AgentRunnerVerticle
                  (lookup-enrich,                       │
                   escalate, ...)              LlmClient.decideNext()
                         │                              │
                  Returns result              ┌─────────▼─────────┐
                                              │  { intent, tool,  │
                                              │    args, stop }   │
                                              └─────────┬─────────┘
                                                        │
                                               Tool.invoke(args, ctx)
                                                        │
                                              EventSinkVerticle (events.out)
```

All wiring — addresses, handlers, tools, LLM client, MCP server — is
externalised to `pipeline.yaml` and resolved at startup via factory classes.

---

## Scenario Data

The trade IDs used in the examples above seed realistic reference data through
`LookupTool` — sanctions screening results for the T-800 range, multi-leg swap
structures for T-900, portfolio and netting positions for T-1000, and so on.
That data is a **fixture**: it is what the tools return when queried.

What the agent *does* with it is not scripted. Which tools it calls, in what
order, how it classifies severity, and whether it escalates are all decided by
the model at runtime, so **the same input will not produce identical output run
to run**. Step counts and tool sequences vary.

### A note on stubs

An earlier version of this project shipped a `StubLlmClient` that replayed
hardcoded command chains for these trade IDs, and a README section claiming
they demonstrated "capabilities requiring a real LLM." They did not — they
demonstrated keyword matching against a `switch`. Worse, 39 of the then-186
tests existed only to assert that those hardcoded scripts returned their own
contents, which inflated the coverage numbers while the actual LLM integration
sat behind two `assertNotNull` calls.

All of it has been removed, and `llm.type` no longer accepts anything but
`openai`. The application cannot be configured to fake its own core function.

The one sanctioned substitute is inline, in test code only: `LlmClient` is a
`@FunctionalInterface`, so a test that needs to drive the runner with a
specific command supplies a lambda —

```java
LlmClient llm = (event, state) -> Future.succeededFuture(
    new JsonObject().put("intent", "CALL_TOOL").put("tool", "case.raiseTicket")...);
```

That is how `AgentRunnerVerticleTest` exercises step limits, unknown-tool
rejection, and error paths. It carries no domain rules and makes no claim to
be reasoning.

### The `reasoning` field

Every step includes a `reasoning` field carrying the model's own explanation
for the decision. It is chain-of-thought from the LLM, not canned text:

```json
{
  "intent": "CALL_TOOL",
  "tool": "comms.notify",
  "args": { "channel": "email", "team": "Compliance + Legal", ... },
  "reasoning": "Choosing email over PagerDuty — the trade is already on hold, so there is no settlement risk requiring an immediate page...",
  "stop": true
}
```

---

## MCP Server

The `mcp-server` module provides `McpServerVerticle` — a standalone MCP server
that exposes the agent's tools via the [Model Context Protocol](https://modelcontextprotocol.io/).

### Transports

| Transport | Spec Version | Endpoints |
|---|---|---|
| **Streamable HTTP** | 2025-03-26 | `POST /mcp`, `GET /mcp`, `DELETE /mcp` |
| **Legacy HTTP+SSE** | 2024-11-05 | `GET /sse`, `POST /message?sessionId=<id>` |

### Streamable HTTP (2025-03-26)

The primary transport. A single `/mcp` endpoint handles all interactions:
- **POST** — JSON-RPC requests/notifications. Responds with `application/json` or `text/event-stream` based on the client's `Accept` header. Supports JSON-RPC batch requests.
- **GET** — Opens an SSE stream for server-initiated messages.
- **DELETE** — Terminates the session.

Session management uses the `Mcp-Session-Id` header — assigned after `initialize`, required on all subsequent requests.

### Legacy HTTP+SSE (2024-11-05)

Backwards-compatible transport for older MCP clients:
- **GET `/sse`** — Opens an SSE connection; server sends an `endpoint` event with the URL for posting messages.
- **POST `/message?sessionId=<id>`** — Receives JSON-RPC requests; responses are delivered via the SSE stream.

### Supported JSON-RPC Methods

| Method | Description |
|---|---|
| `initialize` | Capability negotiation — returns protocol version, server info, and capabilities |
| `ping` | Health check — returns empty result |
| `tools/list` | Returns all registered tools with name, description, and `inputSchema` |
| `tools/call` | Invokes a tool by name with the supplied arguments |

### Configuration

```yaml
mcp:
  enabled: true    # set to false to disable the MCP server
  port: 3001       # TCP port (0 for random in tests)
  basePath: ""     # URL prefix for MCP endpoints
```

### Connecting from Claude Desktop

Add this to your Claude Desktop MCP settings:
```json
{
  "mcpServers": {
    "deterministic-agents-workflow": {
      "transport": "sse",
      "url": "http://localhost:3001/sse"
    }
  }
}
```

---

## How This Demonstrates MCP

This project implements MCP directly — `McpServerVerticle` exposes tools via
standard MCP transports with JSON-RPC 2.0. The core abstractions map onto MCP
concepts:

### MCP Concept Mapping

| MCP Concept | This Project | Where |
|---|---|---|
| **Tool** — a callable capability with a name, description, and input schema | `Tool` interface with `name()`, `description()`, `schema()` | `mcp-server` — `tool/Tool.java` |
| **`tools/list`** — server advertises available tools with their schemas | `ToolRegistry` holds all registered tools; each tool self-describes via `schema()` | `mcp-server` — `tool/ToolRegistry.java` |
| **`tools/call`** — client invokes a tool by name with JSON arguments | `McpServerVerticle` dispatches `{tool, args}` to the matching `Tool` | `mcp-server` — `McpServerVerticle.java` |
| **`inputSchema`** — JSON Schema describing a tool's parameters | `Tool.schema()` returns JSON Schema 2020-12 compatible `JsonObject` | `mcp-server` — `tool/Tool.java` |
| **Tool allow-listing** — security boundary controlling which tools an agent can use | `ToolRegistry` acts as the allow-list; unknown tools are rejected | `mcp-server` — `tool/ToolRegistry.java` |
| **LLM function-calling** — LLM decides which tool to call and with what arguments | `LlmClient.decideNext()` returns `{intent, tool, args}` commands | `agent-core` — `llm/LlmClient.java` |
| **Agent loop** — iterative tool calls until the task is complete | `AgentRunnerVerticle.runLoop()` loops until `stop: true` or step limit | `agent-core` — `runner/AgentRunnerVerticle.java` |

### What Each Tool Exposes (MCP-Ready)

Every `Tool` implementation provides three pieces of metadata that an MCP
server advertises in a `tools/list` response:

```java
public interface Tool {
    String name();             // MCP tool name
    String description();      // MCP tool description
    JsonObject schema();       // MCP inputSchema (JSON Schema)
    Future<JsonObject> invoke(JsonObject args, AgentContext ctx);  // MCP tools/call
}
```

For example, `RaiseTicketTool` exposes:

```json
{
  "name": "case.raiseTicket",
  "description": "Creates a support ticket for a trade failure requiring manual investigation.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "tradeId":  { "type": "string", "description": "The trade identifier" },
      "category": { "type": "string", "description": "Ticket category, e.g. ReferenceData" },
      "summary":  { "type": "string", "description": "Brief summary of the issue" },
      "detail":   { "type": "string", "description": "Detailed description of the failure" }
    },
    "required": ["tradeId", "category", "summary"]
  }
}
```

### The Command Protocol

The LLM communicates with the agent runner using a simple JSON
command format that mirrors MCP's `tools/call` request:

```json
{
  "intent": "CALL_TOOL",
  "tool": "case.raiseTicket",
  "args": {
    "tradeId": "T-200",
    "category": "ReferenceData",
    "summary": "Counterparty LEI issue"
  },
  "stop": true
}
```

| Field | Purpose | MCP Equivalent |
|---|---|---|
| `intent` | Action type (only `CALL_TOOL` supported) | Implicit in `tools/call` |
| `tool` | Tool name to invoke | `tools/call` → `name` |
| `args` | Tool arguments | `tools/call` → `arguments` |
| `stop` | Whether to end the agent loop | Application-level control |

### What's Remaining for Full MCP

| Gap | What's Needed |
|---|---|
| **Resources** | MCP resources for exposing contextual data (e.g. trade details) |
| **Prompts** | MCP prompt templates for structured LLM interactions |
| **External tool servers** | Connect to out-of-process MCP servers instead of in-process `Tool` instances |

The key architectural decision — that **the runner does not choose tools** but
instead validates and dispatches commands from an external decision-maker (LLM)
— is exactly the MCP client pattern. Swapping in a different LLM backend with
MCP-compatible function-calling requires no changes to the tool layer.

### Path to Further MCP Adoption

1. **Add an `McpToolAdapter`** — a `Tool` implementation that wraps an MCP
   server connection, translating `invoke()` calls into MCP `tools/call`
   JSON-RPC requests. This lets external MCP tools sit alongside in-process
   tools transparently.

2. ~~**Expose tools as an MCP server**~~ — ✅ Done. `McpServerVerticle`
   serves `tools/list` and `tools/call` over Streamable HTTP and legacy SSE.

3. ~~**Replace `StubLlmClient`**~~ — ✅ Done. `OpenAiLlmClient`
   (`llm/OpenAiLlmClient.java`) is fully implemented: it sends each tool's
   `schema()` as an OpenAI function definition and maps the model's
   `tool_calls` response back onto the command protocol. Select it with
   `llm.type: openai` in `pipeline.yaml`.

---

## What to Look At

| Component | Module | Purpose |
|---|---|---|
| `McpServerVerticle` | `mcp-server` | MCP server — Streamable HTTP + legacy SSE transports |
| `Tool` interface | `mcp-server` | Self-describing tools with `name()`, `description()`, `schema()` |
| `ToolRegistry` | `mcp-server` | Tool registration and allow-listing |
| `DeterministicFailureProcessorVerticle` | `agent-core` | Normal service — fast, predictable, strategy-pattern handlers |
| `AgentRunnerVerticle` | `agent-core` | Step-limited agent runner + tool dispatch (MCP client pattern) |
| `HttpApiVerticle` | `agent-core` | HTTP ingress with schema validation |
| `OpenAiLlmClient` | `agent-core` | Real LLM via Chat Completions + function-calling |
| `MainVerticle` | `agent-app` | Application bootstrap — deploys all verticles from YAML config |
| `pipeline.yaml` | `agent-app` | All configuration externalised — addresses, handlers, tools, LLM, MCP |
| `config/` package | `agent-app` | Factory classes for YAML-driven wiring |

## Configuration

All pipeline wiring is in `agent-app/src/main/resources/pipeline.yaml`. Override the
config file path at startup:

```bash
java -Dpipeline.config=my-config.yaml -jar agent-app/target/agent-app-0.1.0-SNAPSHOT-fat.jar
```

See `PipelineConfigLoader` for loading details and `MainVerticle` for how
factories resolve YAML aliases to concrete classes.

## Test Coverage

**147 tests across three modules**, split into two tiers.

**Hermetic tier — runs on every build.** No API key, no network, no cost.
Covers everything except the model's own decisions.

**Live tier — gated on `OPENAI_API_KEY`.** Annotated
`@EnabledIfEnvironmentVariable`, so it skips silently when the variable is
absent and runs automatically when it is present. Assertions here are
deliberately structural — that the agent path routed, terminated, and produced
a result — never that a specific tool was chosen or specific wording returned,
because a real model does not repeat itself.

> **Known gap.** `OpenAiLlmClient`'s request construction, `tool_calls`
> parsing, and tool-name sanitisation round-trip are still only covered by a
> constructor check and an unreachable-endpoint check. A hermetic test against
> a canned HTTP response on localhost would close this without needing a key.
> Faking the *transport* to test the parser is legitimate; faking the *model's
> decisions* is what this project just removed.

Coverage by module:

**mcp-server**
- `ToolRegistry` immutability, lookup, and registration
- `McpServerVerticle` — Streamable HTTP transport, legacy SSE transport, JSON-RPC dispatch, session management, batch requests, tool invocation, and error handling

**agent-core**
- Config record validation and defensive copying (`HttpConfig`, `McpConfig`, `LlmConfig`, `HandlerConfig`, `SchemaConfig`)
- `OpenAiLlmClient` construction and unreachable-endpoint handling (see the
  known gap above)
- `InMemoryMemoryStore` lifecycle and case isolation
- `DeterministicFailureProcessorVerticle` routing and handler dispatch
- `AgentRunnerVerticle` step limits, tool dispatch, and error paths — driven by
  inline `LlmClient` lambdas, not by any stub implementation
- `HttpApiVerticle` routing and request validation
- `EventSinkVerticle` event bus publishing

**agent-app**
- Factory resolution (handlers, tools, LLM clients) with error cases
- YAML config parsing and missing-resource handling
- Handler behaviour (`LookupEnrichHandler`, `EscalateHandler`)
- Tool invocation and schema metadata (`RaiseTicketTool`, `PublishEventTool`)
- `LookupTool` — scenario-specific fixture data for all trade ID ranges (sanctions screening, multi-leg structures, credit/netting data)
- `LlmClientFactory` — `openai` param validation, and that `stub` is rejected
- Smoke test, deterministic path (hermetic); agent path (live tier)

## Logging

The application uses `java.util.logging` (JUL) configured via
`agent-app/src/main/resources/logging.properties`, loaded by `MainVerticle` at
startup.

| Setting | Value |
|---------|-------|
| Console | `INFO` to `System.err` |
| File | Rolling files at `logs/vertx-agent-%g.log` |
| Max file size | 10 MB |
| Retained files | 10 |
| Format | ISO-8601 timestamp, e.g. `2026-03-02T10:08:01.937+0800 INFO [class] message` |

Log files are written to the `logs/` directory (created automatically).
Override at runtime with a system property:

```bash
java -Djava.util.logging.config.file=my-logging.properties -jar agent-app/target/agent-app-0.1.0-SNAPSHOT-fat.jar
```

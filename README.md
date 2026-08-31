# deterministic-agents-workflow — Vert.x 5 Agent + mcp-vertx (Java)

A **multi-module Vert.x 5 project** demonstrating the hybrid deterministic-workflow / LLM-agent pattern with the sibling [mcp-vertx](../mcp-vertx) [Model Context Protocol](https://modelcontextprotocol.io/) server embedded at runtime.

- **Deterministic processor** handles known failures via pluggable handlers.
- **Agent boundary** only triggers when the failure is unknown/ambiguous.
- The agent's decisions come from a **real LLM** via function-calling. There is
  no offline or rule-based fallback — see [A note on stubs](#a-note-on-stubs).
- **Tools are self-describing** with JSON Schema metadata aligned with MCP.
- **mcp-vertx integration** exposes the same tools through stateless Streamable HTTP with schema validation, bounded concurrency, deadlines, and secure loopback defaults.

## Modules

| Module | ArtifactId | Purpose |
|---|---|---|
| `../mcp-vertx` | `mcp-vertx` | Sibling standalone MCP server, included in the Maven reactor and embedded by `agent-app`. |
| `agent-core` | `agent-core` | Reusable agent infrastructure — LLM interfaces, memory, agent runner, deterministic processor, config records, HTTP API verticle. |
| `agent-app` | `agent-app` | Trade-failure domain logic — handlers, tools, factories, YAML-driven bootstrap (`MainVerticle`). Produces the fat JAR. |

```
deterministic-agents-workflow/
├── agent-core/         # dev.mars.agent  (core abstractions)
├── agent-app/          # dev.mars.agent  (domain + bootstrap)
└── pom.xml             # aggregates ../mcp-vertx, agent-core, and agent-app

mcp-vertx/              # sibling dev.mars:mcp-vertx project
```

## Requirements
- Java 25
- Maven 3.9+
- The `mcp-vertx` repository checked out beside this repository, at
  `../mcp-vertx` (the root Maven reactor includes it directly)

## Build + Run

### Windows PowerShell (recommended for this project)

Create a `.env` file in the project root:

```dotenv
OPENAI_API_KEY=sk-...
```

Then use the supplied launcher:

```powershell
.\scripts\run.ps1
```

The launcher:

- loads variables from the root `.env` file into the current application process;
- selects the local Java 25 installation when it is available;
- frees ports 8080, 8081, 8082, and 3001;
- builds the modules with tests skipped; and
- starts `dev.mars.agent.Main` from the `agent-app` module.

To restart without rebuilding, or to stop all four services:

```powershell
.\scripts\run.ps1 -SkipBuild
.\scripts\shutdown.ps1
```

The `.env` file is ignored by Git and must not be committed.

### Manual / Unix-like shell

```bash
mvn -q clean test
mvn -q package -DskipTests
export OPENAI_API_KEY=sk-...
java -jar agent-app/target/agent-app-0.1.0-SNAPSHOT-fat.jar
```

Running the application requires an API key; there is no offline or stub LLM
mode. The default `pipeline.yaml` resolves its `llm.params.apiKey` value from
the `OPENAI_API_KEY` environment variable.

`mvn test` does **not** need a key when `OPENAI_API_KEY` is absent. The suite is
split into two tiers: hermetic tests always run, while the live model test skips
itself when the variable is absent. If `OPENAI_API_KEY` is already present—in
the shell environment or loaded by another launcher—the live test runs and can
make a billable network request. See [Test Coverage](#test-coverage).

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

### 9) MCP — mcp-vertx Streamable HTTP (2026-07-28)

```bash
# Discover server capabilities. Every request is stateless and carries
# protocol and client metadata.
curl -s -X POST http://localhost:3001/mcp \
  -H 'content-type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'MCP-Protocol-Version: 2026-07-28' \
  -H 'Mcp-Method: server/discover' \
  -d '{"jsonrpc":"2.0","id":"discover-1","method":"server/discover","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientInfo":{"name":"curl","version":"1.0"},"io.modelcontextprotocol/clientCapabilities":{}}}}'

# List tools
curl -s -X POST http://localhost:3001/mcp \
  -H 'content-type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'MCP-Protocol-Version: 2026-07-28' \
  -H 'Mcp-Method: tools/list' \
  -d '{"jsonrpc":"2.0","id":"tools-1","method":"tools/list","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientInfo":{"name":"curl","version":"1.0"},"io.modelcontextprotocol/clientCapabilities":{}}}}'

# Invoke a tool
curl -s -X POST http://localhost:3001/mcp \
  -H 'content-type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'MCP-Protocol-Version: 2026-07-28' \
  -H 'Mcp-Method: tools/call' \
  -H 'Mcp-Name: case.raiseTicket' \
  -d '{"jsonrpc":"2.0","id":"call-1","method":"tools/call","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientInfo":{"name":"curl","version":"1.0"},"io.modelcontextprotocol/clientCapabilities":{}},"name":"case.raiseTicket","arguments":{"tradeId":"T-300","category":"ReferenceData","summary":"MCP test"}}}'
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

The sibling `mcp-vertx` project provides `McpServerVerticle`. This application
depends on that artifact and embeds the verticle with the same allow-listed
tool instances used by the agent runner. There is no MCP server implementation
in this repository.

### Transports

| Transport | Protocol version | Endpoints |
|---|---|---|
| **Stateless Streamable HTTP** | extended `2026-07-28`; standard initialization compatible with `2025-11-25` | `POST /mcp` |

### Stateless Streamable HTTP

Every request carries its protocol version, client identity, capabilities, MCP
method, and JSON-RPC payload. `GET /mcp` and `DELETE /mcp` return `405 Method
Not Allowed`; legacy session and SSE endpoints are intentionally absent.

The transport validates JSON Schema inputs and outputs, bounds request and
response sizes, limits global and per-tool concurrency, and supplies tool
deadlines and cooperative cancellation through `ToolContext`.

### Supported JSON-RPC Methods

| Method | Description |
|---|---|
| `initialize` | Capability negotiation — returns protocol version, server info, and capabilities |
| `notifications/initialized` | Standard initialization completion notification |
| `server/discover` | Extended stateless capability discovery |
| `tools/list` | Returns all registered tools with name, description, and `inputSchema` |
| `tools/call` | Invokes a tool by name with the supplied arguments |

### Configuration

```yaml
mcp:
  enabled: true    # set to false to disable the MCP server
  port: 3001       # TCP port (0 for random in tests)
  basePath: ""     # URL prefix for MCP endpoints
```

## How This Demonstrates MCP

This project integrates the standalone `mcp-vertx` implementation and adapts
its tool SPI to the agent loop. The core abstractions map onto MCP concepts:

### MCP Concept Mapping

| MCP Concept | This Project | Where |
|---|---|---|
| **Tool** — a callable capability with a name, description, and input schema | `AgentTool` extends the `mcp-vertx` `Tool` SPI and adds agent instructions/context | `agent-core` — `tool/AgentTool.java` |
| **`tools/list`** — server advertises available tools with their schemas | `mcp-vertx` `ToolRegistry` validates and holds the shared tool instances | sibling `mcp-vertx` project |
| **`tools/call`** — client invokes a tool by name with JSON arguments | `mcp-vertx` validates the request and dispatches through `ToolContext` | sibling `mcp-vertx` project |
| **`inputSchema`** — JSON Schema describing a tool's parameters | `AgentTool.schema()` is compiled and enforced by `mcp-vertx` | both projects |
| **Tool allow-listing** — security boundary controlling which tools an agent can use | One registry supplies both the MCP server and agent runner | `MainVerticle` |
| **LLM function-calling** — LLM decides which tool to call and with what arguments | `LlmClient.decideNext()` returns `{intent, tool, args}` commands | `agent-core` — `llm/LlmClient.java` |
| **Agent loop** — iterative tool calls until the task is complete | `AgentRunnerVerticle.runLoop()` loops until `stop: true` or step limit | `agent-core` — `runner/AgentRunnerVerticle.java` |

### What Each Tool Exposes (MCP-Ready)

Every `AgentTool` implementation provides three pieces of metadata that an MCP
server advertises in a `tools/list` response:

```java
public interface AgentTool extends Tool {
    String name();             // MCP tool name
    String description();      // MCP tool description
    JsonObject schema();       // MCP inputSchema (JSON Schema)
    Future<JsonObject> invoke(JsonObject args, AgentContext ctx);  // agent path
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

2. ~~**Expose tools as an MCP server**~~ — ✅ Done. The sibling `mcp-vertx`
   implementation serves `server/discover`, `tools/list`, and `tools/call`
   over stateless Streamable HTTP.

3. ~~**Replace `StubLlmClient`**~~ — ✅ Done. `OpenAiLlmClient`
   (`llm/OpenAiLlmClient.java`) is fully implemented: it sends each tool's
   `schema()` as an OpenAI function definition and maps the model's
   `tool_calls` response back onto the command protocol. Select it with
   `llm.type: openai` in `pipeline.yaml`.

---

## What to Look At

| Component | Module | Purpose |
|---|---|---|
| `McpServerVerticle` | sibling `mcp-vertx` | Current MCP transport, validation, security, deadlines, and concurrency controls |
| `AgentTool` adapter | `agent-core` | Bridges agent context/instructions to the `mcp-vertx` `Tool` SPI |
| `ToolRegistry` | sibling `mcp-vertx` | Tool registration, schema compilation, and allow-listing |
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

The root reactor runs the standalone `mcp-vertx` suite plus the workflow's
`agent-core` and `agent-app` suites, split into two tiers.

**Hermetic tier — runs on every build.** No API key, no network, no cost.
Covers everything except the model's own decisions.

**Live tier — gated on `OPENAI_API_KEY`.** Annotated
`@EnabledIfEnvironmentVariable`, so it skips silently when the variable is
absent and runs automatically when it is present. Assertions here are
deliberately structural — that the agent path routed, terminated, and produced
a result — never that a specific tool was chosen or specific wording returned,
because a real model does not repeat itself.

Coverage by module:

**mcp-vertx**
- Protocol, schema, security, OAuth, concurrency, cancellation, deadline,
  packaging, and optional A2A behavior from the standalone sibling project

**agent-core**
- Config record validation and defensive copying (`HttpConfig`, `McpConfig`, `LlmConfig`, `HandlerConfig`, `SchemaConfig`)
- `OpenAiLlmClient` request/response mapping through a local protocol fixture,
  construction, and unreachable-endpoint handling
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

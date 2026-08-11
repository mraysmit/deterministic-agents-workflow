# deterministic-agents-workflow — Vert.x 5 Agent + MCP Server (Java)

A **multi-module Vert.x 5 project** demonstrating the hybrid deterministic-workflow / LLM-agent pattern with a built-in [Model Context Protocol](https://modelcontextprotocol.io/) (MCP) server.

- **Deterministic processor** handles known failures via pluggable handlers.
- **Agent boundary** only triggers when the failure is unknown/ambiguous.
- The "LLM" is a **stub** that emits strict JSON commands (replace later).
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

- Agent HTTP API: **http://localhost:8080** (override with `http.port` in `pipeline.yaml`)
- MCP server: **http://localhost:3001** (override with `mcp.port` in `pipeline.yaml`)

## Health Check
```bash
curl http://localhost:8080/health
```

## Try it

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

### 3) Sanctions screening — adaptive false-positive analysis (5 steps)
```bash
curl -s -X POST http://localhost:8080/trade/failures \
  -H 'content-type: application/json' \
  -d '{"tradeId":"T-800","reason":"OFAC screening flag on counterparty"}' | jq
```
The agent gathers screening data, performs multi-factor false-positive analysis
(jurisdiction, sector, entity structure, client history), classifies with
regulatory citation (31 CFR § 501.604), and **chooses email over PagerDuty**
because the evidence suggests a probable false positive — calibrated urgency
that only an LLM can provide.

### 4) Multi-leg cascade — structural reasoning (5 steps)
```bash
curl -s -X POST http://localhost:8080/trade/failures \
  -H 'content-type: application/json' \
  -d '{"tradeId":"T-900","reason":"Linked trade cascade failure on swap leg"}' | jq
```
The agent discovers that a trivial SSI data error (BIC format) is blocking a
$50M swap structure with 4 linked trades and $42,500/bp unhedged DV01 exposure.
It classifies as **CRITICAL** (based on impact, not root cause complexity),
publishes a cascade event to prevent duplicate downstream alerts, and sends
**role-tailored notifications** to three different teams (Ops, Trading, Risk).

### 5) Counterparty credit event — cross-domain portfolio analysis (5 steps)
```bash
curl -s -X POST http://localhost:8080/trade/failures \
  -H 'content-type: application/json' \
  -d '{"tradeId":"T-1000","reason":"Counterparty credit downgrade to CCC+"}' | jq
```
The agent analyses a 5-notch downgrade across 4 positions ($19.8M gross, $5.1M
net after ISDA netting + CSA collateral). It identifies that one position (IRS,
-$1.2M MtM) **must NOT be unwound** because it provides netting benefit — the
kind of counter-intuitive insight that requires understanding both financial
math and legal agreements simultaneously.

### 6) Settlement amount mismatch — FX root-cause hypothesis (4 steps)
```bash
curl -s -X POST http://localhost:8080/trade/failures \
  -H 'content-type: application/json' \
  -d '{"tradeId":"T-500","reason":"Settlement amount mismatch detected"}' | jq
```

### 7) Duplicate trade detection — risk exposure analysis (4 steps)
```bash
curl -s -X POST http://localhost:8080/trade/failures \
  -H 'content-type: application/json' \
  -d '{"tradeId":"T-600","reason":"Possible duplicate trade execution"}' | jq
```

### 8) Regulatory deadline — compliance urgency (4 steps)
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

## LLM Showcase Scenarios

The agent includes **7 multi-step reasoning chains** (plus a fallback) that
demonstrate capabilities requiring a real LLM — not just keyword matching.
Each scenario is implemented as a stub rule that simulates what an LLM would
produce; replace `StubLlmClient` with `OpenAiLlmClient` to get the same
behaviour dynamically for **any** failure reason.

### What makes these impossible without an LLM

| Capability | Scenario | What the LLM does |
|---|---|---|
| **Multi-factor evidence weighing** | Sanctions screening (T-800) | Weighs 6 dimensions (jurisdiction, sector, entity type, ownership, history, name similarity) to assess false-positive probability at >90% confidence |
| **Calibrated urgency** | Sanctions screening (T-800) | Chooses **email** over PagerDuty because screening hold means no settlement risk — avoids alert fatigue while ensuring regulatory review |
| **Structural comprehension** | Multi-leg cascade (T-900) | Understands that a swap has pay/receive legs, hedges are linked, and a single SSI error cascades across 4 trades with $42,500/bp unhedged DV01 |
| **Root-cause vs. impact distinction** | Multi-leg cascade (T-900) | Classifies as **CRITICAL** despite trivial root cause (BIC format) because the financial impact ($2.75M MtM) is massive — "5 minutes to fix, $850K at risk" |
| **Role-tailored communication** | Multi-leg cascade (T-900) | One notification with different actionable content for 3 teams: fix instructions for Ops, hedge degradation warning for Trading, VaR metrics for Risk |
| **Cross-domain reasoning** | Credit event (T-1000) | Simultaneously applies financial math (netting), ISDA legal analysis (Section 5(b)(v)), and portfolio risk assessment to produce a single coherent analysis |
| **Counter-intuitive recommendations** | Credit event (T-1000) | Identifies that the IRS position (we **owe** $1.2M) must **NOT** be unwound because it provides netting benefit — the opposite of the naive instinct |
| **Exposure waterfall calculation** | Credit event (T-1000) | Calculates Gross $19.8M → Net $8.6M (ISDA netting) → Net after collateral $5.1M (CSA), then prioritises actions by risk, not position size |
| **Regulatory knowledge** | Sanctions (T-800), Regulatory (T-700) | Cites specific regulations (31 CFR § 501.604, SEC Rule 15c6-1) and understands their operational implications |
| **FX root-cause hypothesis** | Settlement mismatch (T-500) | Correlates the $250K discrepancy (20%) with a EUR/USD rate movement (+0.30%) to hypothesise a T+1 FX rate timing issue |
| **Pattern recognition** | Duplicate trade (T-600) | Recognises 3-second time delta + identical parameters as "retry-after-timeout" pattern and quantifies $1.25M unintended exposure |

### Scenario summary

| Scenario | Trade IDs | Steps | Key LLM capability |
|---|---|---|---|
| LEI missing | T-200 | 3 | Basic multi-step: lookup → classify → raise ticket |
| Settlement mismatch | T-500 | 4 | FX correlation + root-cause hypothesis |
| Duplicate trade | T-600 | 4 | Pattern recognition + risk quantification |
| Regulatory deadline | T-700 | 4 | Time-critical compliance with SEC rule awareness |
| **Sanctions screening** | **T-800** | **5** | **Adaptive false-positive analysis + calibrated urgency** |
| **Multi-leg cascade** | **T-900** | **5** | **Structural reasoning + cross-domain impact analysis** |
| **Credit event** | **T-1000** | **5** | **Portfolio netting + counter-intuitive recommendation** |
| Fallback (any reason) | any | 3 | Graceful handling of unknown patterns |

### The `reasoning` field

Every step includes a `reasoning` field that explains the LLM's thought
process. When using a real LLM, this becomes the model's actual chain-of-thought.
The stub version demonstrates the **quality and depth** of reasoning expected:

```json
{
  "intent": "CALL_TOOL",
  "tool": "comms.notify",
  "args": { "channel": "email", "team": "Compliance + Legal", ... },
  "reasoning": "I'm choosing EMAIL over PagerDuty because this is assessed as a probable false positive with the trade already safely on hold. PagerDuty would be appropriate for a high-confidence true positive where immediate blocking action is needed...",
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

The LLM (or stub) communicates with the agent runner using a simple JSON
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
— is exactly the MCP client pattern. Swapping `StubLlmClient` for a real LLM
with MCP-compatible function-calling requires no changes to the tool layer.

### Path to Further MCP Adoption

1. **Add an `McpToolAdapter`** — a `Tool` implementation that wraps an MCP
   server connection, translating `invoke()` calls into MCP `tools/call`
   JSON-RPC requests. This lets external MCP tools sit alongside in-process
   tools transparently.

2. ~~**Expose tools as an MCP server**~~ — ✅ Done. `McpServerVerticle`
   serves `tools/list` and `tools/call` over Streamable HTTP and legacy SSE.

3. **Replace `StubLlmClient`** — use `OpenAiLlmClient` (placeholder in
   `llm/OpenAiLlmClient.java`) with function-calling, passing tool schemas
   as the function definitions.

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
| `StubLlmClient` | `agent-core` | Rule-based stub returning strict JSON commands |
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

24 test classes / 100 test cases across all three modules covering:

**mcp-server**
- `ToolRegistry` immutability, lookup, and registration
- `McpServerVerticle` — Streamable HTTP transport, legacy SSE transport, JSON-RPC dispatch, session management, batch requests, tool invocation, and error handling

**agent-core**
- Config record validation and defensive copying (`HttpConfig`, `McpConfig`, `LlmConfig`, `HandlerConfig`, `SchemaConfig`)
- `StubLlmClient` rule matching, fallback, and defensive copying
- `OpenAiLlmClient` placeholder
- `InMemoryMemoryStore` lifecycle and case isolation
- `DeterministicFailureProcessorVerticle` routing and handler dispatch
- `AgentRunnerVerticle` step limits, tool dispatch, and error paths
- `HttpApiVerticle` routing and request validation
- `EventSinkVerticle` event bus publishing

**agent-app**
- Factory resolution (handlers, tools, LLM clients) with error cases
- YAML config parsing and missing-resource handling
- Handler behaviour (`LookupEnrichHandler`, `EscalateHandler`)
- Tool invocation and schema metadata (`RaiseTicketTool`, `PublishEventTool`)
- `TradeFailureRuleLoader` — all 7 multi-step rules + fallback, including sanctions (5-step), cascade (5-step), and credit event (5-step) chains
- `LookupTool` — scenario-specific data for all trade ID ranges (sanctions screening, multi-leg structures, credit/netting data)
- End-to-end smoke tests (deterministic + agent paths)

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

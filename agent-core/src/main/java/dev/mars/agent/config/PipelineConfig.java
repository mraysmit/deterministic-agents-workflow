package dev.mars.agent.config;

import java.util.List;

/**
 * Top-level pipeline configuration loaded from YAML.
 *
 * <p>This record is the root of the configuration data model. It captures
 * every externalisable aspect of the pipeline — event bus addresses, HTTP
 * settings, schema validation, agent behaviour, handler wiring, tool
 * selection, and LLM client choice.
 *
 * <h2>Example YAML</h2>
 * <pre>
 * addresses:
 *   inbound: "trade.failures"
 *   agent:   "agent.required"
 *   events:  "events.out"
 *
 * http:
 *   port: 8080
 *   route: "/trade/failures"
 *   requestTimeoutMs: 10000
 *
 * schema:
 *   caseIdField: "tradeId"
 *   allowedFields: [tradeId, reason, altIds]
 *   requiredFields: [tradeId, reason]
 *
 * agent:
 *   maxSteps: 5
 *   timeoutMs: 10000
 *
 * handlers:
 *   - reason: "Missing ISIN"
 *     type: "lookup-enrich"
 *     params: { identifier: "ISIN" }
 *   - reason: "Invalid Counterparty"
 *     type: "escalate"
 *
 * tools:
 *   - type: "publish-event"
 *   - type: "raise-ticket"
 *
 * llm:
 *   type: "stub"
 * </pre>
 *
 * @param addresses event bus address configuration
 * @param http      HTTP ingress configuration
 * @param schema    field validation and case-ID configuration
 * @param agent     agent runner settings
 * @param handlers  deterministic failure handlers
 * @param tools     agent tools
 * @param llm       LLM client configuration
 * @param mcp       MCP server configuration (nullable; {@code null} means
 *                  disabled)
 *
 * @see PipelineConfigLoader
 */
public record PipelineConfig(
    AddressesConfig addresses,
    HttpConfig http,
    SchemaConfig schema,
    AgentConfig agent,
    List<HandlerConfig> handlers,
    List<ToolConfig> tools,
    LlmConfig llm,
    McpConfig mcp
) {}

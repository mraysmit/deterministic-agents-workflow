package dev.mars.agent.tool;

import dev.mars.agent.runner.AgentContext;
import dev.mars.mcp.tool.Tool;
import dev.mars.mcp.tool.ToolContext;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A tool shared by the agent loop and the standalone {@code mcp-vertx}
 * transport.
 *
 * <p>The agent-facing overload receives accumulated reasoning state. MCP
 * calls are adapted from the transport's bounded {@link ToolContext}; its
 * resource identifier becomes the agent case identifier.
 */
public interface AgentTool extends Tool {

  /** Domain-specific guidance added to the LLM system prompt. */
  default String instructions() {
    return "";
  }

  /** Execute this tool from the agent reasoning loop. */
  Future<JsonObject> invoke(JsonObject arguments, AgentContext context);

  /** Adapt an MCP invocation to the agent tool contract. */
  @Override
  default Future<JsonObject> invoke(JsonObject arguments, ToolContext context) {
    AgentContext agentContext = new AgentContext(
        context.correlationId(), context.resourceId(), new JsonObject());
    return invoke(arguments, agentContext);
  }
}

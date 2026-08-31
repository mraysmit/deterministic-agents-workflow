package dev.mars.agent.tool;

import dev.mars.agent.runner.AgentContext;
import dev.mars.mcp.tool.Tool;
import dev.mars.mcp.tool.ToolContext;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolTest {

  @Test
  void adapts_mcp_context_to_agent_context() {
    AtomicReference<AgentContext> received = new AtomicReference<>();
    AgentTool agentTool = new AgentTool() {
      @Override
      public String name() {
        return "test.context";
      }

      @Override
      public Future<JsonObject> invoke(JsonObject arguments, AgentContext context) {
        received.set(context);
        return Future.succeededFuture(new JsonObject().put("status", "ok"));
      }
    };

    Tool mcpTool = agentTool;
    JsonObject result = mcpTool.invoke(new JsonObject(),
            new ToolContext("corr-1", "T-42", new JsonObject()))
        .toCompletionStage().toCompletableFuture().join();

    assertEquals("ok", result.getString("status"));
    assertEquals("corr-1", received.get().correlationId());
    assertEquals("T-42", received.get().caseId());
    assertTrue(received.get().state().isEmpty());
  }
}

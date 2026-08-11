package dev.mars.agent.runner;

import dev.mars.agent.llm.LlmClient;
import dev.mars.agent.memory.InMemoryMemoryStore;
import dev.mars.mcp.tool.AgentContext;
import dev.mars.mcp.tool.Tool;
import dev.mars.mcp.tool.ToolRegistry;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class AgentRunnerVerticleTest {

  private Tool stubTool(String name) {
    return new Tool() {
      @Override
      public String name() { return name; }
      @Override
      public Future<JsonObject> invoke(JsonObject args, AgentContext ctx) {
        return Future.succeededFuture(new JsonObject()
            .put("status", "done")
            .put("toolName", name));
      }
    };
  }

  @Test
  void successful_single_step_invocation(Vertx vertx, VertxTestContext ctx) {
    LlmClient llm = (event, state) -> Future.succeededFuture(new JsonObject()
        .put("intent", "CALL_TOOL")
        .put("tool", "test.tool")
        .put("args", new JsonObject())
        .put("stop", true));

    Map<String, Tool> tools = ToolRegistry.of(stubTool("test.tool"));
    var verticle = new AgentRunnerVerticle(
        "test.agent.run", llm, tools, new InMemoryMemoryStore(), "tradeId");

    vertx.deployVerticle(verticle).compose(id ->
      vertx.eventBus().request("test.agent.run",
          new JsonObject().put("tradeId", "T-1").put("reason", "test"))
    ).onSuccess(reply -> {
      JsonObject body = (JsonObject) reply.body();
      assertEquals("ok", body.getString("status"));
      assertEquals("agent", body.getString("path"));
      assertEquals("T-1", body.getString("tradeId"));
      assertNotNull(body.getJsonObject("result"));
      ctx.completeNow();
    }).onFailure(ctx::failNow);
  }

  @Test
  void step_limit_is_enforced(Vertx vertx, VertxTestContext ctx) {
    // LLM always says "don't stop" → will hit step limit
    LlmClient llm = (event, state) -> Future.succeededFuture(new JsonObject()
        .put("intent", "CALL_TOOL")
        .put("tool", "test.tool")
        .put("args", new JsonObject())
        .put("stop", false));

    Map<String, Tool> tools = ToolRegistry.of(stubTool("test.tool"));
    var verticle = new AgentRunnerVerticle(
        "test.agent.limit", llm, tools, new InMemoryMemoryStore(), "tradeId");

    var opts = new DeploymentOptions().setConfig(
        new JsonObject().put("agent.max.steps", 2));

    vertx.deployVerticle(verticle, opts).compose(id ->
      vertx.eventBus().request("test.agent.limit",
          new JsonObject().put("tradeId", "T-2").put("reason", "test"))
    ).onSuccess(reply -> {
      JsonObject body = (JsonObject) reply.body();
      assertEquals("error", body.getString("status"));
      assertTrue(body.getString("reason").contains("Step limit"));
      ctx.completeNow();
    }).onFailure(ctx::failNow);
  }

  @Test
  void unsupported_intent_fails(Vertx vertx, VertxTestContext ctx) {
    LlmClient llm = (event, state) -> Future.succeededFuture(new JsonObject()
        .put("intent", "UNKNOWN_INTENT")
        .put("tool", "test.tool")
        .put("args", new JsonObject()));

    Map<String, Tool> tools = ToolRegistry.of(stubTool("test.tool"));
    var verticle = new AgentRunnerVerticle(
        "test.agent.intent", llm, tools, new InMemoryMemoryStore(), "tradeId");

    vertx.deployVerticle(verticle).compose(id ->
      vertx.eventBus().request("test.agent.intent",
          new JsonObject().put("tradeId", "T-3").put("reason", "test"))
    ).onSuccess(reply -> ctx.failNow("Expected failure"))
    .onFailure(err -> {
      assertTrue(err.getMessage().contains("Unsupported intent"));
      ctx.completeNow();
    });
  }

  @Test
  void unknown_tool_fails(Vertx vertx, VertxTestContext ctx) {
    LlmClient llm = (event, state) -> Future.succeededFuture(new JsonObject()
        .put("intent", "CALL_TOOL")
        .put("tool", "nonexistent.tool")
        .put("args", new JsonObject()));

    Map<String, Tool> tools = ToolRegistry.of(stubTool("test.tool"));
    var verticle = new AgentRunnerVerticle(
        "test.agent.unknown", llm, tools, new InMemoryMemoryStore(), "tradeId");

    vertx.deployVerticle(verticle).compose(id ->
      vertx.eventBus().request("test.agent.unknown",
          new JsonObject().put("tradeId", "T-4").put("reason", "test"))
    ).onSuccess(reply -> ctx.failNow("Expected failure"))
    .onFailure(err -> {
      assertTrue(err.getMessage().contains("not allowlisted"));
      ctx.completeNow();
    });
  }

  @Test
  void multi_step_loop_until_stop(Vertx vertx, VertxTestContext ctx) {
    // LLM returns stop=false on step 0, stop=true on step 1+
    var stepCounter = new int[]{0};
    LlmClient llm = (event, state) -> {
      boolean stop = stepCounter[0]++ > 0;
      return Future.succeededFuture(new JsonObject()
          .put("intent", "CALL_TOOL")
          .put("tool", "test.tool")
          .put("args", new JsonObject())
          .put("stop", stop));
    };

    Map<String, Tool> tools = ToolRegistry.of(stubTool("test.tool"));
    var verticle = new AgentRunnerVerticle(
        "test.agent.multi", llm, tools, new InMemoryMemoryStore(), "tradeId");

    vertx.deployVerticle(verticle).compose(id ->
      vertx.eventBus().request("test.agent.multi",
          new JsonObject().put("tradeId", "T-5").put("reason", "test"))
    ).onSuccess(reply -> {
      JsonObject body = (JsonObject) reply.body();
      assertEquals("ok", body.getString("status"));
      assertEquals(2, stepCounter[0]); // 2 LLM calls made
      ctx.completeNow();
    }).onFailure(ctx::failNow);
  }

  @Test
  void custom_case_id_field_is_used(Vertx vertx, VertxTestContext ctx) {
    LlmClient llm = (event, state) -> Future.succeededFuture(new JsonObject()
        .put("intent", "CALL_TOOL")
        .put("tool", "test.tool")
        .put("args", new JsonObject())
        .put("stop", true));

    Map<String, Tool> tools = ToolRegistry.of(stubTool("test.tool"));
    var verticle = new AgentRunnerVerticle(
        "test.agent.custom", llm, tools, new InMemoryMemoryStore(), "orderId");

    vertx.deployVerticle(verticle).compose(id ->
      vertx.eventBus().request("test.agent.custom",
          new JsonObject().put("orderId", "O-1").put("reason", "test"))
    ).onSuccess(reply -> {
      JsonObject body = (JsonObject) reply.body();
      assertEquals("O-1", body.getString("orderId"));
      ctx.completeNow();
    }).onFailure(ctx::failNow);
  }
}

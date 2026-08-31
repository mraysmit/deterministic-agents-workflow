package dev.mars.agent.runner;

import dev.mars.agent.llm.LlmClient;
import dev.mars.agent.memory.InMemoryMemoryStore;
import dev.mars.agent.tool.AgentTool;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class AgentRunnerVerticleTest {

  private AgentTool stubTool(String name) {
    return new AgentTool() {
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

  private Map<String, AgentTool> registry(AgentTool... tools) {
    return Stream.of(tools).collect(
        Collectors.toUnmodifiableMap(AgentTool::name, tool -> tool));
  }

  @Test
  void successful_single_step_invocation(Vertx vertx, VertxTestContext ctx) {
    LlmClient llm = (event, state) -> Future.succeededFuture(new JsonObject()
        .put("intent", "CALL_TOOL")
        .put("tool", "test.tool")
        .put("args", new JsonObject())
        .put("stop", true));

    Map<String, AgentTool> tools = registry(stubTool("test.tool"));
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

    Map<String, AgentTool> tools = registry(stubTool("test.tool"));
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

    Map<String, AgentTool> tools = registry(stubTool("test.tool"));
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

    Map<String, AgentTool> tools = registry(stubTool("test.tool"));
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

    Map<String, AgentTool> tools = registry(stubTool("test.tool"));
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

    Map<String, AgentTool> tools = registry(stubTool("test.tool"));
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

  @Test
  void deadline_prevents_late_tool_side_effects(Vertx vertx, VertxTestContext ctx) {
    AtomicInteger invocations = new AtomicInteger();
    AgentTool tool = new AgentTool() {
      @Override
      public String name() { return "test.tool"; }

      @Override
      public Future<JsonObject> invoke(JsonObject args, AgentContext context) {
        invocations.incrementAndGet();
        return Future.succeededFuture(new JsonObject().put("status", "done"));
      }
    };

    LlmClient delayedLlm = (event, state) -> {
      Promise<JsonObject> decision = Promise.promise();
      vertx.setTimer(50, ignored -> decision.complete(new JsonObject()
          .put("intent", "CALL_TOOL")
          .put("tool", "test.tool")
          .put("args", new JsonObject())
          .put("stop", true)));
      return decision.future();
    };

    var verticle = new AgentRunnerVerticle(
        "test.agent.deadline", delayedLlm, registry(tool),
        new InMemoryMemoryStore(), "tradeId");
    var deployOptions = new DeploymentOptions().setConfig(new JsonObject()
        .put("agent.timeout.ms", 10));
    var requestOptions = new DeliveryOptions().setSendTimeout(500);

    vertx.deployVerticle(verticle, deployOptions).compose(id ->
      vertx.eventBus().request("test.agent.deadline",
          new JsonObject().put("tradeId", "T-timeout").put("reason", "test"),
          requestOptions)
    ).onSuccess(reply -> ctx.failNow("Expected deadline failure"))
    .onFailure(err -> {
      assertTrue(err.getMessage().contains("deadline exceeded"));
      assertEquals(0, invocations.get(), "tool must not run after the deadline");
      ctx.completeNow();
    });
  }
}

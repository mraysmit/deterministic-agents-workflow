package dev.mars.agent.llm;

import dev.mars.agent.runner.AgentContext;
import dev.mars.agent.tool.AgentTool;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class OpenAiLlmClientTest {

  @Test
  void constructor_accepts_tools(Vertx vertx) {
    // Should construct without error even with an empty tool list
    var client = new OpenAiLlmClient(vertx,
        "https://api.example.com/v1", "sk-test", "gpt-4o", List.of());
    assertNotNull(client);
  }

  @Test
  void decide_next_fails_on_unreachable_endpoint(Vertx vertx, VertxTestContext ctx) {
    var client = new OpenAiLlmClient(vertx,
        "http://localhost:19999", "sk-test", "gpt-4o", List.of());

    client.decideNext(
        new JsonObject().put("tradeId", "T-100").put("reason", "Missing ISIN"),
        new JsonObject().put("step", 0))
      .onSuccess(r -> ctx.failNow("Expected failure — endpoint is unreachable"))
      .onFailure(err -> {
        // Connection refused or similar network error
        assertNotNull(err.getMessage());
        ctx.completeNow();
      });
  }

  @Test
  void notification_tool_call_keeps_investigation_open(Vertx vertx,
                                                        VertxTestContext ctx) {
    AgentTool notification = new AgentTool() {
      @Override
      public String name() { return "comms.notify"; }

      @Override
      public Future<JsonObject> invoke(JsonObject args, AgentContext context) {
        return Future.succeededFuture(new JsonObject());
      }
    };

    JsonObject responseBody = new JsonObject().put("choices", new JsonArray()
        .add(new JsonObject().put("message", new JsonObject()
            .put("tool_calls", new JsonArray().add(new JsonObject()
                .put("function", new JsonObject()
                    .put("name", "comms_notify")
                    .put("arguments", "{\"tradeId\":\"T-1\"}")))))));

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    router.post("/chat/completions").handler(routingContext ->
        routingContext.response()
            .putHeader("content-type", "application/json")
            .end(responseBody.encode()));

    vertx.createHttpServer().requestHandler(router).listen(0)
      .compose(server -> {
        var client = new OpenAiLlmClient(vertx,
            "http://localhost:" + server.actualPort(),
            "sk-test", "gpt-4o", List.of(notification));
        return client.decideNext(
            new JsonObject().put("tradeId", "T-1").put("reason", "test"),
            new JsonObject().put("step", 0));
      })
      .onSuccess(command -> {
        assertEquals("comms.notify", command.getString("tool"));
        assertFalse(command.getBoolean("stop"));
        ctx.completeNow();
      })
      .onFailure(ctx::failNow);
  }
}

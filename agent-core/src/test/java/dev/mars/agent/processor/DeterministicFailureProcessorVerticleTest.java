package dev.mars.agent.processor;

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
class DeterministicFailureProcessorVerticleTest {

  @Test
  void deterministic_handler_is_used_for_matching_reason(Vertx vertx, VertxTestContext ctx) {
    FailureHandler handler = event -> Future.succeededFuture(
        new JsonObject().put("type", "Handled").put("tradeId", event.getString("tradeId")));

    var verticle = new DeterministicFailureProcessorVerticle(
        "test.inbound", "test.agent", Map.of("Known Reason", handler));

    vertx.deployVerticle(verticle).compose(id ->
      vertx.eventBus().request("test.inbound",
          new JsonObject().put("tradeId", "T-1").put("reason", "Known Reason"))
    ).onSuccess(reply -> {
      JsonObject body = (JsonObject) reply.body();
      assertEquals("ok", body.getString("status"));
      assertEquals("deterministic", body.getString("path"));
      ctx.completeNow();
    }).onFailure(ctx::failNow);
  }

  @Test
  void unknown_reason_routes_to_agent(Vertx vertx, VertxTestContext ctx) {
    // Set up an agent consumer that replies
    vertx.eventBus().consumer("test.agent.2", msg -> {
      msg.reply(new JsonObject()
          .put("status", "ok")
          .put("path", "agent")
          .put("result", new JsonObject().put("handled", true)));
    });

    var verticle = new DeterministicFailureProcessorVerticle(
        "test.inbound.2", "test.agent.2", Map.of());

    vertx.deployVerticle(verticle).compose(id ->
      vertx.eventBus().request("test.inbound.2",
          new JsonObject().put("tradeId", "T-2").put("reason", "Unknown"))
    ).onSuccess(reply -> {
      JsonObject body = (JsonObject) reply.body();
      assertEquals("ok", body.getString("status"));
      assertEquals("agent", body.getString("path"));
      ctx.completeNow();
    }).onFailure(ctx::failNow);
  }

  @Test
  void deterministic_handler_failure_returns_error(Vertx vertx, VertxTestContext ctx) {
    FailureHandler failingHandler = event ->
        Future.failedFuture("Simulated handler failure");

    var verticle = new DeterministicFailureProcessorVerticle(
        "test.inbound.3", "test.agent.3", Map.of("FailReason", failingHandler));

    vertx.deployVerticle(verticle).compose(id ->
      vertx.eventBus().request("test.inbound.3",
          new JsonObject().put("tradeId", "T-3").put("reason", "FailReason"))
    ).onSuccess(reply -> ctx.failNow("Expected failure"))
    .onFailure(err -> {
      assertTrue(err.getMessage().contains("Simulated handler failure"));
      ctx.completeNow();
    });
  }

  @Test
  void empty_reason_falls_through_to_agent(Vertx vertx, VertxTestContext ctx) {
    vertx.eventBus().consumer("test.agent.4", msg -> {
      msg.reply(new JsonObject().put("status", "ok").put("path", "agent"));
    });

    FailureHandler handler = event -> Future.succeededFuture(new JsonObject());
    var verticle = new DeterministicFailureProcessorVerticle(
        "test.inbound.4", "test.agent.4", Map.of("SomeReason", handler));

    vertx.deployVerticle(verticle).compose(id ->
      vertx.eventBus().request("test.inbound.4",
          new JsonObject().put("tradeId", "T-4"))
    ).onSuccess(reply -> {
      JsonObject body = (JsonObject) reply.body();
      assertEquals("agent", body.getString("path"));
      ctx.completeNow();
    }).onFailure(ctx::failNow);
  }
}

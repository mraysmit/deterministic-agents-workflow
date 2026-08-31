package dev.mars.agent.ui;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(VertxExtension.class)
class WorkflowUiVerticleTest {

  @Test
  void validates_and_sanitizes_before_event_bus_dispatch(Vertx vertx,
                                                          VertxTestContext ctx) {
    String inboundAddress = "test.workflow.inbound";
    AtomicInteger dispatches = new AtomicInteger();
    vertx.eventBus().<JsonObject>consumer(inboundAddress, message -> {
      dispatches.incrementAndGet();
      JsonObject body = message.body();
      ctx.verify(() -> assertFalse(body.containsKey("unexpected")));
      message.reply(new JsonObject().put("status", "ok").put("path", "test"));
    });

    var verticle = new WorkflowUiVerticle(
        inboundAddress, "test.workflow.events", 1_000, 0,
        "test", "test-model",
        Set.of("tradeId", "reason"), Set.of("tradeId", "reason"));
    var options = new DeploymentOptions().setConfig(
        new JsonObject().put("workflow.port", 0));
    WebClient client = WebClient.create(vertx);

    vertx.deployVerticle(verticle, options)
      .compose(id -> client.post(verticle.actualPort(), "localhost", "/workflow/api/run")
          .sendJsonObject(new JsonObject().put("unexpected", "value")))
      .compose(invalidResponse -> {
        ctx.verify(() -> {
          assertEquals(400, invalidResponse.statusCode());
          assertEquals(0, dispatches.get());
        });
        return client.post(verticle.actualPort(), "localhost", "/workflow/api/run")
            .sendJsonObject(new JsonObject()
                .put("tradeId", "T-1")
                .put("reason", "test")
                .put("unexpected", "discard"));
      })
      .onSuccess(validResponse -> {
        ctx.verify(() -> {
          assertEquals(200, validResponse.statusCode());
          assertEquals(1, dispatches.get());
        });
        ctx.completeNow();
      })
      .onFailure(ctx::failNow);
  }
}

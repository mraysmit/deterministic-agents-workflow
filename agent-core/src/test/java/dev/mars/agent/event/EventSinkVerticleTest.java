package dev.mars.agent.event;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class EventSinkVerticleTest {

  @Test
  void deploys_and_consumes_events(Vertx vertx, VertxTestContext ctx) {
    var verticle = new EventSinkVerticle("events.sink-test");
    vertx.deployVerticle(verticle).onSuccess(id -> {
      // Publish an event — the sink should consume it without error
      vertx.eventBus().publish("events.sink-test",
          new JsonObject().put("type", "TestEvent").put("data", "hello"));

      // Give the consumer a moment to process, then verify no crash
      vertx.setTimer(100, t -> ctx.completeNow());
    }).onFailure(ctx::failNow);
  }

  @Test
  void deploys_successfully(Vertx vertx, VertxTestContext ctx) {
    var verticle = new EventSinkVerticle("events.sink-deploy");
    vertx.deployVerticle(verticle)
        .onSuccess(id -> {
          assertNotNull(id);
          ctx.completeNow();
        })
        .onFailure(ctx::failNow);
  }
}

package dev.mars.agent.handler;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class EscalateHandlerTest {

  @Test
  void produces_trade_escalated_event(Vertx vertx, VertxTestContext ctx) {
    var handler = new EscalateHandler(vertx, "events.test");
    var event = new JsonObject()
        .put("tradeId", "T-1")
        .put("reason", "Invalid Counterparty");

    handler.handle(event).onSuccess(result -> {
      assertEquals("TradeEscalated", result.getString("type"));
      assertEquals("T-1", result.getString("tradeId"));
      assertEquals("deterministic-processor", result.getString("by"));
      assertEquals("Invalid Counterparty", result.getString("reason"));
      ctx.completeNow();
    }).onFailure(ctx::failNow);
  }

  @Test
  void publishes_event_to_event_bus(Vertx vertx, VertxTestContext ctx) {
    vertx.eventBus().consumer("events.escalate-test", msg -> {
      JsonObject body = (JsonObject) msg.body();
      assertEquals("TradeEscalated", body.getString("type"));
      assertEquals("T-2", body.getString("tradeId"));
      ctx.completeNow();
    });

    var handler = new EscalateHandler(vertx, "events.escalate-test");
    handler.handle(new JsonObject()
        .put("tradeId", "T-2")
        .put("reason", "Unknown reason"));
  }
}

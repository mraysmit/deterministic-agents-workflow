package dev.mars.agent.handler;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class LookupEnrichHandlerTest {

  @Test
  void produces_trade_repaired_event(Vertx vertx, VertxTestContext ctx) {
    var handler = new LookupEnrichHandler(vertx, "events.test", "ISIN");
    var event = new JsonObject().put("tradeId", "T-1").put("reason", "Missing ISIN");

    handler.handle(event).onSuccess(result -> {
      assertEquals("TradeRepaired", result.getString("type"));
      assertEquals("T-1", result.getString("tradeId"));
      assertEquals("deterministic-processor", result.getString("by"));
      assertTrue(result.getJsonObject("details").getString("action").contains("ISIN"));
      ctx.completeNow();
    }).onFailure(ctx::failNow);
  }

  @Test
  void publishes_event_to_event_bus(Vertx vertx, VertxTestContext ctx) {
    vertx.eventBus().consumer("events.handler-test", msg -> {
      JsonObject body = (JsonObject) msg.body();
      assertEquals("TradeRepaired", body.getString("type"));
      ctx.completeNow();
    });

    var handler = new LookupEnrichHandler(vertx, "events.handler-test", "ISIN");
    handler.handle(new JsonObject().put("tradeId", "T-1").put("reason", "Missing ISIN"));
  }

  @Test
  void uses_configured_identifier_name(Vertx vertx, VertxTestContext ctx) {
    var handler = new LookupEnrichHandler(vertx, "events.test", "CUSIP");
    var event = new JsonObject().put("tradeId", "T-2").put("reason", "Missing CUSIP");

    handler.handle(event).onSuccess(result -> {
      assertTrue(result.getJsonObject("details").getString("action").contains("CUSIP"));
      ctx.completeNow();
    }).onFailure(ctx::failNow);
  }
}

package dev.mars.agent.config;

import dev.mars.agent.handler.EscalateHandler;
import dev.mars.agent.handler.LookupEnrichHandler;
import dev.mars.agent.processor.FailureHandler;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class HandlerFactoryTest {

  @Test
  void creates_lookup_enrich_handler(Vertx vertx) {
    FailureHandler handler = HandlerFactory.create(
        "lookup-enrich", Map.of("identifier", "ISIN"), vertx, "events.out");
    assertInstanceOf(LookupEnrichHandler.class, handler);
  }

  @Test
  void lookup_enrich_requires_identifier_param(Vertx vertx) {
    assertThrows(IllegalArgumentException.class,
        () -> HandlerFactory.create("lookup-enrich", Map.of(), vertx, "events.out"));
  }

  @Test
  void lookup_enrich_rejects_blank_identifier(Vertx vertx) {
    assertThrows(IllegalArgumentException.class,
        () -> HandlerFactory.create("lookup-enrich", Map.of("identifier", "  "), vertx, "events.out"));
  }

  @Test
  void creates_escalate_handler(Vertx vertx) {
    FailureHandler handler = HandlerFactory.create(
        "escalate", Map.of(), vertx, "events.out");
    assertInstanceOf(EscalateHandler.class, handler);
  }

  @Test
  void unknown_type_throws(Vertx vertx) {
    var ex = assertThrows(IllegalArgumentException.class,
        () -> HandlerFactory.create("nonexistent", Map.of(), vertx, "events.out"));
    assertTrue(ex.getMessage().contains("nonexistent"));
  }
}

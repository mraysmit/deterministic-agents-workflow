package dev.mars.agent.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HandlerConfigTest {

  @Test
  void null_params_defaults_to_empty_map() {
    var cfg = new HandlerConfig("reason", "type", null);
    assertNotNull(cfg.params());
    assertTrue(cfg.params().isEmpty());
  }

  @Test
  void explicit_params_are_preserved() {
    var params = Map.of("key", "value");
    var cfg = new HandlerConfig("reason", "type", params);
    assertEquals("value", cfg.params().get("key"));
  }

  @Test
  void reason_and_type_are_stored() {
    var cfg = new HandlerConfig("Missing ISIN", "lookup-enrich", Map.of());
    assertEquals("Missing ISIN", cfg.reason());
    assertEquals("lookup-enrich", cfg.type());
  }
}

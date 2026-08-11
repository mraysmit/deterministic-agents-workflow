package dev.mars.agent.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LlmConfigTest {

  @Test
  void null_params_defaults_to_empty_map() {
    var cfg = new LlmConfig("stub", null);
    assertNotNull(cfg.params());
    assertTrue(cfg.params().isEmpty());
  }

  @Test
  void explicit_params_are_preserved() {
    var params = Map.of("endpoint", "https://api.openai.com");
    var cfg = new LlmConfig("openai", params);
    assertEquals("https://api.openai.com", cfg.params().get("endpoint"));
  }

  @Test
  void type_is_stored() {
    var cfg = new LlmConfig("stub", Map.of());
    assertEquals("stub", cfg.type());
  }
}

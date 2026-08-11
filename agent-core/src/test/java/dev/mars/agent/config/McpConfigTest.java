package dev.mars.agent.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McpConfigTest {

  @Test
  void valid_config_creates_record() {
    var cfg = new McpConfig(true, 3001, "");
    assertTrue(cfg.enabled());
    assertEquals(3001, cfg.port());
    assertEquals("", cfg.basePath());
  }

  @Test
  void disabled_by_default_is_valid() {
    var cfg = new McpConfig(false, 3001, "");
    assertFalse(cfg.enabled());
  }

  @Test
  void port_zero_is_valid() {
    var cfg = new McpConfig(true, 0, "/mcp");
    assertEquals(0, cfg.port());
  }

  @Test
  void negative_port_throws() {
    assertThrows(IllegalArgumentException.class,
        () -> new McpConfig(true, -1, ""));
  }

  @Test
  void null_basePath_defaults_to_empty() {
    var cfg = new McpConfig(true, 3001, null);
    assertEquals("", cfg.basePath());
  }

  @Test
  void custom_basePath_is_preserved() {
    var cfg = new McpConfig(true, 3001, "/mcp");
    assertEquals("/mcp", cfg.basePath());
  }
}

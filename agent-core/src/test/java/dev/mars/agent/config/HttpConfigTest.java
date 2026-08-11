package dev.mars.agent.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpConfigTest {

  @Test
  void valid_config_creates_record() {
    var cfg = new HttpConfig(8080, "/api/test", 5000);
    assertEquals(8080, cfg.port());
    assertEquals("/api/test", cfg.route());
    assertEquals(5000, cfg.requestTimeoutMs());
  }

  @Test
  void port_zero_is_valid() {
    var cfg = new HttpConfig(0, "/test", 1000);
    assertEquals(0, cfg.port());
  }

  @Test
  void negative_port_throws() {
    assertThrows(IllegalArgumentException.class,
        () -> new HttpConfig(-1, "/test", 1000));
  }

  @Test
  void null_route_throws() {
    assertThrows(IllegalArgumentException.class,
        () -> new HttpConfig(8080, null, 1000));
  }

  @Test
  void blank_route_throws() {
    assertThrows(IllegalArgumentException.class,
        () -> new HttpConfig(8080, "  ", 1000));
  }
}

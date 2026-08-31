package dev.mars.agent.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentConfigTest {

  @Test
  void accepts_positive_limits() {
    AgentConfig config = new AgentConfig(8, 60_000);
    assertEquals(8, config.maxSteps());
    assertEquals(60_000, config.timeoutMs());
  }

  @Test
  void rejects_non_positive_limits() {
    assertThrows(IllegalArgumentException.class, () -> new AgentConfig(0, 1));
    assertThrows(IllegalArgumentException.class, () -> new AgentConfig(1, 0));
  }
}

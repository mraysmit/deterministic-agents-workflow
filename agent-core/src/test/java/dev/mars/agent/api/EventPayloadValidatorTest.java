package dev.mars.agent.api;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventPayloadValidatorTest {

  private final EventPayloadValidator validator = new EventPayloadValidator(
      Set.of("tradeId", "reason"), Set.of("tradeId", "reason"));

  @Test
  void rejects_missing_blank_and_non_string_required_values() {
    assertThrows(IllegalArgumentException.class,
        () -> validator.validateAndSanitize(new JsonObject().put("tradeId", "T-1")));
    assertThrows(IllegalArgumentException.class,
        () -> validator.validateAndSanitize(new JsonObject()
            .put("tradeId", " ").put("reason", "failure")));
    assertThrows(IllegalArgumentException.class,
        () -> validator.validateAndSanitize(new JsonObject()
            .put("tradeId", 1).put("reason", "failure")));
  }

  @Test
  void returns_a_sanitized_copy() {
    JsonObject input = new JsonObject()
        .put("tradeId", "T-1")
        .put("reason", "failure")
        .put("unexpected", "discard me");

    JsonObject result = validator.validateAndSanitize(input);

    assertEquals("T-1", result.getString("tradeId"));
    assertFalse(result.containsKey("unexpected"));
    input.put("tradeId", "changed");
    assertEquals("T-1", result.getString("tradeId"));
  }
}

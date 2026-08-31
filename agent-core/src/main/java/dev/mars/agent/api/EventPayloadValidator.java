package dev.mars.agent.api;

import io.vertx.core.json.JsonObject;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates and sanitises event payloads at every HTTP ingress boundary.
 */
public final class EventPayloadValidator {

  private final Set<String> allowedFields;
  private final Set<String> requiredFields;

  public EventPayloadValidator(Set<String> allowedFields, Set<String> requiredFields) {
    this.allowedFields = Set.copyOf(allowedFields);
    this.requiredFields = Set.copyOf(requiredFields);

    Set<String> notAllowed = this.requiredFields.stream()
        .filter(field -> !this.allowedFields.contains(field))
        .collect(Collectors.toSet());
    if (!notAllowed.isEmpty()) {
      throw new IllegalArgumentException(
          "Required fields not in allowedFields: " + notAllowed);
    }
  }

  /**
   * Returns a new object containing only allow-listed fields.
   * Required values must be non-blank strings.
   */
  public JsonObject validateAndSanitize(JsonObject event) {
    if (event == null) {
      throw new IllegalArgumentException("Expected JSON body");
    }

    String invalid = requiredFields.stream()
        .filter(field -> !(event.getValue(field) instanceof String value)
            || value.isBlank())
        .sorted()
        .collect(Collectors.joining(", "));
    if (!invalid.isEmpty()) {
      throw new IllegalArgumentException(
          "Missing or invalid required field(s): " + invalid);
    }

    JsonObject sanitized = new JsonObject();
    for (String field : allowedFields) {
      if (event.containsKey(field)) {
        sanitized.put(field, event.getValue(field));
      }
    }
    return sanitized;
  }
}

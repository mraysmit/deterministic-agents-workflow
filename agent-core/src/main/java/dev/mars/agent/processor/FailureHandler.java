package dev.mars.agent.processor;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Strategy for handling a known failure reason deterministically.
 * Implementations are registered by reason string in the processor.
 */
@FunctionalInterface
public interface FailureHandler {

  /**
   * Handle the failure event and return a result describing the outcome.
   *
   * @param event the inbound failure event (contains tradeId, reason, etc.)
   * @return a Future with the result JSON (should include "type" and domain-specific fields)
   */
  Future<JsonObject> handle(JsonObject event);
}

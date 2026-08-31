package dev.mars.agent.runner;

import io.vertx.core.json.JsonObject;

/**
 * Immutable identity and mutable working state for one agent execution.
 *
 * @param correlationId identifier shared by logs and emitted events
 * @param caseId domain case or trade identifier
 * @param state accumulated agent state for the current execution
 */
public record AgentContext(String correlationId, String caseId, JsonObject state) {
  public AgentContext {
    if (correlationId == null || correlationId.isBlank()) {
      throw new IllegalArgumentException("correlationId must not be blank");
    }
    if (caseId == null || caseId.isBlank()) {
      throw new IllegalArgumentException("caseId must not be blank");
    }
    if (state == null) {
      throw new IllegalArgumentException("state must not be null");
    }
  }
}

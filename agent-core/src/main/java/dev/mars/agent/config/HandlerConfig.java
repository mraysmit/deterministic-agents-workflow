package dev.mars.agent.config;

import java.util.Map;

/**
 * Configuration for a single deterministic failure handler.
 *
 * @param reason the failure-reason string that triggers this handler
 * @param type   the handler alias (e.g. {@code "lookup-enrich"},
 *               {@code "escalate"}) resolved by the handler factory
 * @param params type-specific parameters (e.g. {@code {identifier: "ISIN"}})
 */
public record HandlerConfig(
    String reason,
    String type,
    Map<String, String> params
) {
  public HandlerConfig {
    if (params == null) params = Map.of();
  }
}

package dev.mars.agent.config;

/**
 * Agent runner configuration.
 *
 * @param maxSteps  maximum iterative steps before safety stop
 * @param timeoutMs total agent execution budget in milliseconds
 */
public record AgentConfig(
    int maxSteps,
    long timeoutMs
) {
  public AgentConfig {
    if (maxSteps <= 0) throw new IllegalArgumentException("maxSteps must be > 0");
    if (timeoutMs <= 0) throw new IllegalArgumentException("timeoutMs must be > 0");
  }
}

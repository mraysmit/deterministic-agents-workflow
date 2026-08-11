package dev.mars.agent.config;

/**
 * Agent runner configuration.
 *
 * @param maxSteps  maximum iterative steps before safety stop
 * @param timeoutMs timeout for agent dispatch in milliseconds
 */
public record AgentConfig(
    int maxSteps,
    long timeoutMs
) {}

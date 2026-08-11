package dev.mars.agent.config;

/**
 * Configuration for an agent tool.
 *
 * @param type the tool alias (e.g. {@code "publish-event"},
 *             {@code "raise-ticket"}) resolved by the tool factory
 */
public record ToolConfig(
    String type
) {}

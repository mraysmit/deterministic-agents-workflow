package dev.mars.agent.config;

/**
 * Event bus address configuration.
 *
 * @param inbound the address for inbound failure events (request/reply)
 * @param agent   the address for forwarding to the LLM agent (request/reply)
 * @param events  the address for outbound domain events (publish/subscribe)
 */
public record AddressesConfig(
    String inbound,
    String agent,
    String events
) {}

package dev.mars.mcp.tool;

import io.vertx.core.json.JsonObject;

/**
 * Immutable context carried through each step of an agent execution loop.
 *
 * @param correlationId a unique ID tying together all steps and events for a
 *                      single agent invocation (generated if not present on
 *                      the inbound event)
 * @param caseId        the domain-level case identifier (currently the
 *                      {@code tradeId}); used as the key for the
 *                      {@link dev.mars.sample.agent.memory.MemoryStore}
 * @param state         the accumulated state loaded from the memory store
 *                      at the start of the run; includes step count, last
 *                      result, and timestamps
 */
public record AgentContext(String correlationId, String caseId, JsonObject state) {}

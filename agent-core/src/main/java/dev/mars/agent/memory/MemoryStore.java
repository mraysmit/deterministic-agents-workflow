package dev.mars.agent.memory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Persistent (or in-memory) store for agent case state and step logs.
 *
 * <p>Each case is identified by a string {@code caseId} (typically the
 * trade ID). The store tracks:
 * <ul>
 *   <li><b>State</b> — a single {@code JsonObject} per case with metadata
 *       (step count, timestamps, last step result).</li>
 *   <li><b>Log</b> — an append-only sequence of step entries recording
 *       every command the agent executed and its result.</li>
 * </ul>
 *
 * <p>Implementations must be safe to call from the Vert.x event loop
 * (i.e. return {@link Future}s and never block).
 *
 * @see InMemoryMemoryStore
 */
public interface MemoryStore {

  /**
   * Load the current state for a case, creating a default state object
   * if the case has not been seen before.
   *
   * @param caseId the domain-level case identifier
   * @return a Future with a <em>copy</em> of the case state
   */
  Future<JsonObject> load(String caseId);

  /**
   * Append a step entry to the case log and update the persistent state
   * (incrementing step count, recording the latest result, etc.).
   *
   * @param caseId the domain-level case identifier
   * @param entry  the step entry (command, tool result, timestamp)
   * @return a Future that completes when the write is durable
   */
  Future<Void> append(String caseId, JsonObject entry);
}

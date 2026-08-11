package dev.mars.agent.llm;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Abstraction over a Large Language Model (LLM) that decides the next action
 * the agent should take.
 *
 * <h2>Role in the agent loop</h2>
 * The {@link dev.mars.agent.runner.AgentRunnerVerticle AgentRunnerVerticle}
 * does <b>not</b> decide which tool to call — that decision is delegated
 * entirely to the {@code LlmClient}. The runner simply:
 * <ol>
 *   <li>Passes the failure event and accumulated case state to
 *       {@link #decideNext}.</li>
 *   <li>Receives back a structured command that names a tool and its
 *       arguments.</li>
 *   <li>Looks up that tool in its allow-listed tool map and invokes it.</li>
 *   <li>Loops if the command says {@code stop: false}.</li>
 * </ol>
 *
 * <h2>Command schema</h2>
 * Implementations must return a {@code JsonObject} with the following shape:
 * <pre>
 * {
 *   "intent": "CALL_TOOL",         // action type (currently only CALL_TOOL)
 *   "tool":   "case.raiseTicket",   // name of the tool the agent should invoke
 *   "args":   { ... },              // arguments forwarded to Tool.invoke()
 *   "stop":   true | false          // true  = this is the final step
 *                                    // false = runner should call decideNext again
 * }
 * </pre>
 *
 * <h2>Implementations</h2>
 * <ul>
 *   <li>{@link StubLlmClient} — rule-based stub for local development
 *       and testing; no network calls.</li>
 *   <li>{@link OpenAiLlmClient} — placeholder for a real LLM integration
 *       (e.g. OpenAI, Azure OpenAI, Anthropic). Replace the stub with
 *       this in {@code MainVerticle} to enable real LLM-driven decisions.</li>
 * </ul>
 *
 * <p>The contract is intentionally simple so that the same interface can be
 * backed by a local stub, an HTTP call to an LLM provider, or a
 * retrieval-augmented pipeline.
 *
 * @see dev.mars.agent.runner.AgentRunnerVerticle
 * @see StubLlmClient
 * @see OpenAiLlmClient
 */
public interface LlmClient {

  /**
   * Given the failure event and the current case state, decide what the agent
   * should do next.
   *
   * @param event the original failure event ({@code tradeId}, {@code reason}, etc.)
   * @param state the accumulated case state from the {@link dev.mars.agent.memory.MemoryStore}
   * @return a Future containing a structured command JSON
   */
  Future<JsonObject> decideNext(JsonObject event, JsonObject state);
}

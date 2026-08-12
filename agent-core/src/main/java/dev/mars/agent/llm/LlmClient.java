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
 *   <li>{@link OpenAiLlmClient} — calls a real LLM via the OpenAI Chat
 *       Completions API (also compatible with Azure OpenAI and any
 *       provider exposing the same REST shape) using function-calling.</li>
 * </ul>
 *
 * <p>This is a {@link FunctionalInterface}, so tests that need to drive the
 * runner with a specific command can supply one inline:
 * <pre>
 * LlmClient llm = (event, state) -&gt; Future.succeededFuture(
 *     new JsonObject().put("intent", "CALL_TOOL")...);
 * </pre>
 * That is the <b>only</b> sanctioned substitute. Do not add a scripted or
 * rule-based implementation to production code: it makes the agent path
 * appear covered while testing nothing but the script.
 *
 * @see dev.mars.agent.runner.AgentRunnerVerticle
 * @see OpenAiLlmClient
 */
@FunctionalInterface
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

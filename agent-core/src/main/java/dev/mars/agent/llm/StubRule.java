package dev.mars.agent.llm;

import io.vertx.core.json.JsonObject;

/**
 * A rule that the {@link StubLlmClient} evaluates against an incoming
 * event to decide which tool command to return.
 *
 * <p>Implementations inspect the event (e.g. keyword matching on the
 * {@code reason} field) and either return a fully-formed command
 * {@link JsonObject} or {@code null} to indicate no match, allowing the
 * next rule in the chain to be tried.
 *
 * <h2>Command schema</h2>
 * A matching rule must return a JSON object following the same contract
 * as {@link LlmClient#decideNext}:
 * <pre>
 * {
 *   "intent": "CALL_TOOL",
 *   "tool":   "tool.name",
 *   "args":   { ... },
 *   "stop":   true | false
 * }
 * </pre>
 *
 * @see StubLlmClient
 * @see StubLlmClient#keywordRule(String, String, java.util.function.Function)
 */
@FunctionalInterface
public interface StubRule {

  /**
   * Evaluate the event against this rule.
   *
   * @param event the incoming failure event
   * @return a command {@link JsonObject} if this rule matches, or
   *         {@code null} to fall through to the next rule
   */
  JsonObject tryMatch(JsonObject event);

  /**
   * Evaluate the event against this rule, with access to the agent
   * state for multi-step reasoning.
   *
   * <p>The default implementation ignores the state and delegates to
   * {@link #tryMatch(JsonObject)}, so existing single-step rules work
   * unchanged. Multi-step rules should override this method to inspect
   * the state (e.g. step count, prior tool results) and vary their
   * behaviour across iterations.
   *
   * @param event the incoming failure event
   * @param state the agent state snapshot (contains {@code step},
   *              {@code last}, etc.)
   * @return a command {@link JsonObject} if this rule matches, or
   *         {@code null} to fall through to the next rule
   */
  default JsonObject tryMatch(JsonObject event, JsonObject state) {
    return tryMatch(event);
  }
}

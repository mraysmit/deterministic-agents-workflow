package dev.mars.agent.llm;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * A rule-based {@link LlmClient} stub for local development and testing.
 *
 * <p>Instead of calling a real LLM, this implementation evaluates an
 * ordered list of {@link StubRule}s against the incoming event. The first
 * rule whose {@link StubRule#tryMatch} returns a non-null command wins;
 * if no rule matches, the {@code fallback} rule is used.
 *
 * <p>All rules — including the fallback — are injected via the
 * constructor, so this class contains <b>zero domain knowledge</b>.
 * The application's bootstrap code (e.g.&nbsp;{@code MainVerticle})
 * assembles the rule list, making it easy to see exactly which rules
 * and agent behaviours are plugged in for a given use case.
 *
 * <h2>Building rules</h2>
 * <ul>
 *   <li>Use {@link #keywordRule(String, String, Function)} for simple
 *       keyword-on-reason matching.</li>
 *   <li>Implement {@link StubRule} directly for richer logic (regex,
 *       field combinations, look-ups, etc.).</li>
 * </ul>
 *
 * @see LlmClient
 * @see StubRule
 */
public class StubLlmClient implements LlmClient {

  private static final Logger LOG = Logger.getLogger(StubLlmClient.class.getName());

  private final List<StubRule> rules;
  private final StubRule fallback;

  /**
   * Creates a new stub client with the given rules and fallback.
   *
   * @param rules    ordered list of rules evaluated first-match-wins
   * @param fallback rule invoked when no other rule matches; must not
   *                 return {@code null}
   */
  public StubLlmClient(List<StubRule> rules, StubRule fallback) {
    this.rules = List.copyOf(rules);
    this.fallback = Objects.requireNonNull(fallback, "fallback rule must not be null");
  }

  /**
   * Convenience factory for a keyword-matching rule.
   *
   * <p>The rule matches when the event's {@code reason} field contains
   * the given {@code keyword} (case-insensitive). On match it returns a
   * {@code CALL_TOOL} command targeting the specified tool with
   * {@code stop: true}.
   *
   * @param keyword     substring to look for in the reason (case-insensitive)
   * @param toolName    the tool to invoke on match
   * @param argsBuilder builds the {@code args} object from the event
   * @return a reusable {@link StubRule}
   */
  public static StubRule keywordRule(String keyword, String toolName,
                                     Function<JsonObject, JsonObject> argsBuilder) {
    String lowerKeyword = keyword.toLowerCase();
    return event -> {
      String reason = event.getString("reason", "").toLowerCase();
      if (reason.contains(lowerKeyword)) {
        return new JsonObject()
          .put("intent", "CALL_TOOL")
          .put("tool", toolName)
          .put("args", argsBuilder.apply(event))
          .put("stop", true);
      }
      return null;
    };
  }

  @Override
  public Future<JsonObject> decideNext(JsonObject event, JsonObject state) {
    String reason = event.getString("reason", "<none>");
    LOG.info("StubLlmClient evaluating " + rules.size() + " rules for reason='" + reason
        + "' step=" + (state != null ? state.getInteger("step", 0) : 0));

    for (StubRule rule : rules) {
      JsonObject cmd = rule.tryMatch(event, state);
      if (cmd != null) {
        LOG.info("Stub rule matched: tool=" + cmd.getString("tool")
            + " stop=" + cmd.getBoolean("stop", true));
        return Future.succeededFuture(cmd);
      }
    }

    LOG.info("No stub rule matched for reason='" + reason + "' — using fallback");
    return Future.succeededFuture(fallback.tryMatch(event, state));
  }
}

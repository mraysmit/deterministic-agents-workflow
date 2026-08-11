package dev.mars.agent.llm;

import java.util.List;

/**
 * An immutable bundle of {@link StubRule}s and a fallback rule, ready to
 * be handed to a {@link StubLlmClient}.
 *
 * <p>The {@code rules} list is evaluated first-match-wins. If none match,
 * the {@code fallback} is invoked (it must never return {@code null}).
 *
 * @param rules    ordered matching rules
 * @param fallback catch-all rule applied when no other rule matches
 * @see StubRuleLoader
 * @see StubLlmClient
 */
public record StubRuleSet(List<StubRule> rules, StubRule fallback) {

  /**
   * Creates a {@link StubLlmClient} from this rule set.
   *
   * @return a new client wired with these rules
   */
  public StubLlmClient toClient() {
    return new StubLlmClient(rules, fallback);
  }
}

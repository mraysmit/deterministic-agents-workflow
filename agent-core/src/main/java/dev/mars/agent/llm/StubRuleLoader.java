package dev.mars.agent.llm;

/**
 * Strategy for loading the set of {@link StubRule}s that a
 * {@link StubLlmClient} will evaluate.
 *
 * <p>Each implementation represents a <b>use case</b> — for example
 * trade-failure processing, settlement-failure processing, or margin-call
 * handling — and assembles the appropriate rules and fallback.
 *
 * <p>The loader is called once at bootstrap time.  Implementations may
 * read rules from code, configuration files, a database, or any other
 * source.
 *
 * <h2>Usage</h2>
 * <pre>
 * StubRuleLoader loader = new TradeFailureRuleLoader();
 * StubLlmClient  client = loader.load().toClient();
 * </pre>
 *
 * @see StubRuleSet
 * @see StubLlmClient
 */
@FunctionalInterface
public interface StubRuleLoader {

  /**
   * Load the rules for a particular use case.
   *
   * @return a {@link StubRuleSet} containing ordered rules and a fallback
   */
  StubRuleSet load();
}

package dev.mars.agent.handler;
import dev.mars.mcp.tool.Tool;

import dev.mars.agent.llm.StubLlmClient;
import dev.mars.agent.llm.StubRuleSet;
import org.junit.jupiter.api.Test;
import io.vertx.core.json.JsonObject;

import static org.junit.jupiter.api.Assertions.*;

class TradeFailureRuleLoaderTest {

  @Test
  void load_returns_non_null_rule_set() {
    var loader = new TradeFailureRuleLoader();
    StubRuleSet ruleSet = loader.load();
    assertNotNull(ruleSet);
    assertNotNull(ruleSet.rules());
    assertNotNull(ruleSet.fallback());
  }

  @Test
  void lei_step0_triggers_lookup() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-1")
        .put("reason", "LEI not found");
    var state = new JsonObject().put("step", 0);

    var result = client.decideNext(event, state).result();
    assertEquals("CALL_TOOL", result.getString("intent"));
    assertEquals("data.lookup", result.getString("tool"));
    assertFalse(result.getBoolean("stop"));
  }

  @Test
  void lei_step1_triggers_classify() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-1")
        .put("reason", "LEI not found");
    var state = new JsonObject().put("step", 1);

    var result = client.decideNext(event, state).result();
    assertEquals("CALL_TOOL", result.getString("intent"));
    assertEquals("case.classify", result.getString("tool"));
    assertFalse(result.getBoolean("stop"));
  }

  @Test
  void lei_step2_triggers_raise_ticket() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-1")
        .put("reason", "LEI not found");
    var state = new JsonObject().put("step", 2);

    var result = client.decideNext(event, state).result();
    assertEquals("CALL_TOOL", result.getString("intent"));
    assertEquals("case.raiseTicket", result.getString("tool"));
    assertTrue(result.getBoolean("stop"));
    JsonObject args = result.getJsonObject("args");
    assertEquals("T-1", args.getString("tradeId"));
    assertNotNull(args.getString("detail"));
  }

  @Test
  void fallback_step0_triggers_lookup() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-2")
        .put("reason", "Unknown error XYZ");
    var state = new JsonObject().put("step", 0);

    var result = client.decideNext(event, state).result();
    assertEquals("CALL_TOOL", result.getString("intent"));
    assertEquals("data.lookup", result.getString("tool"));
    assertFalse(result.getBoolean("stop"));
  }

  @Test
  void fallback_step2_triggers_notify() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-2")
        .put("reason", "Unknown error XYZ");
    var state = new JsonObject().put("step", 2);

    var result = client.decideNext(event, state).result();
    assertEquals("CALL_TOOL", result.getString("intent"));
    assertEquals("comms.notify", result.getString("tool"));
    assertTrue(result.getBoolean("stop"));
  }

  // ── Settlement Amount Mismatch rule ───────────────────────────────

  @Test
  void mismatch_step0_triggers_lookup() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-500")
        .put("reason", "Settlement amount mismatch");
    var state = new JsonObject().put("step", 0);

    var result = client.decideNext(event, state).result();
    assertEquals("CALL_TOOL", result.getString("intent"));
    assertEquals("data.lookup", result.getString("tool"));
    assertFalse(result.getBoolean("stop"));
    assertNotNull(result.getString("reasoning"));
  }

  @Test
  void mismatch_step1_triggers_classify() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-500")
        .put("reason", "Settlement amount mismatch");
    var state = new JsonObject().put("step", 1);

    var result = client.decideNext(event, state).result();
    assertEquals("CALL_TOOL", result.getString("intent"));
    assertEquals("case.classify", result.getString("tool"));
    assertEquals("HIGH", result.getJsonObject("args").getString("severity"));
    assertEquals("Settlement", result.getJsonObject("args").getString("category"));
    assertFalse(result.getBoolean("stop"));
  }

  @Test
  void mismatch_step2_triggers_ticket() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-500")
        .put("reason", "Settlement amount mismatch");
    var state = new JsonObject().put("step", 2);

    var result = client.decideNext(event, state).result();
    assertEquals("CALL_TOOL", result.getString("intent"));
    assertEquals("case.raiseTicket", result.getString("tool"));
    assertFalse(result.getBoolean("stop"));
  }

  @Test
  void mismatch_step3_triggers_notify_and_stops() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-500")
        .put("reason", "Settlement amount mismatch");
    var state = new JsonObject().put("step", 3);

    var result = client.decideNext(event, state).result();
    assertEquals("CALL_TOOL", result.getString("intent"));
    assertEquals("comms.notify", result.getString("tool"));
    assertTrue(result.getBoolean("stop"));
    assertEquals("pagerduty", result.getJsonObject("args").getString("channel"));
  }

  // ── Duplicate Trade rule ──────────────────────────────────────────

  @Test
  void duplicate_step0_triggers_lookup() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-600")
        .put("reason", "Possible duplicate trade");
    var state = new JsonObject().put("step", 0);

    var result = client.decideNext(event, state).result();
    assertEquals("data.lookup", result.getString("tool"));
    assertFalse(result.getBoolean("stop"));
  }

  @Test
  void duplicate_step1_triggers_classify_critical() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-600")
        .put("reason", "Possible duplicate trade");
    var state = new JsonObject().put("step", 1);

    var result = client.decideNext(event, state).result();
    assertEquals("case.classify", result.getString("tool"));
    assertEquals("CRITICAL", result.getJsonObject("args").getString("severity"));
    assertEquals("Operations", result.getJsonObject("args").getString("category"));
    assertFalse(result.getBoolean("stop"));
  }

  @Test
  void duplicate_step3_triggers_pagerduty() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-600")
        .put("reason", "Possible duplicate trade");
    var state = new JsonObject().put("step", 3);

    var result = client.decideNext(event, state).result();
    assertEquals("comms.notify", result.getString("tool"));
    assertEquals("pagerduty", result.getJsonObject("args").getString("channel"));
    assertTrue(result.getBoolean("stop"));
  }

  // ── Regulatory Deadline rule ──────────────────────────────────────

  @Test
  void regulatory_step0_triggers_lookup() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-700")
        .put("reason", "Regulatory T+1 deadline at risk");
    var state = new JsonObject().put("step", 0);

    var result = client.decideNext(event, state).result();
    assertEquals("data.lookup", result.getString("tool"));
    assertFalse(result.getBoolean("stop"));
  }

  @Test
  void regulatory_step1_triggers_classify_compliance() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-700")
        .put("reason", "Regulatory T+1 deadline at risk");
    var state = new JsonObject().put("step", 1);

    var result = client.decideNext(event, state).result();
    assertEquals("case.classify", result.getString("tool"));
    assertEquals("CRITICAL", result.getJsonObject("args").getString("severity"));
    assertEquals("Compliance", result.getJsonObject("args").getString("category"));
    assertFalse(result.getBoolean("stop"));
  }

  @Test
  void regulatory_step3_triggers_pagerduty_to_compliance() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-700")
        .put("reason", "Regulatory T+1 deadline at risk");
    var state = new JsonObject().put("step", 3);

    var result = client.decideNext(event, state).result();
    assertEquals("comms.notify", result.getString("tool"));
    assertEquals("pagerduty", result.getJsonObject("args").getString("channel"));
    assertEquals("Compliance", result.getJsonObject("args").getString("team"));
    assertTrue(result.getBoolean("stop"));
  }

  // ── Sanctions / AML Screening rule ────────────────────────────────

  @Test
  void sanctions_step0_triggers_lookup() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-800")
        .put("reason", "OFAC screening flag on counterparty");
    var state = new JsonObject().put("step", 0);

    var result = client.decideNext(event, state).result();
    assertEquals("CALL_TOOL", result.getString("intent"));
    assertEquals("data.lookup", result.getString("tool"));
    assertFalse(result.getBoolean("stop"));
    assertNotNull(result.getString("reasoning"));
  }

  @Test
  void sanctions_step1_triggers_classify_with_analysis() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-800")
        .put("reason", "OFAC screening flag on counterparty");
    var state = new JsonObject().put("step", 1);

    var result = client.decideNext(event, state).result();
    assertEquals("case.classify", result.getString("tool"));
    assertEquals("HIGH", result.getJsonObject("args").getString("severity"));
    assertEquals("Sanctions", result.getJsonObject("args").getString("category"));
    // Reasoning should show multi-factor analysis
    assertTrue(result.getString("reasoning").contains("false positive"));
    assertFalse(result.getBoolean("stop"));
  }

  @Test
  void sanctions_step2_triggers_ticket() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-800")
        .put("reason", "OFAC screening flag on counterparty");
    var state = new JsonObject().put("step", 2);

    var result = client.decideNext(event, state).result();
    assertEquals("case.raiseTicket", result.getString("tool"));
    assertEquals("Sanctions", result.getJsonObject("args").getString("category"));
    assertNotNull(result.getJsonObject("args").getString("detail"));
    assertFalse(result.getBoolean("stop"));
  }

  @Test
  void sanctions_step3_triggers_publish_event() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-800")
        .put("reason", "OFAC screening flag on counterparty");
    var state = new JsonObject().put("step", 3);

    var result = client.decideNext(event, state).result();
    assertEquals("events.publish", result.getString("tool"));
    assertEquals("SanctionsScreeningReview", result.getJsonObject("args").getString("type"));
    assertFalse(result.getBoolean("stop"));
  }

  @Test
  void sanctions_step4_triggers_email_not_pagerduty() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-800")
        .put("reason", "OFAC screening flag on counterparty");
    var state = new JsonObject().put("step", 4);

    var result = client.decideNext(event, state).result();
    assertEquals("comms.notify", result.getString("tool"));
    // LLM chose email (not PagerDuty) because it assessed probable false positive
    assertEquals("email", result.getJsonObject("args").getString("channel"));
    assertTrue(result.getBoolean("stop"));
    // Reasoning should explain channel selection
    assertTrue(result.getString("reasoning").contains("EMAIL"));
  }

  // ── Multi-Leg Cascade rule ────────────────────────────────────────

  @Test
  void cascade_step0_triggers_lookup() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-900")
        .put("reason", "Linked trade cascade failure on swap leg");
    var state = new JsonObject().put("step", 0);

    var result = client.decideNext(event, state).result();
    assertEquals("data.lookup", result.getString("tool"));
    assertFalse(result.getBoolean("stop"));
  }

  @Test
  void cascade_step1_triggers_classify_critical() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-900")
        .put("reason", "Linked trade cascade failure on swap leg");
    var state = new JsonObject().put("step", 1);

    var result = client.decideNext(event, state).result();
    assertEquals("case.classify", result.getString("tool"));
    assertEquals("CRITICAL", result.getJsonObject("args").getString("severity"));
    assertEquals("Settlement", result.getJsonObject("args").getString("category"));
    // Reasoning should demonstrate structural understanding
    assertTrue(result.getString("reasoning").contains("trade structure")
        || result.getString("reasoning").contains("TRADE STRUCTURE"));
    assertFalse(result.getBoolean("stop"));
  }

  @Test
  void cascade_step2_triggers_publish_event() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-900")
        .put("reason", "Linked trade cascade failure on swap leg");
    var state = new JsonObject().put("step", 2);

    var result = client.decideNext(event, state).result();
    assertEquals("events.publish", result.getString("tool"));
    assertEquals("CascadeFailureDetected", result.getJsonObject("args").getString("type"));
    assertFalse(result.getBoolean("stop"));
  }

  @Test
  void cascade_step3_triggers_ticket_with_resolution_sequence() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-900")
        .put("reason", "Linked trade cascade failure on swap leg");
    var state = new JsonObject().put("step", 3);

    var result = client.decideNext(event, state).result();
    assertEquals("case.raiseTicket", result.getString("tool"));
    String detail = result.getJsonObject("args").getString("detail");
    assertTrue(detail.contains("RESOLUTION SEQUENCE"));
    assertTrue(detail.contains("GBAGDEFFXXX"));
    assertFalse(result.getBoolean("stop"));
  }

  @Test
  void cascade_step4_triggers_pagerduty_multi_team() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-900")
        .put("reason", "Linked trade cascade failure on swap leg");
    var state = new JsonObject().put("step", 4);

    var result = client.decideNext(event, state).result();
    assertEquals("comms.notify", result.getString("tool"));
    assertEquals("pagerduty", result.getJsonObject("args").getString("channel"));
    // Should notify multiple teams
    String team = result.getJsonObject("args").getString("team");
    assertTrue(team.contains("Settlement") || team.contains("Operations"));
    assertTrue(team.contains("Risk") || team.contains("Trading"));
    assertTrue(result.getBoolean("stop"));
  }

  // ── Counterparty Credit Event rule ────────────────────────────────

  @Test
  void credit_event_step0_triggers_lookup() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-1000")
        .put("reason", "Counterparty credit downgrade to CCC+");
    var state = new JsonObject().put("step", 0);

    var result = client.decideNext(event, state).result();
    assertEquals("data.lookup", result.getString("tool"));
    assertFalse(result.getBoolean("stop"));
    // Reasoning should mention portfolio-level analysis
    assertTrue(result.getString("reasoning").contains("portfolio")
        || result.getString("reasoning").contains("ALL outstanding"));
  }

  @Test
  void credit_event_step1_triggers_classify_with_netting_analysis() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-1000")
        .put("reason", "Counterparty credit downgrade to CCC+");
    var state = new JsonObject().put("step", 1);

    var result = client.decideNext(event, state).result();
    assertEquals("case.classify", result.getString("tool"));
    assertEquals("CRITICAL", result.getJsonObject("args").getString("severity"));
    assertEquals("CreditRisk", result.getJsonObject("args").getString("category"));
    // Classification should include ISDA netting analysis
    String classReason = result.getJsonObject("args").getString("reason");
    assertTrue(classReason.contains("netting") || classReason.contains("ISDA"));
    assertFalse(result.getBoolean("stop"));
  }

  @Test
  void credit_event_step2_triggers_ticket_with_prioritised_actions() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-1000")
        .put("reason", "Counterparty credit downgrade to CCC+");
    var state = new JsonObject().put("step", 2);

    var result = client.decideNext(event, state).result();
    assertEquals("case.raiseTicket", result.getString("tool"));
    String detail = result.getJsonObject("args").getString("detail");
    // Should contain the critical "DO NOT" warning about the IRS position
    assertTrue(detail.contains("DO NOT"));
    // Should contain exposure waterfall
    assertTrue(detail.contains("netting") || detail.contains("Net"));
    assertFalse(result.getBoolean("stop"));
  }

  @Test
  void credit_event_step3_triggers_publish_event() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-1000")
        .put("reason", "Counterparty credit downgrade to CCC+");
    var state = new JsonObject().put("step", 3);

    var result = client.decideNext(event, state).result();
    assertEquals("events.publish", result.getString("tool"));
    assertEquals("CounterpartyCreditEvent", result.getJsonObject("args").getString("type"));
    assertFalse(result.getBoolean("stop"));
  }

  @Test
  void credit_event_step4_triggers_pagerduty_multi_team() {
    var loader = new TradeFailureRuleLoader();
    StubLlmClient client = loader.load().toClient();

    var event = new JsonObject()
        .put("tradeId", "T-1000")
        .put("reason", "Counterparty credit downgrade to CCC+");
    var state = new JsonObject().put("step", 4);

    var result = client.decideNext(event, state).result();
    assertEquals("comms.notify", result.getString("tool"));
    assertEquals("pagerduty", result.getJsonObject("args").getString("channel"));
    String team = result.getJsonObject("args").getString("team");
    assertTrue(team.contains("Credit Risk"));
    assertTrue(team.contains("Legal"));
    assertTrue(result.getBoolean("stop"));
  }
}

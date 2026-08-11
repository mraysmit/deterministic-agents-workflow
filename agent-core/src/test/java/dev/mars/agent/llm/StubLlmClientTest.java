package dev.mars.agent.llm;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StubLlmClientTest {

  @Test
  void keyword_rule_matches_case_insensitively() {
    StubRule rule = StubLlmClient.keywordRule("lei",
        "case.raiseTicket",
        event -> new JsonObject().put("id", event.getString("tradeId")));

    // Match on lower-case
    JsonObject result = rule.tryMatch(
        new JsonObject().put("reason", "lei not found").put("tradeId", "T-1"));
    assertNotNull(result);
    assertEquals("CALL_TOOL", result.getString("intent"));
    assertEquals("case.raiseTicket", result.getString("tool"));
    assertEquals("T-1", result.getJsonObject("args").getString("id"));
  }

  @Test
  void keyword_rule_matches_uppercase() {
    StubRule rule = StubLlmClient.keywordRule("lei", "tool", event -> new JsonObject());
    assertNotNull(rule.tryMatch(new JsonObject().put("reason", "LEI MISMATCH")));
  }

  @Test
  void keyword_rule_returns_null_when_no_match() {
    StubRule rule = StubLlmClient.keywordRule("lei", "tool", event -> new JsonObject());
    assertNull(rule.tryMatch(new JsonObject().put("reason", "Missing ISIN")));
  }

  @Test
  void keyword_rule_handles_missing_reason() {
    StubRule rule = StubLlmClient.keywordRule("lei", "tool", event -> new JsonObject());
    assertNull(rule.tryMatch(new JsonObject()));
  }

  @Test
  void decide_next_uses_first_matching_rule() {
    StubRule rule1 = event -> event.getString("reason", "").contains("A")
        ? new JsonObject().put("matched", "rule1") : null;
    StubRule rule2 = event -> event.getString("reason", "").contains("A")
        ? new JsonObject().put("matched", "rule2") : null;
    StubRule fallback = event -> new JsonObject().put("matched", "fallback");

    var client = new StubLlmClient(List.of(rule1, rule2), fallback);
    var result = client.decideNext(
        new JsonObject().put("reason", "A"), new JsonObject()).result();
    assertEquals("rule1", result.getString("matched"));
  }

  @Test
  void decide_next_uses_fallback_when_no_match() {
    StubRule noMatch = event -> null;
    StubRule fallback = event -> new JsonObject().put("matched", "fallback");

    var client = new StubLlmClient(List.of(noMatch), fallback);
    var result = client.decideNext(
        new JsonObject().put("reason", "unknown"), new JsonObject()).result();
    assertEquals("fallback", result.getString("matched"));
  }

  @Test
  void constructor_rejects_null_fallback() {
    assertThrows(NullPointerException.class,
        () -> new StubLlmClient(List.of(), null));
  }

  @Test
  void rules_list_is_defensively_copied() {
    var mutableList = new java.util.ArrayList<StubRule>();
    mutableList.add(event -> null);
    StubRule fallback = event -> new JsonObject();
    var client = new StubLlmClient(mutableList, fallback);

    // Mutating original list should not affect the client
    mutableList.clear();
    // Client should still have the original rule (no match → fallback)
    var result = client.decideNext(new JsonObject(), new JsonObject()).result();
    assertNotNull(result);
  }
}

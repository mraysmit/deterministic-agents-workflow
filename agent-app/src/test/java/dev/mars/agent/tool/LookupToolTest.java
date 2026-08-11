package dev.mars.agent.tool;

import dev.mars.mcp.tool.AgentContext;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class LookupToolTest {

  private AgentContext testCtx() {
    return new AgentContext("corr-1", "case-1", new JsonObject());
  }

  @Test
  void name_is_data_lookup() {
    var tool = new LookupTool();
    assertEquals("data.lookup", tool.name());
  }

  @Test
  void description_is_not_empty() {
    var tool = new LookupTool();
    assertFalse(tool.description().isBlank());
  }

  @Test
  void schema_declares_required_properties() {
    var tool = new LookupTool();
    var schema = tool.schema();
    assertEquals("object", schema.getString("type"));
    var props = schema.getJsonObject("properties");
    assertNotNull(props.getJsonObject("tradeId"));
    var required = schema.getJsonArray("required");
    assertTrue(required.contains("tradeId"));
  }

  @Test
  void invoke_returns_full_data_without_fields_filter() {
    var tool = new LookupTool();
    var args = new JsonObject().put("tradeId", "T-99");

    var result = tool.invoke(args, testCtx()).result();
    assertEquals("found", result.getString("status"));
    assertEquals("T-99", result.getString("tradeId"));
    var data = result.getJsonObject("data");
    assertNotNull(data);
    // should contain all sections: counterparty, security, settlement
    assertTrue(data.containsKey("counterparty"));
    assertTrue(data.containsKey("security"));
    assertTrue(data.containsKey("settlement"));
  }

  @Test
  void invoke_with_fields_filter_returns_subset() {
    var tool = new LookupTool();
    var args = new JsonObject()
        .put("tradeId", "T-99")
        .put("fields", new JsonArray().add("counterparty"));

    var result = tool.invoke(args, testCtx()).result();
    assertEquals("found", result.getString("status"));
    var data = result.getJsonObject("data");
    assertTrue(data.containsKey("counterparty"));
    assertFalse(data.containsKey("security"));
    assertFalse(data.containsKey("settlement"));
  }

  // ── Scenario-specific data tests ──────────────────────────────────

  @Test
  void invoke_t500_returns_settlement_mismatch_data() {
    var tool = new LookupTool();
    var args = new JsonObject().put("tradeId", "T-500");

    var result = tool.invoke(args, testCtx()).result();
    var data = result.getJsonObject("data");
    var settlement = data.getJsonObject("settlement");

    assertEquals("AMOUNT_MISMATCH", settlement.getString("status"));
    assertEquals(1_250_000, settlement.getInteger("expectedAmount"));
    assertEquals(1_500_000, settlement.getInteger("counterpartyAmount"));
    assertEquals(250_000, settlement.getInteger("discrepancy"));

    // FX data should be present
    var fx = data.getJsonObject("fx");
    assertNotNull(fx);
    assertEquals("EUR/USD", fx.getString("pair"));
    assertEquals(1.0842, fx.getDouble("ourRate"));
  }

  @Test
  void invoke_t600_returns_duplicate_trade_data() {
    var tool = new LookupTool();
    var args = new JsonObject().put("tradeId", "T-600");

    var result = tool.invoke(args, testCtx()).result();
    var data = result.getJsonObject("data");

    assertEquals("PENDING_REVIEW", data.getJsonObject("settlement").getString("status"));

    var related = data.getJsonArray("relatedTrades");
    assertNotNull(related);
    assertEquals(1, related.size());

    var rel = related.getJsonObject(0);
    assertEquals("T-599", rel.getString("tradeId"));
    assertEquals(0.99, rel.getDouble("matchScore"));
    assertEquals("3 seconds earlier", rel.getString("timeDelta"));
  }

  @Test
  void invoke_t700_returns_regulatory_deadline_data() {
    var tool = new LookupTool();
    var args = new JsonObject().put("tradeId", "T-700");

    var result = tool.invoke(args, testCtx()).result();
    var data = result.getJsonObject("data");
    var settlement = data.getJsonObject("settlement");

    assertEquals("2026-03-03", settlement.getString("valueDate"));
    assertEquals("SEC Rule 15c6-1 (T+1)", settlement.getString("regulatoryRule"));
    assertEquals("16:30 ET", settlement.getString("cutoffTime"));
    assertTrue(settlement.getBoolean("failReportRequired"));
  }

  @Test
  void invoke_t500_with_fields_filter_includes_fx() {
    var tool = new LookupTool();
    var args = new JsonObject()
        .put("tradeId", "T-500")
        .put("fields", new JsonArray().add("settlement").add("fx"));

    var result = tool.invoke(args, testCtx()).result();
    var data = result.getJsonObject("data");
    assertTrue(data.containsKey("settlement"));
    assertTrue(data.containsKey("fx"));
    assertFalse(data.containsKey("counterparty"));
    assertFalse(data.containsKey("security"));
  }

  @Test
  void invoke_default_trade_returns_base_data() {
    var tool = new LookupTool();
    var args = new JsonObject().put("tradeId", "T-99");

    var result = tool.invoke(args, testCtx()).result();
    var data = result.getJsonObject("data");

    // default data should NOT have scenario-specific fields
    assertFalse(data.containsKey("fx"));
    assertFalse(data.containsKey("relatedTrades"));
    assertEquals("UNMATCHED", data.getJsonObject("settlement").getString("status"));
    assertEquals("2026-03-05", data.getJsonObject("settlement").getString("valueDate"));
  }

  // ── Sanctions screening data tests ────────────────────────────────

  @Test
  void invoke_t800_returns_sanctions_screening_data() {
    var tool = new LookupTool();
    var args = new JsonObject().put("tradeId", "T-800");

    var result = tool.invoke(args, testCtx()).result();
    var data = result.getJsonObject("data");

    // Counterparty should be UAE entity
    var counterparty = data.getJsonObject("counterparty");
    assertEquals("Meridian Trading FZE", counterparty.getString("name"));
    assertEquals("UAE", counterparty.getString("jurisdiction"));

    // Screening data should be present
    var screening = data.getJsonObject("screening");
    assertNotNull(screening);
    assertEquals("FLAGGED", screening.getString("status"));
    assertEquals("FUZZY_NAME", screening.getString("matchType"));
    assertEquals(0.78, screening.getDouble("matchScore"));
    assertEquals("OFAC SDN List", screening.getString("watchlist"));

    // Settlement should be on screening hold
    assertEquals("SCREENING_HOLD", data.getJsonObject("settlement").getString("status"));
  }

  @Test
  void invoke_t800_screening_includes_analysis_context() {
    var tool = new LookupTool();
    var args = new JsonObject()
        .put("tradeId", "T-800")
        .put("fields", new JsonArray().add("screening"));

    var result = tool.invoke(args, testCtx()).result();
    var screening = result.getJsonObject("data").getJsonObject("screening");

    // Additional context for LLM analysis
    var context = screening.getJsonObject("additionalContext");
    assertNotNull(context);
    assertFalse(context.getBoolean("jurisdictionMatch"));
    assertFalse(context.getBoolean("sectorMatch"));
    assertEquals(0, context.getInteger("historicalFlags"));
    assertEquals(7, context.getInteger("yearsAsClient"));
  }

  // ── Multi-leg cascade data tests ──────────────────────────────────

  @Test
  void invoke_t900_returns_trade_structure_data() {
    var tool = new LookupTool();
    var args = new JsonObject().put("tradeId", "T-900");

    var result = tool.invoke(args, testCtx()).result();
    var data = result.getJsonObject("data");

    // Trade structure should be present
    var structure = data.getJsonObject("tradeStructure");
    assertNotNull(structure);
    assertEquals("Interest Rate Swap", structure.getString("type"));
    assertEquals(50_000_000, structure.getInteger("notional"));

    // Pay leg should show failure
    var payLeg = structure.getJsonObject("payLeg");
    assertEquals("FAILED", payLeg.getString("status"));
    assertTrue(payLeg.getString("failReason").contains("GBAGDEFF"));

    // Receive leg should be blocked
    var receiveLeg = structure.getJsonObject("receiveLeg");
    assertEquals("PENDING", receiveLeg.getString("status"));

    // Hedge trades should be present
    var hedges = structure.getJsonArray("hedgeTrades");
    assertNotNull(hedges);
    assertEquals(2, hedges.size());

    // Exposure metrics
    var exposure = structure.getJsonObject("totalExposure");
    assertNotNull(exposure);
    assertEquals(42_500, exposure.getInteger("dv01"));
  }

  // ── Counterparty credit event data tests ──────────────────────────

  @Test
  void invoke_t1000_returns_credit_event_data() {
    var tool = new LookupTool();
    var args = new JsonObject().put("tradeId", "T-1000");

    var result = tool.invoke(args, testCtx()).result();
    var data = result.getJsonObject("data");

    // Counterparty should show credit deterioration
    var counterparty = data.getJsonObject("counterparty");
    assertEquals("Sterling & Hart Capital", counterparty.getString("name"));
    assertEquals("CCC+", counterparty.getString("rating"));
    assertEquals("BBB-", counterparty.getString("previousRating"));
    assertEquals("S&P Global", counterparty.getString("downgradedBy"));

    // Positions array should show multiple trades
    var positions = data.getJsonArray("positions");
    assertNotNull(positions);
    assertEquals(4, positions.size());

    // Settlement should be on credit hold
    assertEquals("CREDIT_HOLD", data.getJsonObject("settlement").getString("status"));
  }

  @Test
  void invoke_t1000_returns_netting_data() {
    var tool = new LookupTool();
    var args = new JsonObject()
        .put("tradeId", "T-1000")
        .put("fields", new JsonArray().add("netting"));

    var result = tool.invoke(args, testCtx()).result();
    var netting = result.getJsonObject("data").getJsonObject("netting");

    assertNotNull(netting);
    assertTrue(netting.getBoolean("isdaMasterAgreement"));
    assertTrue(netting.getBoolean("csa"));
    assertEquals(3_500_000, netting.getInteger("collateralPosted"));
    assertEquals(19_800_000, netting.getInteger("grossExposure"));
    assertEquals(8_600_000, netting.getInteger("netExposure"));
    assertEquals(5_100_000, netting.getInteger("netAfterCollateral"));
  }
}

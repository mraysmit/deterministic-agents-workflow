package dev.mars.agent.tool;

import dev.mars.mcp.tool.AgentContext;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class ClassifyToolTest {

  private AgentContext testCtx() {
    return new AgentContext("corr-1", "case-1", new JsonObject());
  }

  @Test
  void name_is_case_classify() {
    var tool = new ClassifyTool(Vertx.vertx(), "events.test");
    assertEquals("case.classify", tool.name());
  }

  @Test
  void description_is_not_empty() {
    var tool = new ClassifyTool(Vertx.vertx(), "events.test");
    assertFalse(tool.description().isBlank());
  }

  @Test
  void schema_declares_required_properties() {
    var tool = new ClassifyTool(Vertx.vertx(), "events.test");
    var schema = tool.schema();
    assertEquals("object", schema.getString("type"));
    var props = schema.getJsonObject("properties");
    assertNotNull(props.getJsonObject("tradeId"));
    assertNotNull(props.getJsonObject("category"));
    assertNotNull(props.getJsonObject("severity"));
    var required = schema.getJsonArray("required");
    assertTrue(required.contains("tradeId"));
    assertTrue(required.contains("category"));
    assertTrue(required.contains("severity"));
  }

  @Test
  void invoke_returns_classification(Vertx vertx, VertxTestContext ctx) {
    var tool = new ClassifyTool(vertx, "events.classify-test");
    var args = new JsonObject()
        .put("tradeId", "T-1")
        .put("category", "ReferenceData")
        .put("severity", "HIGH")
        .put("reason", "Missing LEI code");

    tool.invoke(args, testCtx()).onSuccess(result -> {
      assertEquals("classified", result.getString("status"));
      assertEquals("T-1", result.getString("tradeId"));
      assertEquals("ReferenceData", result.getString("category"));
      assertEquals("HIGH", result.getString("severity"));
      assertNotNull(result.getDouble("confidence"));
      ctx.completeNow();
    }).onFailure(ctx::failNow);
  }

  @Test
  void invoke_publishes_failure_classified_event(Vertx vertx, VertxTestContext ctx) {
    vertx.eventBus().consumer("events.classify-evt", msg -> {
      JsonObject event = (JsonObject) msg.body();
      assertEquals("FailureClassified", event.getString("type"));
      assertEquals("T-2", event.getString("tradeId"));
      assertEquals("Operations", event.getString("category"));
      assertEquals("MEDIUM", event.getString("severity"));
      ctx.completeNow();
    });

    var tool = new ClassifyTool(vertx, "events.classify-evt");
    var args = new JsonObject()
        .put("tradeId", "T-2")
        .put("category", "Operations")
        .put("severity", "MEDIUM")
        .put("reason", "Unmatched trade");

    tool.invoke(args, testCtx());
  }
}

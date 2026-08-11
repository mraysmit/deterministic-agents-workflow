package dev.mars.agent.tool;
import dev.mars.mcp.tool.Tool;

import dev.mars.mcp.tool.AgentContext;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class RaiseTicketToolTest {

  private AgentContext testCtx() {
    return new AgentContext("corr-1", "case-1", new JsonObject());
  }

  @Test
  void name_is_case_raise_ticket() {
    var tool = new RaiseTicketTool(Vertx.vertx(), "events.test");
    assertEquals("case.raiseTicket", tool.name());
  }

  @Test
  void description_is_not_empty() {
    var tool = new RaiseTicketTool(Vertx.vertx(), "events.test");
    assertFalse(tool.description().isBlank());
  }

  @Test
  void schema_declares_required_properties() {
    var tool = new RaiseTicketTool(Vertx.vertx(), "events.test");
    var schema = tool.schema();
    assertEquals("object", schema.getString("type"));
    var props = schema.getJsonObject("properties");
    assertNotNull(props.getJsonObject("tradeId"));
    assertNotNull(props.getJsonObject("category"));
    assertNotNull(props.getJsonObject("summary"));
    assertNotNull(props.getJsonObject("detail"));
    var required = schema.getJsonArray("required");
    assertTrue(required.contains("tradeId"));
    assertTrue(required.contains("category"));
    assertTrue(required.contains("summary"));
    assertFalse(required.contains("detail"), "detail should be optional");
  }

  @Test
  void invoke_returns_created_ticket(Vertx vertx, VertxTestContext ctx) {
    var tool = new RaiseTicketTool(vertx, "events.test");
    var args = new JsonObject()
        .put("tradeId", "T-1")
        .put("category", "ReferenceData")
        .put("summary", "Test summary");

    tool.invoke(args, testCtx()).onSuccess(result -> {
      assertEquals("created", result.getString("status"));
      assertTrue(result.getString("ticketId").startsWith("TICKET-"));
      assertEquals("T-1", result.getString("tradeId"));
      assertEquals("ReferenceData", result.getString("category"));
      assertEquals("Test summary", result.getString("summary"));
      ctx.completeNow();
    }).onFailure(ctx::failNow);
  }

  @Test
  void invoke_publishes_ticket_created_event(Vertx vertx, VertxTestContext ctx) {
    vertx.eventBus().consumer("events.ticket-test", msg -> {
      JsonObject body = (JsonObject) msg.body();
      assertEquals("TicketCreated", body.getString("type"));
      assertEquals("T-1", body.getString("tradeId"));
      assertEquals("corr-1", body.getString("correlationId"));
      assertEquals("case-1", body.getString("caseId"));
      assertTrue(body.getString("ticketId").startsWith("TICKET-"));
      ctx.completeNow();
    });

    var tool = new RaiseTicketTool(vertx, "events.ticket-test");
    tool.invoke(new JsonObject()
        .put("tradeId", "T-1")
        .put("category", "ReferenceData")
        .put("summary", "Test"), testCtx());
  }

  @Test
  void each_invocation_generates_unique_ticket_id(Vertx vertx, VertxTestContext ctx) {
    var tool = new RaiseTicketTool(vertx, "events.test");
    var args = new JsonObject().put("tradeId", "T-1").put("category", "A").put("summary", "S");

    tool.invoke(args, testCtx())
      .compose(first -> tool.invoke(args, testCtx())
        .map(second -> {
          assertNotEquals(first.getString("ticketId"), second.getString("ticketId"));
          return null;
        }))
      .onSuccess(v -> ctx.completeNow())
      .onFailure(ctx::failNow);
  }
}

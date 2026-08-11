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
class PublishEventToolTest {

  private AgentContext testCtx() {
    return new AgentContext("corr-1", "case-1", new JsonObject());
  }

  @Test
  void name_is_events_publish() {
    var tool = new PublishEventTool(Vertx.vertx(), "events.test");
    assertEquals("events.publish", tool.name());
  }

  @Test
  void description_is_not_empty() {
    var tool = new PublishEventTool(Vertx.vertx(), "events.test");
    assertFalse(tool.description().isBlank());
  }

  @Test
  void schema_declares_type_property_as_required() {
    var tool = new PublishEventTool(Vertx.vertx(), "events.test");
    var schema = tool.schema();
    assertEquals("object", schema.getString("type"));
    assertNotNull(schema.getJsonObject("properties").getJsonObject("type"));
    assertTrue(schema.getJsonArray("required").contains("type"));
  }

  @Test
  void invoke_returns_published_status(Vertx vertx, VertxTestContext ctx) {
    var tool = new PublishEventTool(vertx, "events.test");
    var args = new JsonObject().put("type", "TestEvent").put("data", "hello");

    tool.invoke(args, testCtx()).onSuccess(result -> {
      assertEquals("published", result.getString("status"));
      JsonObject event = result.getJsonObject("event");
      assertEquals("TestEvent", event.getString("type"));
      assertEquals("corr-1", event.getString("correlationId"));
      assertEquals("case-1", event.getString("caseId"));
      ctx.completeNow();
    }).onFailure(ctx::failNow);
  }

  @Test
  void invoke_publishes_to_event_bus(Vertx vertx, VertxTestContext ctx) {
    vertx.eventBus().consumer("events.pub-test", msg -> {
      JsonObject body = (JsonObject) msg.body();
      assertEquals("TestEvent", body.getString("type"));
      assertEquals("corr-1", body.getString("correlationId"));
      ctx.completeNow();
    });

    var tool = new PublishEventTool(vertx, "events.pub-test");
    tool.invoke(new JsonObject().put("type", "TestEvent"), testCtx());
  }

  @Test
  void args_are_copied_not_mutated(Vertx vertx, VertxTestContext ctx) {
    var tool = new PublishEventTool(vertx, "events.test");
    var args = new JsonObject().put("type", "Test");

    tool.invoke(args, testCtx()).onSuccess(result -> {
      // Original args should not have correlationId/caseId added
      assertNull(args.getString("correlationId"));
      ctx.completeNow();
    }).onFailure(ctx::failNow);
  }
}

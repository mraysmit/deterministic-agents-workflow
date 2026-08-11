package dev.mars.agent.config;

import dev.mars.agent.tool.ClassifyTool;
import dev.mars.agent.tool.LookupTool;
import dev.mars.agent.tool.NotifyTool;
import dev.mars.agent.tool.PublishEventTool;
import dev.mars.agent.tool.RaiseTicketTool;
import dev.mars.mcp.tool.Tool;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class ToolFactoryTest {

  @Test
  void creates_publish_event_tool(Vertx vertx) {
    Tool tool = ToolFactory.create("publish-event", vertx, "events.out");
    assertInstanceOf(PublishEventTool.class, tool);
    assertEquals("events.publish", tool.name());
  }

  @Test
  void creates_raise_ticket_tool(Vertx vertx) {
    Tool tool = ToolFactory.create("raise-ticket", vertx, "events.out");
    assertInstanceOf(RaiseTicketTool.class, tool);
    assertEquals("case.raiseTicket", tool.name());
  }

  @Test
  void creates_lookup_tool(Vertx vertx) {
    Tool tool = ToolFactory.create("lookup", vertx, "events.out");
    assertInstanceOf(LookupTool.class, tool);
    assertEquals("data.lookup", tool.name());
  }

  @Test
  void creates_classify_tool(Vertx vertx) {
    Tool tool = ToolFactory.create("classify", vertx, "events.out");
    assertInstanceOf(ClassifyTool.class, tool);
    assertEquals("case.classify", tool.name());
  }

  @Test
  void creates_notify_tool(Vertx vertx) {
    Tool tool = ToolFactory.create("notify", vertx, "events.out");
    assertInstanceOf(NotifyTool.class, tool);
    assertEquals("comms.notify", tool.name());
  }

  @Test
  void unknown_type_throws(Vertx vertx) {
    var ex = assertThrows(IllegalArgumentException.class,
        () -> ToolFactory.create("unknown-tool", vertx, "events.out"));
    assertTrue(ex.getMessage().contains("unknown-tool"));
  }
}

package dev.mars.mcp;

import dev.mars.agent.runner.AgentContext;
import dev.mars.agent.tool.AgentTool;
import dev.mars.mcp.tool.Tool;
import dev.mars.mcp.tool.ToolRegistry;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(VertxExtension.class)
class WorkflowMcpIntegrationTest {

  private static final String PROTOCOL = "2026-07-28";

  @Test
  void standalone_server_lists_and_invokes_an_agent_tool(Vertx vertx,
                                                          VertxTestContext ctx) {
    AgentTool agentTool = new AgentTool() {
      @Override
      public String name() {
        return "test.context";
      }

      @Override
      public JsonObject schema() {
        return new JsonObject()
            .put("type", "object")
            .put("properties", new JsonObject()
                .put("tradeId", new JsonObject().put("type", "string")))
            .put("required", new JsonArray().add("tradeId"))
            .put("additionalProperties", false);
      }

      @Override
      public Future<JsonObject> invoke(JsonObject arguments, AgentContext context) {
        return Future.succeededFuture(new JsonObject()
            .put("tradeId", arguments.getString("tradeId"))
            .put("caseId", context.caseId())
            .put("correlationId", context.correlationId()));
      }
    };

    Map<String, Tool> tools = ToolRegistry.of(agentTool);
    var server = new McpServerVerticle(tools, "tradeId");
    var options = new DeploymentOptions().setConfig(
        new JsonObject().put("mcp.port", 0));
    WebClient client = WebClient.create(vertx);

    vertx.deployVerticle(server, options)
        .compose(id -> post(client, server.actualPort(), "tools/list",
            request("tools/list", "list-1", new JsonObject())))
        .compose(listResponse -> {
          ctx.verify(() -> {
            assertEquals(200, listResponse.statusCode());
            JsonArray listed = listResponse.bodyAsJsonObject()
                .getJsonObject("result").getJsonArray("tools");
            assertEquals("test.context", listed.getJsonObject(0).getString("name"));
          });
          JsonObject params = new JsonObject()
              .put("name", "test.context")
              .put("arguments", new JsonObject().put("tradeId", "T-42"));
          return post(client, server.actualPort(), "tools/call",
              request("tools/call", "call-1", params))
              .map(response -> Map.entry(response, params));
        })
        .onSuccess(entry -> {
          ctx.verify(() -> {
            var response = entry.getKey();
            assertEquals(200, response.statusCode());
            JsonObject result = response.bodyAsJsonObject().getJsonObject("result");
            assertFalse(result.getBoolean("isError"));
            JsonObject structured = result.getJsonObject("structuredContent");
            assertEquals("T-42", structured.getString("tradeId"));
            assertEquals("T-42", structured.getString("caseId"));
          });
          ctx.completeNow();
        })
        .onFailure(ctx::failNow);
  }

  private static JsonObject request(String method, String id, JsonObject params) {
    JsonObject metadata = new JsonObject()
        .put("io.modelcontextprotocol/protocolVersion", PROTOCOL)
        .put("io.modelcontextprotocol/clientInfo",
            new JsonObject().put("name", "workflow-test").put("version", "1.0"))
        .put("io.modelcontextprotocol/clientCapabilities", new JsonObject());
    return new JsonObject()
        .put("jsonrpc", "2.0")
        .put("id", id)
        .put("method", method)
        .put("params", params.copy().put("_meta", metadata));
  }

  private static Future<io.vertx.ext.web.client.HttpResponse<io.vertx.core.buffer.Buffer>> post(
      WebClient client, int port, String method, JsonObject request) {
    var httpRequest = client.post(port, "127.0.0.1", "/mcp")
        .putHeader("Content-Type", "application/json")
        .putHeader("Accept", "application/json, text/event-stream")
        .putHeader("MCP-Protocol-Version", PROTOCOL)
        .putHeader("Mcp-Method", method);
    if ("tools/call".equals(method)) {
      httpRequest.putHeader("Mcp-Name", request.getJsonObject("params").getString("name"));
    }
    return httpRequest.sendJsonObject(request);
  }
}

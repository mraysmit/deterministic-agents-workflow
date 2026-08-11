package dev.mars.agent;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration-level smoke tests that deploy the full verticle stack and
 * exercise both the deterministic and agent processing paths via the
 * event bus.
 *
 * <p>Each test uses a random HTTP port ({@code http.port: 0}) so tests
 * can run in parallel without port-bind conflicts.
 */
@ExtendWith(VertxExtension.class)
public class SmokeTest {

  /** Deploy MainVerticle with a random HTTP port to avoid bind conflicts in parallel tests. */
  private DeploymentOptions testDeploymentOptions() {
    return new DeploymentOptions().setConfig(new JsonObject()
        .put("http.port", 0)
        .put("mcp.port", 0)
        .put("ui.port", 0)
        .put("workflow.port", 0));
  }

  @Test
  void deterministic_path_for_missing_isin(Vertx vertx, VertxTestContext ctx) {
    vertx.deployVerticle(new MainVerticle(), testDeploymentOptions())
      .compose(id -> vertx.eventBus().request("trade.failures",
        new JsonObject().put("tradeId", "T-1").put("reason", "Missing ISIN")))
      .onSuccess(reply -> {
        JsonObject body = (JsonObject) reply.body();
        assertEquals("ok", body.getString("status"));
        assertEquals("deterministic", body.getString("path"));
        ctx.completeNow();
      })
      .onFailure(ctx::failNow);
  }

  @Test
  void agent_path_for_unknown_reason(Vertx vertx, VertxTestContext ctx) {
    vertx.deployVerticle(new MainVerticle(), testDeploymentOptions())
      .compose(id -> vertx.eventBus().request("trade.failures",
        new JsonObject().put("tradeId", "T-2").put("reason", "LEI not found")))
      .onSuccess(reply -> {
        JsonObject body = (JsonObject) reply.body();
        assertEquals("ok", body.getString("status"));
        assertEquals("agent", body.getString("path"));
        JsonObject result = body.getJsonObject("result");
        assertNotNull(result);
        assertEquals("created", result.getString("status"));
        assertTrue(result.getString("ticketId").startsWith("TICKET-"));
        ctx.completeNow();
      })
      .onFailure(ctx::failNow);
  }
}

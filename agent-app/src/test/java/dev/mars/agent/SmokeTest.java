package dev.mars.agent;

import dev.mars.agent.config.PipelineConfigLoader;
import dev.mars.agent.memory.InMemoryMemoryStore;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration-level smoke tests that deploy the full verticle stack and
 * exercise the two processing paths via the event bus.
 *
 * <h2>Two tiers</h2>
 * <ul>
 *   <li><b>Deterministic path</b> — hermetic. Runs on every build. The
 *       LLM is never consulted, so no key and no network are needed.</li>
 *   <li><b>Agent path</b> — requires a real model, and is therefore gated
 *       on {@code OPENAI_API_KEY}. It skips silently when the variable is
 *       absent rather than being faked into passing.</li>
 * </ul>
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

  /**
   * The deterministic path resolves inside
   * {@link dev.mars.agent.processor.DeterministicFailureProcessorVerticle}
   * and never reaches the agent, so this asserts real behaviour with no
   * model involved. The test config supplies a literal API key purely so
   * the client can be constructed at startup; it is never called.
   */
  @Test
  void deterministic_path_for_missing_isin(Vertx vertx, VertxTestContext ctx) {
    var verticle = new MainVerticle(
        PipelineConfigLoader.load("smoke-test-pipeline.yaml"),
        new InMemoryMemoryStore());

    vertx.deployVerticle(verticle, testDeploymentOptions())
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

  /**
   * Exercises the agent path against a live model.
   *
   * <p>Assertions are deliberately <b>structural</b>, not textual: a real
   * model does not emit identical output run to run, so this checks that
   * the loop routed to the agent, terminated, and produced a result —
   * never that a specific tool was chosen or specific wording returned.
   * Asserting on exact choices here is what made the previous scripted
   * version of this test pass while validating nothing.
   */
  @Test
  @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+",
      disabledReason = "Requires a real LLM; set OPENAI_API_KEY to run")
  @Timeout(value = 120, timeUnit = TimeUnit.SECONDS)
  void agent_path_for_unknown_reason(Vertx vertx, VertxTestContext ctx) {
    vertx.deployVerticle(new MainVerticle(), testDeploymentOptions())
      .compose(id -> vertx.eventBus().request("trade.failures",
        new JsonObject().put("tradeId", "T-2").put("reason", "LEI not found")))
      .onSuccess(reply -> {
        JsonObject body = (JsonObject) reply.body();
        assertEquals("ok", body.getString("status"));
        assertEquals("agent", body.getString("path"));
        assertNotNull(body.getJsonObject("result"),
            "agent path must produce a tool result");
        ctx.completeNow();
      })
      .onFailure(ctx::failNow);
  }
}

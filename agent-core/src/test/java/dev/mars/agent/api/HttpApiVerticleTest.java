package dev.mars.agent.api;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HttpApiVerticle}.
 *
 * <p>The verticle is deployed on port 0 (random). The actual port is
 * discovered via Vert.x shared local map where the verticle stores it
 * after successful listen. A small helper in {@link #deployAndGetPort}
 * wires this up for HTTP-level assertions.
 */
@ExtendWith(VertxExtension.class)
class HttpApiVerticleTest {

  /** Unique address counter to avoid cross-test event bus collisions. */
  private static final AtomicInteger SEQ = new AtomicInteger();

  private DeploymentOptions portZero() {
    return new DeploymentOptions().setConfig(new JsonObject().put("http.port", 0));
  }

  // ── Constructor validation ────────────────────────────────────────

  @Test
  void constructor_rejects_required_fields_not_in_allowed() {
    assertThrows(IllegalArgumentException.class,
        () -> new HttpApiVerticle("/test", "addr",
            Set.of("a"), Set.of("a", "b")));
  }

  @Test
  void constructor_accepts_valid_config() {
    // Should not throw
    var verticle = new HttpApiVerticle("/test", "addr",
        Set.of("a", "b"), Set.of("a"));
    assertNotNull(verticle);
  }

  @Test
  void constructor_accepts_empty_required_fields() {
    var verticle = new HttpApiVerticle("/test", "addr",
        Set.of("a"), Set.of());
    assertNotNull(verticle);
  }

  // ── Deployment ────────────────────────────────────────────────────

  @Test
  void deploys_successfully_on_random_port(Vertx vertx, VertxTestContext ctx) {
    String addr = "test.deploy." + SEQ.incrementAndGet();
    vertx.eventBus().consumer(addr, msg -> msg.reply(new JsonObject()));

    var verticle = new HttpApiVerticle("/test", addr,
        Set.of("id"), Set.of("id"));

    vertx.deployVerticle(verticle, portZero())
        .onSuccess(id -> ctx.completeNow())
        .onFailure(ctx::failNow);
  }
}

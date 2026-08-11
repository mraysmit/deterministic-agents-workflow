package dev.mars.agent.llm;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class OpenAiLlmClientTest {

  @Test
  void constructor_accepts_tools(Vertx vertx) {
    // Should construct without error even with an empty tool list
    var client = new OpenAiLlmClient(vertx,
        "https://api.example.com/v1", "sk-test", "gpt-4o", List.of());
    assertNotNull(client);
  }

  @Test
  void decide_next_fails_on_unreachable_endpoint(Vertx vertx, VertxTestContext ctx) {
    var client = new OpenAiLlmClient(vertx,
        "http://localhost:19999", "sk-test", "gpt-4o", List.of());

    client.decideNext(
        new JsonObject().put("tradeId", "T-100").put("reason", "Missing ISIN"),
        new JsonObject().put("step", 0))
      .onSuccess(r -> ctx.failNow("Expected failure — endpoint is unreachable"))
      .onFailure(err -> {
        // Connection refused or similar network error
        assertNotNull(err.getMessage());
        ctx.completeNow();
      });
  }
}

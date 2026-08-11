package dev.mars.agent.config;

import dev.mars.agent.llm.LlmClient;
import dev.mars.agent.llm.OpenAiLlmClient;
import dev.mars.agent.llm.StubLlmClient;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class LlmClientFactoryTest {

  @Test
  void creates_stub_client(Vertx vertx) {
    LlmClient client = LlmClientFactory.create("stub", Map.of(), vertx);
    assertInstanceOf(StubLlmClient.class, client);
  }

  @Test
  void openai_requires_endpoint(Vertx vertx) {
    var ex = assertThrows(IllegalArgumentException.class,
        () -> LlmClientFactory.create("openai",
            Map.of("apiKey", "key", "model", "gpt-4"), vertx));
    assertTrue(ex.getMessage().contains("endpoint"));
  }

  @Test
  void openai_requires_api_key(Vertx vertx) {
    var ex = assertThrows(IllegalArgumentException.class,
        () -> LlmClientFactory.create("openai",
            Map.of("endpoint", "https://api.example.com", "model", "gpt-4"), vertx));
    assertTrue(ex.getMessage().contains("apiKey"));
  }

  @Test
  void openai_requires_model(Vertx vertx) {
    var ex = assertThrows(IllegalArgumentException.class,
        () -> LlmClientFactory.create("openai",
            Map.of("endpoint", "https://api.example.com", "apiKey", "key"), vertx));
    assertTrue(ex.getMessage().contains("model"));
  }

  @Test
  void openai_creates_client_with_literal_key(Vertx vertx) {
    // Use a literal apiKey (not ${...}) so env var resolution is skipped
    LlmClient client = LlmClientFactory.create("openai",
        Map.of("endpoint", "https://api.example.com",
               "apiKey", "sk-literal-key",
               "model", "gpt-4"), vertx);
    assertInstanceOf(OpenAiLlmClient.class, client);
  }

  @Test
  void unknown_type_throws(Vertx vertx) {
    var ex = assertThrows(IllegalArgumentException.class,
        () -> LlmClientFactory.create("unknown", Map.of(), vertx));
    assertTrue(ex.getMessage().contains("unknown"));
  }
}

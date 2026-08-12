package dev.mars.agent.config;

import java.util.Map;

/**
 * Configuration for the LLM client.
 *
 * @param type   the client alias; currently only {@code "openai"} is
 *               supported. There is deliberately no offline or rule-based
 *               alias — see {@code LlmClientFactory}.
 * @param params type-specific parameters (e.g.
 *               {@code {endpoint: "https://api.openai.com/v1",
 *               apiKey: "${OPENAI_API_KEY}", model: "gpt-4"}})
 */
public record LlmConfig(
    String type,
    Map<String, String> params
) {
  public LlmConfig {
    if (params == null) params = Map.of();
  }
}

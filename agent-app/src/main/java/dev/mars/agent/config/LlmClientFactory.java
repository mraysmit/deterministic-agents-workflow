package dev.mars.agent.config;

import dev.mars.agent.handler.TradeFailureRuleLoader;
import dev.mars.agent.llm.LlmClient;
import dev.mars.agent.llm.OpenAiLlmClient;
import dev.mars.mcp.tool.Tool;
import io.vertx.core.Vertx;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Factory that resolves LLM client type aliases from YAML configuration
 * into {@link LlmClient} instances.
 *
 * <h2>Supported types</h2>
 * <table>
 *   <tr><th>Alias</th><th>Description</th><th>Required params</th></tr>
 *   <tr>
 *     <td>{@code stub}</td>
 *     <td>Rule-based stub using {@link TradeFailureRuleLoader}.
 *         No network calls — suitable for local dev and testing.</td>
 *     <td><em>none</em></td>
 *   </tr>
 *   <tr>
 *     <td>{@code openai}</td>
 *     <td>Calls a real LLM via the OpenAI Chat Completions API
 *         using function-calling.</td>
 *     <td>{@code endpoint}, {@code apiKey}, {@code model}</td>
 *   </tr>
 * </table>
 *
 * <p>To add a new LLM backend, register it in the {@code switch}
 * expression in {@link #create}.
 */
public final class LlmClientFactory {

  private static final Logger LOG = Logger.getLogger(LlmClientFactory.class.getName());

  private LlmClientFactory() {}

  /**
   * Create an {@link LlmClient} from a type alias and parameters.
   *
   * @param type   the client alias (e.g. {@code "stub"}, {@code "openai"})
   * @param params type-specific parameters from YAML
   * @param vertx  the Vert.x instance (needed by some implementations)
   * @param tools  the resolved agent tools (needed for function-calling schemas)
   * @return a configured LLM client instance
   * @throws IllegalArgumentException if the type is unknown or required
   *         params are missing
   */
  public static LlmClient create(String type, Map<String, String> params,
                                  Vertx vertx, Collection<Tool> tools) {
    LOG.info("Creating LLM client: type=" + type);
    return switch (type) {
      case "stub" -> {
        LOG.info("Stub LLM client created with trade-failure rules");
        yield new TradeFailureRuleLoader().load().toClient();
      }

      case "openai" -> {
        String endpoint = requireParam(params, "endpoint", type);
        String apiKey   = resolveEnvVar(requireParam(params, "apiKey", type));
        String model    = requireParam(params, "model", type);
        LOG.info("OpenAI LLM client created: endpoint=" + endpoint + " model=" + model);
        yield new OpenAiLlmClient(vertx, endpoint, apiKey, model, tools);
      }

      default -> {
        LOG.severe("Unknown LLM type: " + type);
        throw new IllegalArgumentException("Unknown LLM type: " + type);
      }
    };
  }

  /**
   * Convenience overload for clients that don't need the tool list
   * (e.g. {@code "stub"}).
   */
  public static LlmClient create(String type, Map<String, String> params, Vertx vertx) {
    return create(type, params, vertx, Collections.emptyList());
  }

  private static String requireParam(Map<String, String> params, String key, String type) {
    String value = params.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(
          "LLM type '" + type + "' requires param '" + key + "'");
    }
    return value;
  }

  /**
   * If the value looks like {@code ${ENV_VAR}}, resolve it from the
   * environment. Otherwise return it as-is.
   */
  private static String resolveEnvVar(String value) {
    if (value.startsWith("${") && value.endsWith("}")) {
      String envName = value.substring(2, value.length() - 1);
      LOG.fine("Resolving environment variable: " + envName);
      String envValue = System.getenv(envName);
      if (envValue == null || envValue.isBlank()) {
        LOG.severe("Environment variable '" + envName + "' is not set");
        throw new IllegalStateException(
            "Environment variable '" + envName + "' is not set");
      }
      return envValue;
    }
    return value;
  }
}

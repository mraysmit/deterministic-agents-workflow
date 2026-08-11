package dev.mars.agent.config;

import dev.mars.agent.handler.EscalateHandler;
import dev.mars.agent.handler.LookupEnrichHandler;
import dev.mars.agent.processor.FailureHandler;
import io.vertx.core.Vertx;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Factory that resolves handler type aliases from YAML configuration into
 * {@link FailureHandler} instances.
 *
 * <h2>Supported types</h2>
 * <table>
 *   <tr><th>Alias</th><th>Class</th><th>Required params</th></tr>
 *   <tr>
 *     <td>{@code lookup-enrich}</td>
 *     <td>{@link LookupEnrichHandler}</td>
 *     <td>{@code identifier} — the identifier name (e.g. {@code "ISIN"})</td>
 *   </tr>
 *   <tr>
 *     <td>{@code escalate}</td>
 *     <td>{@link EscalateHandler}</td>
 *     <td><em>none</em></td>
 *   </tr>
 * </table>
 *
 * <p>To add a new handler type, register it in the {@code switch}
 * expression in {@link #create}.
 */
public final class HandlerFactory {

  private static final Logger LOG = Logger.getLogger(HandlerFactory.class.getName());

  private HandlerFactory() {}

  /**
   * Create a {@link FailureHandler} from a type alias and parameters.
   *
   * @param type          the handler alias (e.g. {@code "lookup-enrich"})
   * @param params        type-specific parameters from YAML
   * @param vertx         the Vert.x instance
   * @param eventsAddress the event bus address for outbound events
   * @return a configured handler instance
   * @throws IllegalArgumentException if the type is unknown or required
   *         params are missing
   */
  public static FailureHandler create(String type, Map<String, String> params,
                                      Vertx vertx, String eventsAddress) {
    LOG.info("Creating handler: type=" + type + " params=" + params);
    FailureHandler handler = switch (type) {
      case "lookup-enrich" -> {
        String identifier = params.get("identifier");
        if (identifier == null || identifier.isBlank()) {
          LOG.severe("Handler type 'lookup-enrich' missing required param 'identifier'");
          throw new IllegalArgumentException(
              "Handler type 'lookup-enrich' requires param 'identifier'");
        }
        LOG.info("Created LookupEnrichHandler for identifier='" + identifier + "'");
        yield new LookupEnrichHandler(vertx, eventsAddress, identifier);
      }
      case "escalate" -> {
        LOG.info("Created EscalateHandler");
        yield new EscalateHandler(vertx, eventsAddress);
      }

      default -> {
        LOG.severe("Unknown handler type: " + type);
        throw new IllegalArgumentException("Unknown handler type: " + type);
      }
    };
    return handler;
  }
}

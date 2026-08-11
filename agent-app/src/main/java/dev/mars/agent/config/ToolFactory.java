package dev.mars.agent.config;

import dev.mars.agent.tool.ClassifyTool;
import dev.mars.agent.tool.LookupTool;
import dev.mars.agent.tool.NotifyTool;
import dev.mars.agent.tool.PublishEventTool;
import dev.mars.agent.tool.RaiseTicketTool;
import dev.mars.mcp.tool.Tool;
import io.vertx.core.Vertx;

import java.util.logging.Logger;

/**
 * Factory that resolves tool type aliases from YAML configuration into
 * {@link Tool} instances.
 *
 * <h2>Supported types</h2>
 * <table>
 *   <tr><th>Alias</th><th>Class</th></tr>
 *   <tr>
 *     <td>{@code publish-event}</td>
 *     <td>{@link PublishEventTool}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code raise-ticket}</td>
 *     <td>{@link RaiseTicketTool}</td>
 *   </tr>
 * </table>
 *
 * <p>To add a new tool type, register it in the {@code switch}
 * expression in {@link #create}.
 */
public final class ToolFactory {

  private static final Logger LOG = Logger.getLogger(ToolFactory.class.getName());

  private ToolFactory() {}

  /**
   * Create a {@link Tool} from a type alias.
   *
   * @param type          the tool alias (e.g. {@code "publish-event"})
   * @param vertx         the Vert.x instance
   * @param eventsAddress the event bus address for outbound events
   * @return a configured tool instance
   * @throws IllegalArgumentException if the type is unknown
   */
  public static Tool create(String type, Vertx vertx, String eventsAddress) {
    LOG.info("Creating tool: type=" + type);
    Tool tool = switch (type) {
      case "publish-event" -> new PublishEventTool(vertx, eventsAddress);
      case "raise-ticket"  -> new RaiseTicketTool(vertx, eventsAddress);
      case "lookup"         -> new LookupTool();
      case "classify"       -> new ClassifyTool(vertx, eventsAddress);
      case "notify"         -> new NotifyTool(vertx, eventsAddress);

      default -> {
        LOG.severe("Unknown tool type: " + type);
        throw new IllegalArgumentException("Unknown tool type: " + type);
      }
    };
    LOG.info("Created tool: name=" + tool.name() + " type=" + type);
    return tool;
  }
}

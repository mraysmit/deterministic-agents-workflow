package dev.mars.agent.handler;

import dev.mars.agent.processor.FailureHandler;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.logging.Logger;

/**
 * {@link FailureHandler} that escalates a trade failure which is recognised but
 * cannot be automatically repaired — for example, an invalid counterparty.
 *
 * <h2>Side effects</h2>
 * Publishes a {@code TradeEscalated} event to the injected events address
 * containing the trade ID and the original failure reason.
 *
 * <h2>Example result</h2>
 * <pre>
 * {
 *   "type":    "TradeEscalated",
 *   "tradeId": "T-100",
 *   "by":      "deterministic-processor",
 *   "reason":  "Invalid Counterparty"
 * }
 * </pre>
 *
 * @see FailureHandler
 */
public class EscalateHandler implements FailureHandler {

  private static final Logger LOG = Logger.getLogger(EscalateHandler.class.getName());

  private final Vertx vertx;
  private final String eventsAddress;

  /**
   * @param vertx         the Vert.x instance for event bus access
   * @param eventsAddress the event bus address to publish escalation events to
   */
  public EscalateHandler(Vertx vertx, String eventsAddress) {
    this.vertx = vertx;
    this.eventsAddress = eventsAddress;
  }

  @Override
  public Future<JsonObject> handle(JsonObject event) {
    String tradeId = event.getString("tradeId");
    String reason = event.getString("reason");

    LOG.info("Escalating trade=" + tradeId + " reason='" + reason + "'");

    JsonObject escalated = new JsonObject()
      .put("type", "TradeEscalated")
      .put("tradeId", tradeId)
      .put("by", "deterministic-processor")
      .put("reason", reason);

    vertx.eventBus().publish(eventsAddress, escalated);

    LOG.info("Published TradeEscalated event for trade=" + tradeId);
    return Future.succeededFuture(escalated);
  }
}

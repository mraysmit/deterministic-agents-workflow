package dev.mars.agent.handler;

import dev.mars.agent.processor.FailureHandler;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.logging.Logger;

/**
 * {@link FailureHandler} that repairs a trade by looking up and enriching a
 * missing identifier.
 *
 * <p>The identifier name (e.g. {@code "ISIN"}, {@code "CUSIP"}) is supplied at
 * construction time, making this handler reusable for different lookup+enrich
 * scenarios without code changes.
 *
 * <h2>Side effects</h2>
 * Publishes a {@code TradeRepaired} event to the injected events address
 * with the repair action detail.
 *
 * <h2>Example result</h2>
 * <pre>
 * {
 *   "type":    "TradeRepaired",
 *   "tradeId": "T-100",
 *   "by":      "deterministic-processor",
 *   "details": { "action": "lookup+enrich ISIN" }
 * }
 * </pre>
 *
 * @see FailureHandler
 */
public class LookupEnrichHandler implements FailureHandler {

  private static final Logger LOG = Logger.getLogger(LookupEnrichHandler.class.getName());

  private final Vertx vertx;
  private final String eventsAddress;
  private final String identifierName;

  /**
   * @param vertx         the Vert.x instance for event bus access
   * @param eventsAddress the event bus address to publish repair events to
   * @param identifierName the identifier type being looked up (e.g. {@code "ISIN"})
   */
  public LookupEnrichHandler(Vertx vertx, String eventsAddress, String identifierName) {
    this.vertx = vertx;
    this.eventsAddress = eventsAddress;
    this.identifierName = identifierName;
  }

  @Override
  public Future<JsonObject> handle(JsonObject event) {
    String tradeId = event.getString("tradeId");

    LOG.info("Lookup+enrich " + identifierName + " for trade=" + tradeId);

    JsonObject repaired = new JsonObject()
      .put("type", "TradeRepaired")
      .put("tradeId", tradeId)
      .put("by", "deterministic-processor")
      .put("details", new JsonObject().put("action", "lookup+enrich " + identifierName));

    vertx.eventBus().publish(eventsAddress, repaired);

    LOG.info("Published TradeRepaired event for trade=" + tradeId + " (" + identifierName + ")");
    return Future.succeededFuture(repaired);
  }
}

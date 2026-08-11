package dev.mars.agent.event;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

import java.util.logging.Logger;

/**
 * Simple consumer verticle that subscribes to an injected event bus address
 * and logs every domain event it receives.
 *
 * <p>In a production system this would be replaced (or extended) with a
 * persistent sink — e.g. writing events to Kafka, a database, or a
 * monitoring platform. The current implementation logs to
 * {@code java.util.logging} for demonstration purposes.
 *
 * <p>Because the address uses <em>publish/subscribe</em> semantics, multiple
 * sinks can coexist on the same address without interfering with each other.
 */
public class EventSinkVerticle extends AbstractVerticle {

  private static final Logger LOG = Logger.getLogger(EventSinkVerticle.class.getName());

  private final String eventsAddress;

  /**
   * @param eventsAddress the event bus address to subscribe to for domain events
   */
  public EventSinkVerticle(String eventsAddress) {
    this.eventsAddress = eventsAddress;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    LOG.info("EventSinkVerticle subscribing to: " + eventsAddress);
    vertx.eventBus().consumer(eventsAddress, msg -> {
      JsonObject event = (JsonObject) msg.body();
      LOG.info("[EVENTS_OUT] " + event.encode());
    });
    startPromise.complete();
  }
}

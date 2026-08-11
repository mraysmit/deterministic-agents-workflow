package dev.mars.agent.api;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Generic HTTP ingress verticle that exposes a REST API for submitting
 * event payloads and a health-check endpoint.
 *
 * <p>All domain-specific details — route path, accepted fields, mandatory
 * fields — are injected via the constructor, making this class reusable
 * across different event schemas (trade failures, settlement failures,
 * margin calls, etc.).
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET /health} — returns {@code {"status":"UP"}}.</li>
 *   <li>{@code POST <routePath>} — accepts a JSON body, validates that
 *       all {@code requiredFields} are present, sanitises the payload to
 *       the {@code allowedFields} whitelist, then dispatches it over the
 *       event bus to the injected {@code targetAddress} using
 *       request/reply.</li>
 * </ul>
 *
 * <h2>Configuration (Vert.x config)</h2>
 * <ul>
 *   <li>{@code http.port} — TCP port to listen on (default {@code 8080},
 *       use {@code 0} for a random port in tests).</li>
 *   <li>{@code request.timeout.ms} — event-bus request timeout in
 *       milliseconds (default {@code 10 000}).</li>
 * </ul>
 *
 * <h2>Input sanitisation</h2>
 * Only fields listed in the {@code allowedFields} set are forwarded
 * downstream. This prevents unexpected or malicious data from reaching
 * the processor or LLM agent.
 *
 */
public class HttpApiVerticle extends AbstractVerticle {

  private static final Logger LOG = Logger.getLogger(HttpApiVerticle.class.getName());
  private static final long DEFAULT_REQUEST_TIMEOUT_MS = 10_000;

  private final String routePath;
  private final String targetAddress;
  private final Set<String> allowedFields;
  private final Set<String> requiredFields;

  /**
   * Creates a new HTTP API verticle with the given routing and validation
   * parameters.
   *
   * @param routePath      the POST route path (e.g. {@code "/trade/failures"})
   * @param targetAddress  the event bus address to dispatch sanitised
   *                       payloads to
   * @param allowedFields  field names that are forwarded downstream after
   *                       sanitisation; must be a superset of
   *                       {@code requiredFields}
   * @param requiredFields field names that must be present in the incoming
   *                       JSON body — the request is rejected with 400 if
   *                       any are missing
   * @throws IllegalArgumentException if {@code requiredFields} contains
   *         names not present in {@code allowedFields}
   */
  public HttpApiVerticle(String routePath,
                         String targetAddress,
                         Set<String> allowedFields,
                         Set<String> requiredFields) {
    this.routePath = routePath;
    this.targetAddress = targetAddress;
    this.allowedFields = Set.copyOf(allowedFields);
    this.requiredFields = Set.copyOf(requiredFields);

    // Fail fast if required fields are not a subset of allowed fields
    Set<String> notAllowed = requiredFields.stream()
      .filter(f -> !allowedFields.contains(f))
      .collect(Collectors.toSet());
    if (!notAllowed.isEmpty()) {
      throw new IllegalArgumentException(
        "Required fields not in allowedFields: " + notAllowed);
    }
  }

  @Override
  public void start(Promise<Void> startPromise) {
    long timeoutMs = config().getLong("request.timeout.ms", DEFAULT_REQUEST_TIMEOUT_MS);

    LOG.info("HttpApiVerticle starting: route=" + routePath
        + " target=" + targetAddress
        + " timeout=" + timeoutMs + "ms"
        + " allowedFields=" + allowedFields
        + " requiredFields=" + requiredFields);

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    router.get("/health").handler(ctx -> ctx.response()
      .putHeader("content-type", "application/json")
      .end(new JsonObject().put("status", "UP").encode()));

    router.post(routePath).handler(ctx -> {
      JsonObject event = ctx.body().asJsonObject();
      if (event == null) {
        LOG.warning("Rejected request on " + routePath + ": null JSON body");
        ctx.response().setStatusCode(400)
          .putHeader("content-type", "application/json")
          .end(new JsonObject().put("error", "Expected JSON body").encode());
        return;
      }

      LOG.info("Received POST " + routePath + ": " + event.encode());

      // Check all required fields are present
      String missing = requiredFields.stream()
        .filter(f -> event.getString(f) == null)
        .collect(Collectors.joining(", "));
      if (!missing.isEmpty()) {
        LOG.warning("Rejected request on " + routePath + ": missing fields=[" + missing + "]");
        ctx.response().setStatusCode(400)
          .putHeader("content-type", "application/json")
          .end(new JsonObject()
            .put("error", "Missing required field(s): " + missing).encode());
        return;
      }

      // Whitelist known fields to prevent unexpected data reaching the agent/LLM
      JsonObject sanitized = new JsonObject();
      for (String field : allowedFields) {
        if (event.containsKey(field)) {
          sanitized.put(field, event.getValue(field));
        }
      }

      LOG.fine("Dispatching sanitised payload to " + targetAddress + ": " + sanitized.encode());

      DeliveryOptions opts = new DeliveryOptions().setSendTimeout(timeoutMs);
      vertx.eventBus().request(targetAddress, sanitized, opts)
        .onSuccess(reply -> {
          LOG.info("Request on " + routePath + " succeeded — returning 200");
          ctx.response()
            .putHeader("content-type", "application/json")
            .end(((JsonObject) reply.body()).encodePrettily());
        }).onFailure(err -> {
          LOG.warning("Request failed on " + routePath + ": " + err.getMessage());
          ctx.response().setStatusCode(500)
            .putHeader("content-type", "application/json")
            .end(new JsonObject()
              .put("error", err.getMessage()).encode());
        });
    });

    int port = config().getInteger("http.port", 8080);
    vertx.createHttpServer()
      .requestHandler(router)
      .listen(port)
      .onSuccess(server -> {
        LOG.info("HTTP server started on port " + server.actualPort());
        startPromise.complete();
      })
      .onFailure(startPromise::fail);
  }
}

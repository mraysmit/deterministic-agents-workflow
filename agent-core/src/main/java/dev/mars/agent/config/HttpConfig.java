package dev.mars.agent.config;

/**
 * HTTP ingress configuration.
 *
 * @param port             TCP port to listen on (0 for random in tests)
 * @param route            the POST route path (e.g. {@code "/trade/failures"})
 * @param requestTimeoutMs event-bus request timeout in milliseconds
 */
public record HttpConfig(
    int port,
    String route,
    long requestTimeoutMs
) {
  /** Defaults: port 8080, 10-second timeout. */
  public HttpConfig {
    if (port < 0) throw new IllegalArgumentException("port must be >= 0");
    if (route == null || route.isBlank()) throw new IllegalArgumentException("route must not be blank");
  }
}

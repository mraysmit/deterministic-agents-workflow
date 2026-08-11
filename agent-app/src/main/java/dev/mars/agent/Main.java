package dev.mars.agent;

import io.vertx.core.Vertx;

/**
 * Application entry point. Creates a Vert.x instance, deploys
 * {@link MainVerticle}, and blocks on shutdown.
 *
 * <p>In Vert.x 5 the legacy {@code io.vertx.core.Launcher} was removed,
 * so this class provides a plain {@code main()} that does the same job.
 */
public final class Main {

  private Main() {}

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new MainVerticle())
      .onSuccess(id -> { /* banner is printed by MainVerticle */ })
      .onFailure(err -> {
        System.err.println("Failed to deploy MainVerticle: " + err.getMessage());
        err.printStackTrace();
        vertx.close();
      });
  }
}

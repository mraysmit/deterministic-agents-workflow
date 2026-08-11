package dev.mars.agent.config;

/**
 * MCP (Model Context Protocol) server configuration.
 *
 * <p>When {@code enabled} is {@code true}, the pipeline deploys an
 * {@link dev.mars.mcp.McpServerVerticle McpServerVerticle}
 * that exposes the agent's tools via the MCP protocol (HTTP + SSE
 * transport).
 *
 * <h2>Example YAML</h2>
 * <pre>
 * mcp:
 *   enabled: true
 *   port: 3001
 *   basePath: ""
 * </pre>
 *
 * @param enabled  whether the MCP server should be started
 * @param port     TCP port for the MCP HTTP server (0 for random in tests)
 * @param basePath URL path prefix for MCP endpoints (e.g. {@code "/mcp"});
 *                 defaults to empty string (endpoints at root)
 */
public record McpConfig(
    boolean enabled,
    int port,
    String basePath
) {
  public McpConfig {
    if (port < 0) throw new IllegalArgumentException("port must be >= 0");
    if (basePath == null) basePath = "";
  }
}

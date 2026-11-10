package com.javadiag.mcp.http;

import com.javadiag.mcp.config.DiagConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Human-readable landing page describing how to connect.
 */
public final class RootInfoServlet extends HttpServlet {

    private final DiagConfig config;

    public RootInfoServlet(DiagConfig config) {
        this.config = config;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("text/plain; charset=utf-8");
        String authHint = "Authorization: Bearer <auth.token / JAVA_DIAG_AUTH_TOKEN>";
        String body = """
                java-diag MCP server %s
                instanceId=%s

                Connect Agent via IP:port using MCP HTTP transports:
                  SSE endpoint:        http://<host>:%d/sse
                  SSE message POST:    http://<host>:%d/mcp/message
                  Streamable HTTP:     http://<host>:%d/mcp
                  Health:              http://<host>:%d/health

                Auth: %s
                """.formatted(
                config.version(),
                config.instanceId(),
                config.httpPort(),
                config.httpPort(),
                config.httpPort(),
                config.httpPort(),
                authHint);
        resp.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
}

package com.javadiag.mcp.http;

import com.javadiag.mcp.config.DiagConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Liveness endpoint for cloud deploy / load balancers.
 */
public final class HealthServlet extends HttpServlet {

    private final DiagConfig config;

    public HealthServlet(DiagConfig config) {
        this.config = config;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("application/json; charset=utf-8");
        String body = "{"
                + "\"ok\":true,"
                + "\"service\":\"java-diag\","
                + "\"version\":\"" + config.version() + "\","
                + "\"instanceId\":\"" + config.instanceId() + "\","
                + "\"transport\":\"http\","
                + "\"endpoints\":{"
                + "\"sse\":\"/sse\","
                + "\"message\":\"/mcp/message\","
                + "\"streamable\":\"/mcp\""
                + "}"
                + "}";
        resp.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
}

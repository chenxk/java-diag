package com.javadiag.mcp.http;

import com.javadiag.mcp.config.DiagConfig;
import com.javadiag.mcp.mcp.ToolRegistrar;
import com.javadiag.mcp.platform.JsonCodec;
import com.javadiag.mcp.tools.ToolSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpMcpServerBootstrapTest {

    @TempDir
    Path tempDir;

    private HttpMcpServerBootstrap bootstrap;

    @AfterEach
    void tearDown() {
        if (bootstrap != null) {
            bootstrap.stop();
        }
    }

    @Test
    void healthAndAuthWorkOverHttp() throws Exception {
        int port = findFreePort();
        DiagConfig config = DiagConfig.fromEnvironment(Map.of(
                DiagConfig.ENV_TRANSPORT, "http",
                DiagConfig.ENV_HTTP_HOST, "127.0.0.1",
                DiagConfig.ENV_HTTP_PORT, Integer.toString(port),
                DiagConfig.ENV_AUTH_TOKEN, "secret-token",
                DiagConfig.ENV_REGISTRY_REGISTER_TOKEN, "reg-secret",
                DiagConfig.ENV_ALLOWED_LOG_DIRS, tempDir.toString(),
                DiagConfig.ENV_AUDIT_PATH, tempDir.resolve("audit.log").toString()));

        JsonCodec codec = JsonCodec.createDefault();
        ToolRegistrar registrar = new ToolRegistrar(ToolSupport.create(config, codec));
        bootstrap = new HttpMcpServerBootstrap(config, registrar, codec);
        bootstrap.start();

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        String base = "http://127.0.0.1:" + port;

        HttpResponse<String> health = client.send(
                HttpRequest.newBuilder(URI.create(base + "/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, health.statusCode());
        assertTrue(health.body().contains("\"ok\":true"));
        assertTrue(health.body().contains("/sse"));

        HttpResponse<String> denied = client.send(
                HttpRequest.newBuilder(URI.create(base + "/sse")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, denied.statusCode());

        // SSE keeps the body open; only assert response headers/status.
        HttpURLConnection conn = (HttpURLConnection) URI.create(base + "/sse").toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer secret-token");
        conn.setConnectTimeout(3_000);
        conn.setReadTimeout(2_000);
        assertEquals(200, conn.getResponseCode());
        conn.disconnect();
    }

    private static int findFreePort() throws Exception {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}

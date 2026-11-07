package com.javadiag.mcp.cluster;

import com.javadiag.mcp.config.DiagConfig;
import com.javadiag.mcp.platform.JsonCodec;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional client that registers this probe with a java-diag-gateway registry and heartbeats.
 */
public final class RegistryHeartbeatClient implements AutoCloseable {

    private final DiagConfig config;
    private final JsonCodec codec;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "java-diag-registry-heartbeat");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean started = new AtomicBoolean(false);

    public RegistryHeartbeatClient(DiagConfig config, JsonCodec codec) {
        this.config = config;
        this.codec = codec;
    }

    public void start() {
        if (config.registerUrl().isEmpty()) {
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        registerOnce();
        long interval = Math.max(5, config.heartbeatIntervalSeconds());
        scheduler.scheduleAtFixedRate(this::heartbeatSafe, interval, interval, TimeUnit.SECONDS);
        System.err.println("registry heartbeat enabled -> " + config.registerUrl().orElseThrow()
                + " interval=" + interval + "s instanceId=" + config.instanceId());
    }

    public void stop() {
        if (!started.get()) {
            return;
        }
        try {
            deregisterOnce();
        } catch (Exception ex) {
            System.err.println("registry deregister failed: " + ex.getMessage());
        }
        scheduler.shutdownNow();
    }

    @Override
    public void close() {
        stop();
    }

    private void registerOnce() {
        Map<String, Object> body = buildPayload();
        post("/register", body, true);
    }

    private void heartbeatSafe() {
        try {
            Map<String, Object> body = Map.of("instanceId", config.instanceId());
            int code = post("/heartbeat", body, false);
            if (code == 404) {
                registerOnce();
            }
        } catch (Exception ex) {
            System.err.println("registry heartbeat error: " + ex.getMessage());
        }
    }

    private void deregisterOnce() {
        post("/deregister", Map.of("instanceId", config.instanceId()), false);
    }

    private Map<String, Object> buildPayload() {
        String base = config.advertiseUrl()
                .orElseGet(() -> "http://" + advertiseHost() + ":" + config.httpPort());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("instanceId", config.instanceId());
        body.put("serviceName", config.serviceName());
        body.put("host", advertiseHost());
        body.put("port", config.httpPort());
        body.put("mcpBaseUrl", trimSlash(base));
        body.put("ssePath", "/sse");
        body.put("mcpPath", "/mcp");
        body.put("probeToken", config.authToken().orElse(null));
        body.put("tags", config.tags());
        body.put("version", config.version());
        return body;
    }

    private int post(String path, Map<String, Object> body, boolean throwOnError) {
        String registerBase = trimSlash(config.registerUrl().orElseThrow());
        String url = registerBase.endsWith("/registry")
                ? registerBase + path
                : registerBase + "/registry" + path;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(codec.toJson(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                String msg = "registry " + path + " -> HTTP " + response.statusCode() + " " + response.body();
                if (throwOnError) {
                    throw new IllegalStateException(msg);
                }
                System.err.println(msg);
            } else if (throwOnError) {
                System.err.println("registered with gateway registry: " + config.instanceId());
            }
            return response.statusCode();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (throwOnError) {
                throw new IllegalStateException("registry call interrupted", ex);
            }
            return -1;
        } catch (Exception ex) {
            if (throwOnError) {
                throw new IllegalStateException("registry call failed: " + ex.getMessage(), ex);
            }
            System.err.println("registry call failed: " + ex.getMessage());
            return -1;
        }
    }

    private String advertiseHost() {
        if (config.advertiseUrl().isPresent()) {
            try {
                return URI.create(config.advertiseUrl().get()).getHost();
            } catch (Exception ignored) {
                // fall through
            }
        }
        if (!"0.0.0.0".equals(config.httpHost()) && !config.httpHost().isBlank()) {
            return config.httpHost();
        }
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ex) {
            return "127.0.0.1";
        }
    }

    private static String trimSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}

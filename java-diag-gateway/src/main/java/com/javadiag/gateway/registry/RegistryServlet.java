package com.javadiag.gateway.registry;

import com.javadiag.gateway.json.JsonSupport;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Control-plane HTTP API: /registry/register|heartbeat|deregister|instances.
 * Requires non-empty register-token ({@code JAVA_DIAG_REGISTRY_REGISTER_TOKEN} / {@code register.token}).
 */
public final class RegistryServlet extends HttpServlet {

    private final InMemoryRegistry registry;
    private final JsonSupport json;
    private final Optional<String> registerToken;

    public RegistryServlet(InMemoryRegistry registry, JsonSupport json, Optional<String> registerToken) {
        this.registry = registry;
        this.json = json;
        this.registerToken = registerToken == null ? Optional.empty() : registerToken;
    }

    public RegistryServlet(InMemoryRegistry registry, JsonSupport json) {
        this(registry, json, Optional.empty());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!authorizeRegister(req, resp)) {
            return;
        }
        String path = normalize(req.getPathInfo());
        String body = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> payload = body.isBlank() ? Map.of() : json.toMap(body);

        switch (path) {
            case "/register" -> handleRegister(payload, resp);
            case "/heartbeat" -> handleHeartbeat(payload, resp);
            case "/deregister" -> handleDeregister(payload, resp);
            default -> writeError(resp, 404, "unknown registry path: " + path);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!authorizeRegister(req, resp)) {
            return;
        }
        String path = normalize(req.getPathInfo());
        if (!"/deregister".equals(path)) {
            writeError(resp, 404, "unknown registry path: " + path);
            return;
        }
        String instanceId = req.getParameter("instanceId");
        if (instanceId == null || instanceId.isBlank()) {
            String body = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!body.isBlank()) {
                Object id = json.toMap(body).get("instanceId");
                instanceId = id == null ? null : id.toString();
            }
        }
        boolean removed = instanceId != null && registry.deregister(instanceId);
        writeJson(resp, 200, Map.of("ok", removed, "instanceId", instanceId == null ? "" : instanceId));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = normalize(req.getPathInfo());
        if (!"/instances".equals(path) && !"".equals(path) && !"/".equals(path)) {
            writeError(resp, 404, "unknown registry path: " + path);
            return;
        }
        String service = req.getParameter("serviceName");
        String app = req.getParameter("app");
        List<ProbeInstance> list = registry.list(service, app, Map.of(), false);
        writeJson(resp, 200, Map.of(
                "ok", true,
                "count", list.size(),
                "instances", list.stream().map(ProbeInstance::toPublicMap).collect(Collectors.toList())));
    }

    private void handleRegister(Map<String, Object> payload, HttpServletResponse resp) throws IOException {
        String mcpBaseUrl = str(payload.get("mcpBaseUrl"));
        if (mcpBaseUrl == null) {
            writeError(resp, 400, "mcpBaseUrl is required");
            return;
        }
        String scheme = mcpBaseUrl.toLowerCase(java.util.Locale.ROOT);
        if (!scheme.startsWith("http://") && !scheme.startsWith("https://")) {
            writeError(resp, 400, "mcpBaseUrl must be http:// or https://");
            return;
        }
        // Prefer client-supplied instanceId; else derive host:port from advertise mcpBaseUrl.
        String instanceId = str(payload.get("instanceId"));
        if (instanceId == null) {
            instanceId = instanceIdFromMcpBaseUrl(mcpBaseUrl);
            if (instanceId == null) {
                writeError(resp, 400, "instanceId missing and could not be derived from mcpBaseUrl");
                return;
            }
        }
        String serviceName = str(payload.get("serviceName"));
        String host = str(payload.get("host"));
        int port = payload.get("port") instanceof Number n ? n.intValue() : 0;
        if (host == null || port <= 0) {
            HostPort derived = hostPortFromMcpBaseUrl(mcpBaseUrl);
            if (host == null) {
                host = derived == null ? "" : derived.host();
            }
            if (port <= 0) {
                port = derived == null ? 0 : derived.port();
            }
        }
        String ssePath = str(payload.get("ssePath"));
        String mcpPath = str(payload.get("mcpPath"));
        String probeToken = str(payload.get("probeToken"));
        if (probeToken == null) {
            probeToken = str(payload.get("authToken"));
        }
        String version = str(payload.get("version"));
        @SuppressWarnings("unchecked")
        Map<String, String> tags = castTags(payload.get("tags"));
        List<ProbeJvmApp> apps = ProbeJvmApp.parseList(
                payload.get("apps") != null ? payload.get("apps") : payload.get("jvms"), 50);

        ProbeInstance instance = new ProbeInstance(
                instanceId,
                serviceName,
                host,
                port,
                mcpBaseUrl,
                ssePath,
                mcpPath,
                probeToken,
                tags,
                apps,
                version,
                Instant.now(),
                ProbeStatus.HEALTHY);
        registry.register(instance);
        writeJson(resp, 200, Map.of("ok", true, "instanceId", instanceId));
    }

    static String instanceIdFromMcpBaseUrl(String mcpBaseUrl) {
        HostPort hp = hostPortFromMcpBaseUrl(mcpBaseUrl);
        return hp == null ? null : hp.host() + ":" + hp.port();
    }

    private record HostPort(String host, int port) {
    }

    static HostPort hostPortFromMcpBaseUrl(String mcpBaseUrl) {
        try {
            URI uri = URI.create(mcpBaseUrl);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            int port = uri.getPort();
            if (port < 0) {
                port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            }
            return new HostPort(host, port);
        } catch (Exception ex) {
            return null;
        }
    }

    private void handleHeartbeat(Map<String, Object> payload, HttpServletResponse resp) throws IOException {
        String instanceId = str(payload.get("instanceId"));
        if (instanceId == null) {
            writeError(resp, 400, "instanceId is required");
            return;
        }
        String probeToken = str(payload.get("probeToken"));
        if (probeToken == null) {
            probeToken = str(payload.get("authToken"));
        }
        List<ProbeJvmApp> apps = null;
        if (payload.containsKey("apps") || payload.containsKey("jvms")) {
            apps = ProbeJvmApp.parseList(
                    payload.get("apps") != null ? payload.get("apps") : payload.get("jvms"), 50);
        }
        boolean ok = registry.heartbeat(instanceId, probeToken, apps);
        if (!ok) {
            writeError(resp, 404, "instance not registered: " + instanceId);
            return;
        }
        writeJson(resp, 200, Map.of("ok", true, "instanceId", instanceId));
    }

    private void handleDeregister(Map<String, Object> payload, HttpServletResponse resp) throws IOException {
        String instanceId = str(payload.get("instanceId"));
        if (instanceId == null) {
            writeError(resp, 400, "instanceId is required");
            return;
        }
        boolean removed = registry.deregister(instanceId);
        writeJson(resp, 200, Map.of("ok", removed, "instanceId", instanceId));
    }

    private static String normalize(String pathInfo) {
        if (pathInfo == null || pathInfo.isBlank()) {
            return "/";
        }
        return pathInfo;
    }

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString();
        return s.isBlank() ? null : s;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> castTags(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                out.put(e.getKey().toString(), e.getValue().toString());
            }
        }
        return out;
    }

    private void writeJson(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json; charset=utf-8");
        resp.getOutputStream().write(json.toJson(body).getBytes(StandardCharsets.UTF_8));
    }

    private void writeError(HttpServletResponse resp, int status, String message) throws IOException {
        writeJson(resp, status, Map.of("ok", false, "error", message));
    }

    boolean authorizeRegister(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (registerToken.isEmpty() || registerToken.get().isBlank()) {
            writeJson(resp, 401, Map.of("ok", false, "error", "registry register token is not configured"));
            return false;
        }
        String expected = registerToken.get();
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            if (expected.equals(auth.substring("Bearer ".length()).trim())) {
                return true;
            }
        }
        String header = req.getHeader("X-Registry-Token");
        if (expected.equals(header)) {
            return true;
        }
        writeJson(resp, 401, Map.of("ok", false, "error", "registry register token required"));
        return false;
    }
}

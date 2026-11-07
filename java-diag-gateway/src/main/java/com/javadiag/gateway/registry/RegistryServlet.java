package com.javadiag.gateway.registry;

import com.javadiag.gateway.json.JsonSupport;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Control-plane HTTP API: /registry/register|heartbeat|deregister|instances
 */
public final class RegistryServlet extends HttpServlet {

    private final InMemoryRegistry registry;
    private final String registerToken;
    private final JsonSupport json;

    public RegistryServlet(InMemoryRegistry registry, String registerToken, JsonSupport json) {
        this.registry = registry;
        this.registerToken = registerToken;
        this.json = json;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!authorize(req, resp)) {
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
        if (!authorize(req, resp)) {
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
        if (!authorize(req, resp)) {
            return;
        }
        String path = normalize(req.getPathInfo());
        if (!"/instances".equals(path) && !"".equals(path) && !"/".equals(path)) {
            writeError(resp, 404, "unknown registry path: " + path);
            return;
        }
        String service = req.getParameter("serviceName");
        List<ProbeInstance> list = registry.list(service, Map.of(), false);
        writeJson(resp, 200, Map.of(
                "ok", true,
                "count", list.size(),
                "instances", list.stream().map(ProbeInstance::toPublicMap).collect(Collectors.toList())));
    }

    private void handleRegister(Map<String, Object> payload, HttpServletResponse resp) throws IOException {
        String instanceId = str(payload.get("instanceId"));
        String mcpBaseUrl = str(payload.get("mcpBaseUrl"));
        if (instanceId == null || mcpBaseUrl == null) {
            writeError(resp, 400, "instanceId and mcpBaseUrl are required");
            return;
        }
        String serviceName = str(payload.get("serviceName"));
        String host = str(payload.get("host"));
        int port = payload.get("port") instanceof Number n ? n.intValue() : 0;
        String ssePath = str(payload.get("ssePath"));
        String mcpPath = str(payload.get("mcpPath"));
        String probeToken = str(payload.get("probeToken"));
        String version = str(payload.get("version"));
        @SuppressWarnings("unchecked")
        Map<String, String> tags = castTags(payload.get("tags"));

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
                version,
                Instant.now(),
                ProbeStatus.HEALTHY);
        registry.register(instance);
        writeJson(resp, 200, Map.of("ok", true, "instanceId", instanceId));
    }

    private void handleHeartbeat(Map<String, Object> payload, HttpServletResponse resp) throws IOException {
        String instanceId = str(payload.get("instanceId"));
        if (instanceId == null) {
            writeError(resp, 400, "instanceId is required");
            return;
        }
        boolean ok = registry.heartbeat(instanceId);
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

    private boolean authorize(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String provided = null;
        String header = req.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            provided = header.substring(7).trim();
        }
        if (provided == null) {
            provided = req.getHeader("X-Java-Diag-Register-Token");
        }
        if (provided == null || !provided.equals(registerToken)) {
            writeError(resp, 401, "missing or invalid register token");
            return false;
        }
        return true;
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
}

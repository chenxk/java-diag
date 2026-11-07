package com.javadiag.gateway.admin;

import com.javadiag.gateway.config.GatewayConfig;
import com.javadiag.gateway.json.JsonSupport;
import com.javadiag.gateway.registry.InMemoryRegistry;
import com.javadiag.gateway.registry.ProbeInstance;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin console JSON API: {@code /admin/api/login|logout|session|instances}.
 * Uses HttpSession cookie (separate from agent Bearer and registry register-token).
 */
public final class AdminApiServlet extends HttpServlet {

    private static final int SESSION_MAX_INACTIVE_SECONDS = 8 * 60 * 60;
    static final String MCP_SERVER_NAME = "java-diag-gateway";

    private final InMemoryRegistry registry;
    private final JsonSupport json;
    private final AdminCredentials credentials;
    private final GatewayConfig gatewayConfig;

    public AdminApiServlet(
            InMemoryRegistry registry, JsonSupport json, AdminCredentials credentials, GatewayConfig gatewayConfig) {
        this.registry = registry;
        this.json = json;
        this.credentials = credentials;
        this.gatewayConfig = gatewayConfig;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = normalizePath(req.getPathInfo());
        switch (path) {
            case "/session" -> handleSession(req, resp);
            case "/instances" -> {
                if (!requireAdmin(req, resp)) {
                    return;
                }
                handleInstances(req, resp);
            }
            default -> writeError(resp, 404, "unknown admin api path: " + path);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = normalizePath(req.getPathInfo());
        String body = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> payload = body.isBlank() ? Map.of() : json.toMap(body);

        switch (path) {
            case "/login" -> handleLogin(req, resp, payload);
            case "/logout" -> {
                if (!requireAdmin(req, resp)) {
                    return;
                }
                handleLogout(req, resp);
            }
            default -> writeError(resp, 404, "unknown admin api path: " + path);
        }
    }

    private void handleLogin(
            HttpServletRequest req, HttpServletResponse resp, Map<String, Object> payload) throws IOException {
        String user = str(payload.get("username"));
        if (user == null) {
            user = str(payload.get("user"));
        }
        String password = str(payload.get("password"));
        if (!credentials.verify(user, password)) {
            writeJson(resp, 401, Map.of("ok", false, "error", "invalid username or password"));
            return;
        }
        HttpSession session = req.getSession(true);
        session.setMaxInactiveInterval(SESSION_MAX_INACTIVE_SECONDS);
        session.setAttribute(AdminCredentials.SESSION_ATTR, Boolean.TRUE);
        writeJson(resp, 200, Map.of("ok", true, "user", user));
    }

    private void handleLogout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        writeJson(resp, 200, Map.of("ok", true));
    }

    private void handleSession(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        boolean authenticated = isAuthenticated(req);
        if (!authenticated) {
            writeJson(resp, 200, Map.of("ok", true, "authenticated", false));
            return;
        }
        Map<String, Object> mcp = new java.util.LinkedHashMap<>();
        mcp.put("serverName", MCP_SERVER_NAME);
        if (gatewayConfig.authToken().isPresent()) {
            mcp.put("agentAuthConfigured", true);
            mcp.put("agentAuthToken", gatewayConfig.authToken().get());
        } else {
            mcp.put("agentAuthConfigured", false);
        }
        writeJson(
                resp,
                200,
                Map.of(
                        "ok", true,
                        "authenticated", true,
                        "mcp", mcp));
    }

    private void handleInstances(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String service = req.getParameter("serviceName");
        List<ProbeInstance> list = registry.list(service, Map.of(), false);
        writeJson(resp, 200, Map.of(
                "ok", true,
                "count", list.size(),
                "instances", list.stream().map(ProbeInstance::toAdminMap).collect(Collectors.toList())));
    }

    static boolean isAuthenticated(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) {
            return false;
        }
        Object flag = session.getAttribute(AdminCredentials.SESSION_ATTR);
        return Boolean.TRUE.equals(flag);
    }

    private boolean requireAdmin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (isAuthenticated(req)) {
            return true;
        }
        writeJson(resp, 401, Map.of("ok", false, "error", "admin login required"));
        return false;
    }

    private static String normalizePath(String pathInfo) {
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

    private void writeJson(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json; charset=utf-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getOutputStream().write(json.toJson(body).getBytes(StandardCharsets.UTF_8));
    }

    private void writeError(HttpServletResponse resp, int status, String message) throws IOException {
        writeJson(resp, status, Map.of("ok", false, "error", message));
    }
}

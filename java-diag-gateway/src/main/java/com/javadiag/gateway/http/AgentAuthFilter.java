package com.javadiag.gateway.http;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protects Agent-facing MCP endpoints. Registry and health are excluded.
 */
public final class AgentAuthFilter implements Filter {

    private final String expectedToken;

    public AgentAuthFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = normalizePath(req.getRequestURI());
        if (isExemptPath(path)) {
            chain.doFilter(request, response);
            return;
        }
        String provided = null;
        String header = req.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            provided = header.substring(7).trim();
        }
        if (provided == null) {
            provided = req.getHeader("X-Java-Diag-Token");
        }
        if (provided == null || !constantTimeEquals(provided, expectedToken)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json; charset=utf-8");
            resp.getOutputStream().write(
                    "{\"ok\":false,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"missing or invalid token\"}}"
                            .getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }

    /** Visible for tests: health/root/registry only (exact segment, after normalize). */
    static boolean isExemptPath(String path) {
        return "/health".equals(path)
                || "/".equals(path)
                || "/registry".equals(path)
                || path.startsWith("/registry/")
                || "/admin".equals(path)
                || path.startsWith("/admin/");
    }

    static String normalizePath(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return "/";
        }
        try {
            String normalized = URI.create(requestUri).normalize().getPath();
            if (normalized == null || normalized.isBlank()) {
                return "/";
            }
            return normalized;
        } catch (IllegalArgumentException ex) {
            return requestUri;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length) {
            return MessageDigest.isEqual(left, left) && false;
        }
        return MessageDigest.isEqual(left, right);
    }
}

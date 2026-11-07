package com.javadiag.gateway.http;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
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
        String path = req.getRequestURI();
        if ("/health".equals(path) || "/".equals(path) || path.startsWith("/registry")) {
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

    private static boolean constantTimeEquals(String a, String b) {
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length) {
            return MessageDigest.isEqual(left, left) && false;
        }
        return MessageDigest.isEqual(left, right);
    }
}

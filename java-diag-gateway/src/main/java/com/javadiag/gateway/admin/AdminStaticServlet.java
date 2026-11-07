package com.javadiag.gateway.admin;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Serves static admin UI from classpath {@code admin/}. */
public final class AdminStaticServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        if (path == null || path.isBlank() || "/".equals(path)) {
            path = "/index.html";
        }
        if (path.contains("..") || path.contains("\\")) {
            resp.sendError(404);
            return;
        }
        String resource = "admin" + path;
        try (InputStream in = AdminStaticServlet.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                resp.sendError(404);
                return;
            }
            resp.setStatus(200);
            resp.setContentType(contentType(path));
            resp.setHeader("Cache-Control", "public, max-age=300");
            try (OutputStream out = resp.getOutputStream()) {
                in.transferTo(out);
            }
        }
    }

    static String contentType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (lower.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (lower.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".woff2")) {
            return "font/woff2";
        }
        return "application/octet-stream";
    }

    static String decodePath(String raw) {
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }
}

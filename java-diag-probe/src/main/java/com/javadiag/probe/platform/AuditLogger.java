package com.javadiag.probe.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Append-only local audit log (JSON Lines).
 */
public final class AuditLogger {

    private final Path path;
    private final ReentrantLock lock = new ReentrantLock();
    private final JsonCodec codec;

    public AuditLogger(Path path, JsonCodec codec) {
        this.path = path;
        this.codec = codec;
    }

    public void record(
            String tool,
            String actor,
            Map<String, Object> args,
            long durationMs,
            boolean success,
            String errorCode) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("ts", Instant.now().toString());
        event.put("actor", actor == null ? "local" : actor);
        event.put("tool", tool);
        event.put("targetInstance", null);
        event.put("argsDigest", sha256Digest(args));
        event.put("durationMs", durationMs);
        event.put("success", success);
        event.put("errorCode", errorCode);

        String line;
        try {
            line = codec.toJson(event);
        } catch (Exception ex) {
            line = "{\"tool\":\"" + tool + "\",\"success\":" + success + ",\"error\":\"audit_serialize_failed\"}";
        }

        lock.lock();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    path,
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // audit must not break tool execution
        } finally {
            lock.unlock();
        }
    }

    private String sha256Digest(Map<String, Object> args) {
        try {
            String payload = codec.toJson(args == null ? Map.of() : args);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | RuntimeException ex) {
            return "unavailable";
        }
    }
}

package com.javadiag.probe.tools;

import com.javadiag.probe.config.DiagConfig;
import com.javadiag.probe.model.ErrorCode;
import com.javadiag.probe.model.ToolEnvelope;
import com.javadiag.probe.platform.AuditLogger;
import com.javadiag.probe.platform.DiagException;
import com.javadiag.probe.platform.JdkToolchain;
import com.javadiag.probe.platform.JsonCodec;
import com.javadiag.probe.platform.PathGuard;
import com.javadiag.probe.platform.ProcessRunner;
import com.javadiag.probe.platform.ToolRateLimiter;
import com.javadiag.probe.process.JavaProcessService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Shared dependencies and envelope/audit wrapping for tools.
 */
public final class ToolSupport {

    private final DiagConfig config;
    private final PathGuard pathGuard;
    private final ProcessRunner processRunner;
    private final JdkToolchain jdkToolchain;
    private final JavaProcessService processService;
    private final AuditLogger auditLogger;
    private final JsonCodec jsonCodec;
    private final ToolRateLimiter rateLimiter;

    public ToolSupport(
            DiagConfig config,
            PathGuard pathGuard,
            ProcessRunner processRunner,
            JdkToolchain jdkToolchain,
            JavaProcessService processService,
            AuditLogger auditLogger,
            JsonCodec jsonCodec) {
        this.config = config;
        this.pathGuard = pathGuard;
        this.processRunner = processRunner;
        this.jdkToolchain = jdkToolchain;
        this.processService = processService;
        this.auditLogger = auditLogger;
        this.jsonCodec = jsonCodec;
        this.rateLimiter = new ToolRateLimiter(config.dumpCooldownMs());
    }

    public static ToolSupport create(DiagConfig config, JsonCodec codec) {
        PathGuard pathGuard = new PathGuard(config.allowedLogDirs());
        ProcessRunner runner = new ProcessRunner(config.maxOutputBytes(), config.toolTimeoutMs());
        JdkToolchain toolchain = new JdkToolchain();
        JavaProcessService processes = new JavaProcessService(
                config.allowedMainClassRegex(), runner, toolchain);
        AuditLogger audit = new AuditLogger(config.auditPath(), codec);
        return new ToolSupport(config, pathGuard, runner, toolchain, processes, audit, codec);
    }

    public DiagConfig config() {
        return config;
    }

    public PathGuard pathGuard() {
        return pathGuard;
    }

    public ProcessRunner processRunner() {
        return processRunner;
    }

    public JdkToolchain jdkToolchain() {
        return jdkToolchain;
    }

    public JavaProcessService processService() {
        return processService;
    }

    public JsonCodec jsonCodec() {
        return jsonCodec;
    }

    public ToolEnvelope invoke(String tool, Map<String, Object> args, Function<Map<String, Object>, ToolResult> handler) {
        long start = System.currentTimeMillis();
        try {
            rateLimiter.check(tool);
            ToolResult result = handler.apply(args == null ? Map.of() : args);
            long took = System.currentTimeMillis() - start;
            auditLogger.record(tool, "local", args, took, true, null);
            return ToolEnvelope.success(tool, config.instanceId(), took, result.truncated(), result.data());
        } catch (DiagException ex) {
            long took = System.currentTimeMillis() - start;
            auditLogger.record(tool, "local", args, took, false, ex.code().name());
            return ToolEnvelope.failure(tool, config.instanceId(), took, ex.code(), ex.getMessage());
        } catch (Exception ex) {
            long took = System.currentTimeMillis() - start;
            auditLogger.record(tool, "local", args, took, false, ErrorCode.INTERNAL.name());
            return ToolEnvelope.failure(
                    tool,
                    config.instanceId(),
                    took,
                    ErrorCode.INTERNAL,
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    public long requirePid(Map<String, Object> args) {
        Object raw = args.get("pid");
        if (raw == null) {
            throw new DiagException(ErrorCode.INVALID_ARGUMENT, "pid is required");
        }
        long pid;
        if (raw instanceof Number number) {
            pid = number.longValue();
        } else {
            try {
                pid = Long.parseLong(raw.toString().trim());
            } catch (NumberFormatException ex) {
                throw new DiagException(ErrorCode.INVALID_ARGUMENT, "pid must be a number");
            }
        }
        if (pid <= 0) {
            throw new DiagException(ErrorCode.INVALID_ARGUMENT, "pid must be positive");
        }
        processService.requireAlive(pid);
        return pid;
    }

    public String optString(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? null : v.toString();
    }

    public int optInt(Map<String, Object> args, String key, int defaultValue, int min, int max) {
        Object v = args.get(key);
        if (v == null) {
            return defaultValue;
        }
        int value;
        if (v instanceof Number number) {
            value = number.intValue();
        } else {
            try {
                value = Integer.parseInt(v.toString().trim());
            } catch (NumberFormatException ex) {
                throw new DiagException(ErrorCode.INVALID_ARGUMENT, key + " must be an integer");
            }
        }
        if (value < min || value > max) {
            throw new DiagException(
                    ErrorCode.INVALID_ARGUMENT,
                    key + " must be between " + min + " and " + max);
        }
        return value;
    }

    public boolean optBoolean(Map<String, Object> args, String key, boolean defaultValue) {
        Object v = args.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        String s = v.toString().trim().toLowerCase();
        return switch (s) {
            case "1", "true", "yes", "on" -> true;
            case "0", "false", "no", "off" -> false;
            default -> throw new DiagException(ErrorCode.INVALID_ARGUMENT, key + " must be boolean");
        };
    }

    public Map<String, Object> baseMeta() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("instanceId", config.instanceId());
        map.put("version", config.version());
        return map;
    }

    public record ToolResult(Object data, boolean truncated) {
        public static ToolResult of(Object data) {
            return new ToolResult(data, false);
        }

        public static ToolResult of(Object data, boolean truncated) {
            return new ToolResult(data, truncated);
        }
    }
}

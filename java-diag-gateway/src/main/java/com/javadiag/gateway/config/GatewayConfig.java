package com.javadiag.gateway.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public final class GatewayConfig {

    public static final String ENV_HTTP_HOST = "JAVA_DIAG_HTTP_HOST";
    public static final String ENV_HTTP_PORT = "JAVA_DIAG_HTTP_PORT";
    public static final String ENV_AUTH_TOKEN = "JAVA_DIAG_AUTH_TOKEN";
    public static final String ENV_HEARTBEAT_TTL = "JAVA_DIAG_HEARTBEAT_TTL_SECONDS";
    public static final String ENV_INSTANCE_ID = "JAVA_DIAG_INSTANCE_ID";
    public static final String ENV_AUDIT_PATH = "JAVA_DIAG_GATEWAY_AUDIT_PATH";
    public static final String ENV_REGISTRY_REGISTER_TOKEN = "JAVA_DIAG_REGISTRY_REGISTER_TOKEN";
    public static final String ENV_DUMP_COOLDOWN_MS = "JAVA_DIAG_DUMP_COOLDOWN_MS";
    public static final String ENV_ADMIN_USER = "JAVA_DIAG_ADMIN_USER";
    public static final String ENV_ADMIN_PASSWORD = "JAVA_DIAG_ADMIN_PASSWORD";

    private static final String DEFAULT_HOST = "0.0.0.0";
    public static final String DEFAULT_ADMIN_USER = "admin";
    public static final String DEFAULT_ADMIN_PASSWORD = "java-diag";
    private static final int DEFAULT_PORT = 8787;
    private static final long DEFAULT_TTL_SECONDS = 90L;
    private static final long DEFAULT_DUMP_COOLDOWN_MS = 10_000L;

    private final String httpHost;
    private final int httpPort;
    private final Optional<String> authToken;
    private final long heartbeatTtlSeconds;
    private final String instanceId;
    private final String version;
    private final Path auditPath;
    private final Optional<String> registryRegisterToken;
    private final long dumpCooldownMs;
    private final String adminUser;
    private final String adminPassword;

    private GatewayConfig(
            String httpHost,
            int httpPort,
            Optional<String> authToken,
            long heartbeatTtlSeconds,
            String instanceId,
            String version,
            Path auditPath,
            Optional<String> registryRegisterToken,
            long dumpCooldownMs,
            String adminUser,
            String adminPassword) {
        this.httpHost = httpHost;
        this.httpPort = httpPort;
        this.authToken = authToken;
        this.heartbeatTtlSeconds = heartbeatTtlSeconds;
        this.instanceId = instanceId;
        this.version = version;
        this.auditPath = auditPath;
        this.registryRegisterToken = registryRegisterToken;
        this.dumpCooldownMs = dumpCooldownMs;
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
    }

    public static GatewayConfig from(List<String> cliArgs) {
        return from(System.getenv(), cliArgs);
    }

    public static GatewayConfig from(java.util.Map<String, String> env, List<String> cliArgs) {
        String host = Optional.ofNullable(env.get(ENV_HTTP_HOST)).filter(s -> !s.isBlank()).orElse(DEFAULT_HOST);
        int port = (int) parseLong(env.get(ENV_HTTP_PORT), DEFAULT_PORT);
        Optional<String> auth = Optional.ofNullable(env.get(ENV_AUTH_TOKEN)).filter(s -> !s.isBlank());
        long ttl = parseLong(env.get(ENV_HEARTBEAT_TTL), DEFAULT_TTL_SECONDS);
        String instanceId = Optional.ofNullable(env.get(ENV_INSTANCE_ID)).filter(s -> !s.isBlank())
                .orElseGet(() -> "gateway-" + ProcessHandle.current().pid());
        Path auditPath = Paths.get(Optional.ofNullable(env.get(ENV_AUDIT_PATH))
                .filter(s -> !s.isBlank())
                .orElse("./logs/java-diag-gateway-audit.log"))
                .toAbsolutePath()
                .normalize();
        Optional<String> registryToken =
                Optional.ofNullable(env.get(ENV_REGISTRY_REGISTER_TOKEN)).filter(s -> !s.isBlank());
        long dumpCooldown = parseLong(env.get(ENV_DUMP_COOLDOWN_MS), DEFAULT_DUMP_COOLDOWN_MS);
        String adminUser = Optional.ofNullable(env.get(ENV_ADMIN_USER)).filter(s -> !s.isBlank())
                .orElse(DEFAULT_ADMIN_USER);
        String adminPassword = Optional.ofNullable(env.get(ENV_ADMIN_PASSWORD)).filter(s -> !s.isBlank())
                .orElse(DEFAULT_ADMIN_PASSWORD);

        for (int i = 0; i < cliArgs.size(); i++) {
            String arg = cliArgs.get(i);
            switch (arg) {
                case "--host" -> host = requireValue(cliArgs, ++i, arg);
                case "--port" -> port = Integer.parseInt(requireValue(cliArgs, ++i, arg));
                case "--token", "--auth-token" -> auth = Optional.of(requireValue(cliArgs, ++i, arg));
                case "--heartbeat-ttl-seconds" -> ttl = Long.parseLong(requireValue(cliArgs, ++i, arg));
                case "--register-token" -> registryToken = Optional.of(requireValue(cliArgs, ++i, arg));
                case "--admin-user" -> adminUser = requireValue(cliArgs, ++i, arg);
                case "--admin-password" -> adminPassword = requireValue(cliArgs, ++i, arg);
                default -> {
                }
            }
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        if (ttl < 5) {
            throw new IllegalArgumentException("heartbeat TTL must be >= 5 seconds");
        }
        return new GatewayConfig(
                host, port, auth, ttl, instanceId, "0.1.0", auditPath, registryToken, dumpCooldown, adminUser,
                adminPassword);
    }

    private static String requireValue(List<String> args, int index, String flag) {
        if (index >= args.size() || args.get(index).startsWith("-")) {
            throw new IllegalArgumentException(flag + " requires a value");
        }
        String v = args.get(index);
        if (v.isBlank()) {
            throw new IllegalArgumentException(flag + " must not be blank");
        }
        return v;
    }

    private static long parseLong(String raw, long defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Long.parseLong(raw.trim());
    }

    public String httpHost() {
        return httpHost;
    }

    public int httpPort() {
        return httpPort;
    }

    public Optional<String> authToken() {
        return authToken;
    }

    public long heartbeatTtlSeconds() {
        return heartbeatTtlSeconds;
    }

    public String instanceId() {
        return instanceId;
    }

    public String version() {
        return version;
    }

    public Path auditPath() {
        return auditPath;
    }

    public Optional<String> registryRegisterToken() {
        return registryRegisterToken;
    }

    public long dumpCooldownMs() {
        return dumpCooldownMs;
    }

    public String adminUser() {
        return adminUser;
    }

    public String adminPassword() {
        return adminPassword;
    }
}

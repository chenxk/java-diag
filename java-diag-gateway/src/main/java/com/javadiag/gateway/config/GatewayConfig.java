package com.javadiag.gateway.config;

import java.util.List;
import java.util.Optional;

public final class GatewayConfig {

    public static final String ENV_HTTP_HOST = "JAVA_DIAG_HTTP_HOST";
    public static final String ENV_HTTP_PORT = "JAVA_DIAG_HTTP_PORT";
    public static final String ENV_AUTH_TOKEN = "JAVA_DIAG_AUTH_TOKEN";
    public static final String ENV_REGISTER_TOKEN = "JAVA_DIAG_REGISTER_TOKEN";
    public static final String ENV_HEARTBEAT_TTL = "JAVA_DIAG_HEARTBEAT_TTL_SECONDS";
    public static final String ENV_INSTANCE_ID = "JAVA_DIAG_INSTANCE_ID";

    private static final String DEFAULT_HOST = "0.0.0.0";
    private static final int DEFAULT_PORT = 8787;
    private static final long DEFAULT_TTL_SECONDS = 90L;

    private final String httpHost;
    private final int httpPort;
    private final Optional<String> authToken;
    private final String registerToken;
    private final long heartbeatTtlSeconds;
    private final String instanceId;
    private final String version;

    private GatewayConfig(
            String httpHost,
            int httpPort,
            Optional<String> authToken,
            String registerToken,
            long heartbeatTtlSeconds,
            String instanceId,
            String version) {
        this.httpHost = httpHost;
        this.httpPort = httpPort;
        this.authToken = authToken;
        this.registerToken = registerToken;
        this.heartbeatTtlSeconds = heartbeatTtlSeconds;
        this.instanceId = instanceId;
        this.version = version;
    }

    public static GatewayConfig from(List<String> cliArgs) {
        return from(System.getenv(), cliArgs);
    }

    public static GatewayConfig from(java.util.Map<String, String> env, List<String> cliArgs) {
        String host = Optional.ofNullable(env.get(ENV_HTTP_HOST)).filter(s -> !s.isBlank()).orElse(DEFAULT_HOST);
        int port = (int) parseLong(env.get(ENV_HTTP_PORT), DEFAULT_PORT);
        Optional<String> auth = Optional.ofNullable(env.get(ENV_AUTH_TOKEN)).filter(s -> !s.isBlank());
        String register = Optional.ofNullable(env.get(ENV_REGISTER_TOKEN)).filter(s -> !s.isBlank()).orElse("");
        long ttl = parseLong(env.get(ENV_HEARTBEAT_TTL), DEFAULT_TTL_SECONDS);
        String instanceId = Optional.ofNullable(env.get(ENV_INSTANCE_ID)).filter(s -> !s.isBlank())
                .orElseGet(() -> "gateway-" + ProcessHandle.current().pid());

        for (int i = 0; i < cliArgs.size(); i++) {
            String arg = cliArgs.get(i);
            switch (arg) {
                case "--host" -> host = requireValue(cliArgs, ++i, arg);
                case "--port" -> port = Integer.parseInt(requireValue(cliArgs, ++i, arg));
                case "--token", "--auth-token" -> auth = Optional.of(requireValue(cliArgs, ++i, arg));
                case "--register-token" -> register = requireValue(cliArgs, ++i, arg);
                case "--heartbeat-ttl-seconds" -> ttl = Long.parseLong(requireValue(cliArgs, ++i, arg));
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
        if (register == null || register.isBlank()) {
            throw new IllegalArgumentException("JAVA_DIAG_REGISTER_TOKEN or --register-token is required");
        }
        return new GatewayConfig(host, port, auth, register, ttl, instanceId, "0.1.0");
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

    public String registerToken() {
        return registerToken;
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
}

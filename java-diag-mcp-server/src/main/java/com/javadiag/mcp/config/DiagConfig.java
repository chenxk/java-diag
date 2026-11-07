package com.javadiag.mcp.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Runtime configuration loaded from environment variables / CLI overrides.
 */
public final class DiagConfig {

    public static final String ENV_ALLOWED_LOG_DIRS = "JAVA_DIAG_ALLOWED_LOG_DIRS";
    public static final String ENV_ALLOWED_MAIN_CLASS_REGEX = "JAVA_DIAG_ALLOWED_MAIN_CLASS_REGEX";
    public static final String ENV_TOOL_TIMEOUT_MS = "JAVA_DIAG_TOOL_TIMEOUT_MS";
    public static final String ENV_MAX_OUTPUT_BYTES = "JAVA_DIAG_MAX_OUTPUT_BYTES";
    public static final String ENV_DANGEROUS_TOOLS = "JAVA_DIAG_DANGEROUS_TOOLS_ENABLED";
    public static final String ENV_AUDIT_PATH = "JAVA_DIAG_AUDIT_PATH";
    public static final String ENV_INSTANCE_ID = "JAVA_DIAG_INSTANCE_ID";
    public static final String ENV_TRANSPORT = "JAVA_DIAG_TRANSPORT";
    public static final String ENV_HTTP_HOST = "JAVA_DIAG_HTTP_HOST";
    public static final String ENV_HTTP_PORT = "JAVA_DIAG_HTTP_PORT";
    public static final String ENV_AUTH_TOKEN = "JAVA_DIAG_AUTH_TOKEN";
    public static final String ENV_SERVICE_NAME = "JAVA_DIAG_SERVICE_NAME";
    public static final String ENV_REGISTER_URL = "JAVA_DIAG_REGISTER_URL";
    public static final String ENV_ADVERTISE_URL = "JAVA_DIAG_ADVERTISE_URL";
    public static final String ENV_HEARTBEAT_INTERVAL = "JAVA_DIAG_HEARTBEAT_INTERVAL_SECONDS";
    public static final String ENV_TAGS = "JAVA_DIAG_TAGS";

    private static final long DEFAULT_TIMEOUT_MS = 15_000L;
    private static final long DEFAULT_DUMP_TIMEOUT_MS = 30_000L;
    private static final int DEFAULT_MAX_OUTPUT_BYTES = 256 * 1024;
    private static final String DEFAULT_HTTP_HOST = "0.0.0.0";
    private static final int DEFAULT_HTTP_PORT = 8765;
    private static final long DEFAULT_HEARTBEAT_INTERVAL = 20L;

    public enum Transport {
        HTTP,
        STDIO
    }

    private final List<Path> allowedLogDirs;
    private final Optional<Pattern> allowedMainClassRegex;
    private final long toolTimeoutMs;
    private final long dumpTimeoutMs;
    private final int maxOutputBytes;
    private final boolean dangerousToolsEnabled;
    private final Path auditPath;
    private final String instanceId;
    private final String version;
    private final Transport transport;
    private final String httpHost;
    private final int httpPort;
    private final Optional<String> authToken;
    private final Optional<String> serviceName;
    private final Optional<String> registerUrl;
    private final Optional<String> advertiseUrl;
    private final long heartbeatIntervalSeconds;
    private final Map<String, String> tags;

    private DiagConfig(
            List<Path> allowedLogDirs,
            Optional<Pattern> allowedMainClassRegex,
            long toolTimeoutMs,
            long dumpTimeoutMs,
            int maxOutputBytes,
            boolean dangerousToolsEnabled,
            Path auditPath,
            String instanceId,
            String version,
            Transport transport,
            String httpHost,
            int httpPort,
            Optional<String> authToken,
            Optional<String> serviceName,
            Optional<String> registerUrl,
            Optional<String> advertiseUrl,
            long heartbeatIntervalSeconds,
            Map<String, String> tags) {
        this.allowedLogDirs = List.copyOf(allowedLogDirs);
        this.allowedMainClassRegex = allowedMainClassRegex;
        this.toolTimeoutMs = toolTimeoutMs;
        this.dumpTimeoutMs = dumpTimeoutMs;
        this.maxOutputBytes = maxOutputBytes;
        this.dangerousToolsEnabled = dangerousToolsEnabled;
        this.auditPath = auditPath;
        this.instanceId = instanceId;
        this.version = version;
        this.transport = transport;
        this.httpHost = httpHost;
        this.httpPort = httpPort;
        this.authToken = authToken;
        this.serviceName = serviceName;
        this.registerUrl = registerUrl;
        this.advertiseUrl = advertiseUrl;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        this.tags = Map.copyOf(tags);
    }

    public static DiagConfig fromEnvironment() {
        return fromEnvironment(System.getenv(), List.of());
    }

    public static DiagConfig fromEnvironment(java.util.Map<String, String> env) {
        return fromEnvironment(env, List.of());
    }

    public static DiagConfig fromEnvironment(java.util.Map<String, String> env, List<String> cliArgs) {
        List<Path> dirs = parseDirs(env.get(ENV_ALLOWED_LOG_DIRS));
        if (dirs.isEmpty()) {
            dirs = defaultAllowedDirs();
        }

        Optional<Pattern> mainClassRegex = Optional.empty();
        String regex = env.get(ENV_ALLOWED_MAIN_CLASS_REGEX);
        if (regex != null && !regex.isBlank()) {
            mainClassRegex = Optional.of(Pattern.compile(regex));
        }

        long timeout = parseLong(env.get(ENV_TOOL_TIMEOUT_MS), DEFAULT_TIMEOUT_MS);
        int maxBytes = (int) Math.min(Integer.MAX_VALUE,
                parseLong(env.get(ENV_MAX_OUTPUT_BYTES), DEFAULT_MAX_OUTPUT_BYTES));
        boolean dangerous = parseBoolean(env.get(ENV_DANGEROUS_TOOLS), false);

        Path audit = Paths.get(Optional.ofNullable(env.get(ENV_AUDIT_PATH))
                .filter(s -> !s.isBlank())
                .orElse("./logs/java-diag-audit.log"))
                .toAbsolutePath()
                .normalize();

        String instanceId = Optional.ofNullable(env.get(ENV_INSTANCE_ID))
                .filter(s -> !s.isBlank())
                .orElseGet(DiagConfig::defaultInstanceId);

        Transport transport = parseTransport(env.get(ENV_TRANSPORT), Transport.HTTP);
        String httpHost = Optional.ofNullable(env.get(ENV_HTTP_HOST))
                .filter(s -> !s.isBlank())
                .orElse(DEFAULT_HTTP_HOST);
        int httpPort = (int) parseLong(env.get(ENV_HTTP_PORT), DEFAULT_HTTP_PORT);
        Optional<String> authToken = Optional.ofNullable(env.get(ENV_AUTH_TOKEN)).filter(s -> !s.isBlank());
        Optional<String> serviceName = Optional.ofNullable(env.get(ENV_SERVICE_NAME)).filter(s -> !s.isBlank());
        Optional<String> registerUrl = Optional.ofNullable(env.get(ENV_REGISTER_URL)).filter(s -> !s.isBlank());
        Optional<String> advertiseUrl = Optional.ofNullable(env.get(ENV_ADVERTISE_URL)).filter(s -> !s.isBlank());
        long heartbeatInterval = parseLong(env.get(ENV_HEARTBEAT_INTERVAL), DEFAULT_HEARTBEAT_INTERVAL);
        Map<String, String> tags = parseTags(env.get(ENV_TAGS));

        for (int i = 0; i < cliArgs.size(); i++) {
            String arg = cliArgs.get(i);
            switch (arg) {
                case "--stdio" -> transport = Transport.STDIO;
                case "--http" -> transport = Transport.HTTP;
                case "--host" -> httpHost = requireValue(cliArgs, ++i, arg);
                case "--port" -> httpPort = Integer.parseInt(requireValue(cliArgs, ++i, arg));
                case "--token", "--auth-token" -> authToken = Optional.of(requireValue(cliArgs, ++i, arg));
                case "--instance-id" -> instanceId = requireValue(cliArgs, ++i, arg);
                case "--service-name" -> serviceName = Optional.of(requireValue(cliArgs, ++i, arg));
                case "--register-url" -> registerUrl = Optional.of(requireValue(cliArgs, ++i, arg));
                case "--advertise-url" -> advertiseUrl = Optional.of(requireValue(cliArgs, ++i, arg));
                case "--heartbeat-interval-seconds" ->
                        heartbeatInterval = Long.parseLong(requireValue(cliArgs, ++i, arg));
                case "--tag" -> {
                    String kv = requireValue(cliArgs, ++i, arg);
                    int eq = kv.indexOf('=');
                    if (eq <= 0) {
                        throw new IllegalArgumentException("--tag requires key=value, got: " + kv);
                    }
                    Map<String, String> mutable = new LinkedHashMap<>(tags);
                    mutable.put(kv.substring(0, eq), kv.substring(eq + 1));
                    tags = mutable;
                }
                default -> {
                }
            }
        }

        if (httpPort < 1 || httpPort > 65535) {
            throw new IllegalArgumentException("http port out of range: " + httpPort);
        }

        return new DiagConfig(
                dirs,
                mainClassRegex,
                timeout,
                DEFAULT_DUMP_TIMEOUT_MS,
                maxBytes,
                dangerous,
                audit,
                instanceId,
                "0.1.0",
                transport,
                httpHost,
                httpPort,
                authToken,
                serviceName,
                registerUrl,
                advertiseUrl,
                heartbeatInterval,
                tags);
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

    private static Map<String, String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String part : raw.split(",")) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            int eq = item.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            map.put(item.substring(0, eq).trim(), item.substring(eq + 1).trim());
        }
        return map;
    }

    private static Transport parseTransport(String raw, Transport defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "http", "sse", "remote" -> Transport.HTTP;
            case "stdio", "std", "local" -> Transport.STDIO;
            default -> throw new IllegalArgumentException(
                    "Unknown JAVA_DIAG_TRANSPORT='" + raw + "' (use http|stdio)");
        };
    }

    private static List<Path> defaultAllowedDirs() {
        List<Path> dirs = new ArrayList<>();
        dirs.add(Paths.get(".").toAbsolutePath().normalize());
        dirs.add(Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize());
        dirs.add(Paths.get("/tmp").toAbsolutePath().normalize());
        dirs.add(Paths.get("/var/log").toAbsolutePath().normalize());
        return dirs;
    }

    private static List<Path> parseDirs(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(p -> Paths.get(p).toAbsolutePath().normalize())
                .toList();
    }

    private static long parseLong(String raw, long defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static boolean parseBoolean(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes", "on" -> true;
            case "0", "false", "no", "off" -> false;
            default -> defaultValue;
        };
    }

    private static String defaultInstanceId() {
        String host = "localhost";
        try {
            host = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            // keep default
        }
        return host + "-" + ProcessHandle.current().pid();
    }

    public List<Path> allowedLogDirs() {
        return allowedLogDirs;
    }

    public Optional<Pattern> allowedMainClassRegex() {
        return allowedMainClassRegex;
    }

    public long toolTimeoutMs() {
        return toolTimeoutMs;
    }

    public long dumpTimeoutMs() {
        return dumpTimeoutMs;
    }

    public int maxOutputBytes() {
        return maxOutputBytes;
    }

    public boolean dangerousToolsEnabled() {
        return dangerousToolsEnabled;
    }

    public Path auditPath() {
        return auditPath;
    }

    public String instanceId() {
        return instanceId;
    }

    public String version() {
        return version;
    }

    public Transport transport() {
        return transport;
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

    public Optional<String> serviceName() {
        return serviceName;
    }

    public Optional<String> registerUrl() {
        return registerUrl;
    }

    public Optional<String> advertiseUrl() {
        return advertiseUrl;
    }

    public long heartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    public Map<String, String> tags() {
        return tags;
    }
}

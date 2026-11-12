package com.javadiag.probe.config;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import com.javadiag.probe.process.AppNameMapper;

import java.util.regex.Pattern;

/**
 * Runtime configuration: built-in defaults → properties file → environment → CLI.
 */
public final class DiagConfig {

    /** Dot keys in {@code java-diag-probe.properties} (legacy {@code JAVA_DIAG_*} names also accepted). */
    public static final String PROP_TRANSPORT = "transport";
    public static final String PROP_HTTP_HOST = "http.host";
    public static final String PROP_HTTP_PORT = "http.port";
    public static final String PROP_AUTH_TOKEN = "auth.token";
    public static final String PROP_ALLOWED_LOG_DIRS = "allowed.log.dirs";
    public static final String PROP_ALLOWED_MAIN_CLASS_REGEX = "allowed.main.class.regex";
    public static final String PROP_TOOL_TIMEOUT_MS = "tool.timeout.ms";
    public static final String PROP_MAX_OUTPUT_BYTES = "max.output.bytes";
    public static final String PROP_DANGEROUS_TOOLS = "dangerous.tools.enabled";
    public static final String PROP_AUDIT_PATH = "audit.path";
    public static final String PROP_SERVICE_NAME = "service.name";
    public static final String PROP_REGISTER_URL = "register.url";
    public static final String PROP_REGISTER_TOKEN = "register.token";
    public static final String PROP_ADVERTISE_URL = "advertise.url";
    public static final String PROP_HEARTBEAT_INTERVAL = "heartbeat.interval.seconds";
    public static final String PROP_TAGS = "tags";
    public static final String PROP_DUMP_COOLDOWN_MS = "dump.cooldown.ms";
    public static final String PROP_DUMP_TIMEOUT_MS = "dump.timeout.ms";
    public static final String PROP_APP_NAME_MAP = "app.name.map";

    public static final String ENV_ALLOWED_LOG_DIRS = "JAVA_DIAG_ALLOWED_LOG_DIRS";
    public static final String ENV_ALLOWED_MAIN_CLASS_REGEX = "JAVA_DIAG_ALLOWED_MAIN_CLASS_REGEX";
    public static final String ENV_TOOL_TIMEOUT_MS = "JAVA_DIAG_TOOL_TIMEOUT_MS";
    public static final String ENV_MAX_OUTPUT_BYTES = "JAVA_DIAG_MAX_OUTPUT_BYTES";
    public static final String ENV_DANGEROUS_TOOLS = "JAVA_DIAG_DANGEROUS_TOOLS_ENABLED";
    public static final String ENV_AUDIT_PATH = "JAVA_DIAG_AUDIT_PATH";
    public static final String ENV_TRANSPORT = "JAVA_DIAG_TRANSPORT";
    public static final String ENV_HTTP_HOST = "JAVA_DIAG_HTTP_HOST";
    public static final String ENV_HTTP_PORT = "JAVA_DIAG_HTTP_PORT";
    public static final String ENV_AUTH_TOKEN = "JAVA_DIAG_AUTH_TOKEN";
    public static final String ENV_SERVICE_NAME = "JAVA_DIAG_SERVICE_NAME";
    public static final String ENV_REGISTER_URL = "JAVA_DIAG_REGISTER_URL";
    public static final String ENV_ADVERTISE_URL = "JAVA_DIAG_ADVERTISE_URL";
    public static final String ENV_HEARTBEAT_INTERVAL = "JAVA_DIAG_HEARTBEAT_INTERVAL_SECONDS";
    public static final String ENV_TAGS = "JAVA_DIAG_TAGS";
    public static final String ENV_DUMP_COOLDOWN_MS = "JAVA_DIAG_DUMP_COOLDOWN_MS";
    public static final String ENV_DUMP_TIMEOUT_MS = "JAVA_DIAG_DUMP_TIMEOUT_MS";
    public static final String ENV_REGISTRY_REGISTER_TOKEN = "JAVA_DIAG_REGISTRY_REGISTER_TOKEN";
    public static final String ENV_APP_NAME_MAP = "JAVA_DIAG_APP_NAME_MAP";

    private static final long DEFAULT_TIMEOUT_MS = 15_000L;
    private static final long DEFAULT_DUMP_COOLDOWN_MS = 10_000L;
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
    private final String serviceName;
    private final Optional<String> registerUrl;
    private final Optional<String> advertiseUrl;
    private final long heartbeatIntervalSeconds;
    private final Map<String, String> tags;
    private final long dumpCooldownMs;
    private final Optional<String> registryRegisterToken;
    private final AppNameMapper appNameMapper;

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
            String serviceName,
            Optional<String> registerUrl,
            Optional<String> advertiseUrl,
            long heartbeatIntervalSeconds,
            Map<String, String> tags,
            long dumpCooldownMs,
            Optional<String> registryRegisterToken,
            AppNameMapper appNameMapper) {
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
        this.dumpCooldownMs = dumpCooldownMs;
        this.registryRegisterToken = registryRegisterToken;
        this.appNameMapper = appNameMapper == null ? AppNameMapper.empty() : appNameMapper;
    }

    public static DiagConfig load(java.util.Map<String, String> env, List<String> cliArgs) {
        SettingsProperties.ConfigLayer layer = SettingsProperties.loadConfigLayer(cliArgs);
        return fromEnvironment(env, layer.cliArgsWithoutConfig(), layer.fileSettings());
    }

    public static DiagConfig fromEnvironment() {
        return fromEnvironment(System.getenv(), List.of());
    }

    public static DiagConfig fromEnvironment(java.util.Map<String, String> env) {
        return fromEnvironment(env, List.of());
    }

    public static DiagConfig fromEnvironment(java.util.Map<String, String> env, List<String> cliArgs) {
        return fromEnvironment(env, cliArgs, Map.of());
    }

    public static DiagConfig fromEnvironment(
            java.util.Map<String, String> env, List<String> cliArgs, Map<String, String> fileSettings) {
        List<Path> dirs = parseDirs(SettingsProperties.resolve(
                fileSettings, env, ENV_ALLOWED_LOG_DIRS, PROP_ALLOWED_LOG_DIRS, ENV_ALLOWED_LOG_DIRS));
        if (dirs.isEmpty()) {
            dirs = defaultAllowedDirs();
        }

        Optional<Pattern> mainClassRegex = Optional.empty();
        String regex = SettingsProperties.resolve(
                fileSettings,
                env,
                ENV_ALLOWED_MAIN_CLASS_REGEX,
                PROP_ALLOWED_MAIN_CLASS_REGEX,
                ENV_ALLOWED_MAIN_CLASS_REGEX);
        if (regex != null && !regex.isBlank()) {
            mainClassRegex = Optional.of(Pattern.compile(regex));
        }

        long timeout = parseLong(
                SettingsProperties.resolve(
                        fileSettings, env, ENV_TOOL_TIMEOUT_MS, PROP_TOOL_TIMEOUT_MS, ENV_TOOL_TIMEOUT_MS),
                DEFAULT_TIMEOUT_MS);
        int maxBytes = (int) Math.min(
                Integer.MAX_VALUE,
                parseLong(
                        SettingsProperties.resolve(
                                fileSettings, env, ENV_MAX_OUTPUT_BYTES, PROP_MAX_OUTPUT_BYTES, ENV_MAX_OUTPUT_BYTES),
                        DEFAULT_MAX_OUTPUT_BYTES));
        boolean dangerous = parseBoolean(
                SettingsProperties.resolve(
                        fileSettings, env, ENV_DANGEROUS_TOOLS, PROP_DANGEROUS_TOOLS, ENV_DANGEROUS_TOOLS),
                false);

        Path audit = Paths.get(Optional.ofNullable(SettingsProperties.resolve(
                        fileSettings, env, ENV_AUDIT_PATH, PROP_AUDIT_PATH, ENV_AUDIT_PATH))
                .filter(s -> !s.isBlank())
                .orElse("./logs/java-diag-audit.log"))
                .toAbsolutePath()
                .normalize();

        Transport transport = parseTransport(
                SettingsProperties.resolve(fileSettings, env, ENV_TRANSPORT, PROP_TRANSPORT, ENV_TRANSPORT),
                Transport.HTTP);
        String httpHost = Optional.ofNullable(SettingsProperties.resolve(
                        fileSettings, env, ENV_HTTP_HOST, PROP_HTTP_HOST, ENV_HTTP_HOST))
                .filter(s -> !s.isBlank())
                .orElse(DEFAULT_HTTP_HOST);
        int httpPort = (int) parseLong(
                SettingsProperties.resolve(fileSettings, env, ENV_HTTP_PORT, PROP_HTTP_PORT, ENV_HTTP_PORT),
                DEFAULT_HTTP_PORT);
        Optional<String> authToken = Optional.ofNullable(SettingsProperties.resolve(
                        fileSettings, env, ENV_AUTH_TOKEN, PROP_AUTH_TOKEN, ENV_AUTH_TOKEN))
                .filter(s -> !s.isBlank());
        Optional<String> serviceNameOpt = Optional.ofNullable(SettingsProperties.resolve(
                        fileSettings, env, ENV_SERVICE_NAME, PROP_SERVICE_NAME, ENV_SERVICE_NAME))
                .filter(s -> !s.isBlank());
        Optional<String> registerUrl = Optional.ofNullable(SettingsProperties.resolve(
                        fileSettings, env, ENV_REGISTER_URL, PROP_REGISTER_URL, ENV_REGISTER_URL))
                .filter(s -> !s.isBlank());
        Optional<String> advertiseUrl = Optional.ofNullable(SettingsProperties.resolve(
                        fileSettings, env, ENV_ADVERTISE_URL, PROP_ADVERTISE_URL, ENV_ADVERTISE_URL))
                .filter(s -> !s.isBlank());
        long heartbeatInterval = parseLong(
                SettingsProperties.resolve(
                        fileSettings,
                        env,
                        ENV_HEARTBEAT_INTERVAL,
                        PROP_HEARTBEAT_INTERVAL,
                        ENV_HEARTBEAT_INTERVAL),
                DEFAULT_HEARTBEAT_INTERVAL);
        Map<String, String> tags = parseTags(
                SettingsProperties.resolve(fileSettings, env, ENV_TAGS, PROP_TAGS, ENV_TAGS));
        long dumpCooldownMs = parseLong(
                SettingsProperties.resolve(
                        fileSettings, env, ENV_DUMP_COOLDOWN_MS, PROP_DUMP_COOLDOWN_MS, ENV_DUMP_COOLDOWN_MS),
                DEFAULT_DUMP_COOLDOWN_MS);
        long dumpTimeoutMs = parseLong(
                SettingsProperties.resolve(
                        fileSettings, env, ENV_DUMP_TIMEOUT_MS, PROP_DUMP_TIMEOUT_MS, ENV_DUMP_TIMEOUT_MS),
                DEFAULT_DUMP_TIMEOUT_MS);
        Optional<String> registryRegisterToken = Optional.ofNullable(SettingsProperties.resolve(
                        fileSettings,
                        env,
                        ENV_REGISTRY_REGISTER_TOKEN,
                        PROP_REGISTER_TOKEN,
                        ENV_REGISTRY_REGISTER_TOKEN))
                .filter(s -> !s.isBlank());
        String appNameMapEnv = SettingsProperties.resolve(
                fileSettings, env, ENV_APP_NAME_MAP, PROP_APP_NAME_MAP, ENV_APP_NAME_MAP);
        List<String> cliAppMaps = new ArrayList<>();

        for (int i = 0; i < cliArgs.size(); i++) {
            String arg = cliArgs.get(i);
            switch (arg) {
                case "--stdio" -> transport = Transport.STDIO;
                case "--http" -> transport = Transport.HTTP;
                case "--host" -> httpHost = requireValue(cliArgs, ++i, arg);
                case "--port" -> httpPort = Integer.parseInt(requireValue(cliArgs, ++i, arg));
                case "--token", "--auth-token" -> authToken = Optional.of(requireValue(cliArgs, ++i, arg));
                case "--service-name" -> serviceNameOpt = Optional.of(requireValue(cliArgs, ++i, arg));
                case "--register-url" -> registerUrl = Optional.of(requireValue(cliArgs, ++i, arg));
                case "--register-token" -> registryRegisterToken = Optional.of(requireValue(cliArgs, ++i, arg));
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
                case "--app-map" -> cliAppMaps.add(requireValue(cliArgs, ++i, arg));
                default -> {
                }
            }
        }

        AppNameMapper appNameMapper = AppNameMapper.parse(appNameMapEnv, cliAppMaps);

        if (httpPort < 1 || httpPort > 65535) {
            throw new IllegalArgumentException("http port out of range: " + httpPort);
        }

        authToken = requireConfiguredToken(authToken, PROP_AUTH_TOKEN, ENV_AUTH_TOKEN, "--token / --auth-token");
        registryRegisterToken = requireConfiguredToken(
                registryRegisterToken, PROP_REGISTER_TOKEN, ENV_REGISTRY_REGISTER_TOKEN, "--register-token");

        String serviceName = serviceNameOpt.orElseGet(DiagConfig::primaryIpAddress);
        // Registry identity = advertise host:port (mcpBaseUrl); no separate instance-id env/CLI.
        String instanceId = instanceIdFromAdvertise(advertiseUrl, httpHost, httpPort);

        return new DiagConfig(
                dirs,
                mainClassRegex,
                timeout,
                dumpTimeoutMs,
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
                tags,
                dumpCooldownMs,
                registryRegisterToken,
                appNameMapper);
    }

    /** MCP and Registry tokens are mandatory — empty means unauthenticated access. */
    static Optional<String> requireConfiguredToken(
            Optional<String> value, String propKey, String envKey, String cliHint) {
        if (value.isEmpty() || value.get().isBlank()) {
            throw new IllegalArgumentException(
                    propKey + " is required (also " + envKey + " or " + cliHint + "); empty tokens are not allowed");
        }
        return value;
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
        return new ArrayList<>(com.javadiag.probe.platform.OsPlatform.defaultAllowedLogDirs());
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

    /**
     * Primary machine IP (not hostname). Prefers a non-loopback IPv4 from an up NIC;
     * falls back to {@link InetAddress#getLocalHost()} address, then {@code 127.0.0.1}.
     * Used as the default for {@code serviceName} when unset.
     */
    static String primaryIpAddress() {
        try {
            InetAddress ipv6Fallback = null;
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            if (nics != null) {
                while (nics.hasMoreElements()) {
                    NetworkInterface nic = nics.nextElement();
                    if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
                        continue;
                    }
                    Enumeration<InetAddress> addrs = nic.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress addr = addrs.nextElement();
                        if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                            continue;
                        }
                        if (addr instanceof Inet4Address) {
                            return addr.getHostAddress();
                        }
                        if (ipv6Fallback == null) {
                            ipv6Fallback = addr;
                        }
                    }
                }
            }
            if (ipv6Fallback != null) {
                return ipv6Fallback.getHostAddress();
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {
            return "127.0.0.1";
        }
    }

    /**
     * Registry / envelope instance identity derived from advertise URL (or listen host) + port.
     * Format {@code host:port} — same host the registry stores in {@code mcpBaseUrl}.
     */
    public static String instanceIdFromAdvertise(Optional<String> advertiseUrl, String httpHost, int httpPort) {
        return resolveAdvertiseHost(advertiseUrl, httpHost) + ":" + resolveAdvertisePort(advertiseUrl, httpPort);
    }

    /**
     * Host advertised to the gateway (from {@code JAVA_DIAG_ADVERTISE_URL}, else bind host, else primary IP).
     */
    public static String resolveAdvertiseHost(Optional<String> advertiseUrl, String httpHost) {
        if (advertiseUrl != null && advertiseUrl.isPresent()) {
            try {
                String host = URI.create(advertiseUrl.get()).getHost();
                if (host != null && !host.isBlank()) {
                    return host;
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        if (httpHost != null && !httpHost.isBlank() && !"0.0.0.0".equals(httpHost) && !"[::]".equals(httpHost)) {
            return httpHost;
        }
        return primaryIpAddress();
    }

    public static int resolveAdvertisePort(Optional<String> advertiseUrl, int httpPort) {
        if (advertiseUrl != null && advertiseUrl.isPresent()) {
            try {
                int port = URI.create(advertiseUrl.get()).getPort();
                if (port > 0) {
                    return port;
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return httpPort;
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

    public String serviceName() {
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

    public long dumpCooldownMs() {
        return dumpCooldownMs;
    }

    public Optional<String> registryRegisterToken() {
        return registryRegisterToken;
    }

    public AppNameMapper appNameMapper() {
        return appNameMapper;
    }
}

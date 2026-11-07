package com.javadiag.gateway.registry;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ProbeInstance {

    private final String instanceId;
    private final String serviceName;
    private final String host;
    private final int port;
    private final String mcpBaseUrl;
    private final String ssePath;
    private final String mcpPath;
    private final String probeToken;
    private final Map<String, String> tags;
    private final String version;
    private volatile Instant lastHeartbeat;
    private volatile ProbeStatus status;

    public ProbeInstance(
            String instanceId,
            String serviceName,
            String host,
            int port,
            String mcpBaseUrl,
            String ssePath,
            String mcpPath,
            String probeToken,
            Map<String, String> tags,
            String version,
            Instant lastHeartbeat,
            ProbeStatus status) {
        this.instanceId = Objects.requireNonNull(instanceId);
        this.serviceName = serviceName == null ? "" : serviceName;
        this.host = host;
        this.port = port;
        this.mcpBaseUrl = Objects.requireNonNull(mcpBaseUrl);
        this.ssePath = ssePath == null || ssePath.isBlank() ? "/sse" : ssePath;
        this.mcpPath = mcpPath == null || mcpPath.isBlank() ? "/mcp" : mcpPath;
        this.probeToken = probeToken;
        this.tags = Map.copyOf(tags == null ? Map.of() : tags);
        this.version = version;
        this.lastHeartbeat = lastHeartbeat == null ? Instant.now() : lastHeartbeat;
        this.status = status == null ? ProbeStatus.HEALTHY : status;
    }

    public String instanceId() {
        return instanceId;
    }

    public String serviceName() {
        return serviceName;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String mcpBaseUrl() {
        return mcpBaseUrl;
    }

    public String ssePath() {
        return ssePath;
    }

    public String mcpPath() {
        return mcpPath;
    }

    public String probeToken() {
        return probeToken;
    }

    public Map<String, String> tags() {
        return tags;
    }

    public String version() {
        return version;
    }

    public Instant lastHeartbeat() {
        return lastHeartbeat;
    }

    public ProbeStatus status() {
        return status;
    }

    public void touchHeartbeat() {
        this.lastHeartbeat = Instant.now();
        this.status = ProbeStatus.HEALTHY;
    }

    public void markUnhealthy() {
        this.status = ProbeStatus.UNHEALTHY;
    }

    public Map<String, Object> toPublicMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("instanceId", instanceId);
        map.put("serviceName", serviceName);
        map.put("host", host);
        map.put("port", port);
        map.put("mcpBaseUrl", mcpBaseUrl);
        map.put("ssePath", ssePath);
        map.put("mcpPath", mcpPath);
        map.put("tags", tags);
        map.put("version", version);
        map.put("lastHeartbeat", lastHeartbeat.toString());
        map.put("status", status.name());
        // never expose probeToken to agents
        return map;
    }
}

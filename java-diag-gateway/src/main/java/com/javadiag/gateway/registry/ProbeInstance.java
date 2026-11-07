package com.javadiag.gateway.registry;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
    private final List<ProbeJvmApp> apps;
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
            List<ProbeJvmApp> apps,
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
        this.apps = List.copyOf(apps == null ? List.of() : apps);
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

    public List<ProbeJvmApp> apps() {
        return apps;
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

    public boolean hasMatchingApp(String appFilter) {
        if (appFilter == null || appFilter.isBlank()) {
            return true;
        }
        for (ProbeJvmApp app : apps) {
            if (app.matchesQuery(appFilter)) {
                return true;
            }
        }
        return false;
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
        if (!apps.isEmpty()) {
            map.put(
                    "apps",
                    apps.stream().map(ProbeJvmApp::toPublicMap).collect(Collectors.toList()));
        }
        // never expose probeToken to agents
        return map;
    }

    /** Admin console only: same as {@link #toPublicMap()} plus probe auth token when set. */
    public Map<String, Object> toAdminMap() {
        Map<String, Object> map = new LinkedHashMap<>(toPublicMap());
        map.put(
                "apps",
                apps.stream().map(ProbeJvmApp::toPublicMap).collect(Collectors.toList()));
        if (probeToken != null && !probeToken.isBlank()) {
            map.put("authToken", probeToken);
        }
        return map;
    }

    ProbeInstance withProbeToken(String newToken) {
        return new ProbeInstance(
                instanceId,
                serviceName,
                host,
                port,
                mcpBaseUrl,
                ssePath,
                mcpPath,
                newToken,
                tags,
                apps,
                version,
                lastHeartbeat,
                status);
    }

    ProbeInstance withApps(List<ProbeJvmApp> newApps) {
        return new ProbeInstance(
                instanceId,
                serviceName,
                host,
                port,
                mcpBaseUrl,
                ssePath,
                mcpPath,
                probeToken,
                tags,
                newApps,
                version,
                lastHeartbeat,
                status);
    }
}

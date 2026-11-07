package com.javadiag.gateway.registry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Built-in probe registry with TTL-based health.
 */
public final class InMemoryRegistry {

    private final ConcurrentHashMap<String, ProbeInstance> instances = new ConcurrentHashMap<>();
    private final Duration ttl;

    public InMemoryRegistry(Duration ttl) {
        this.ttl = ttl;
    }

    public ProbeInstance register(ProbeInstance instance) {
        ProbeInstance prev = instances.get(instance.instanceId());
        if (prev != null) {
            String token = instance.probeToken();
            if (token == null || token.isBlank()) {
                token = prev.probeToken();
            }
            List<ProbeJvmApp> apps = instance.apps().isEmpty() ? prev.apps() : instance.apps();
            ProbeInstance merged = instance;
            if (token != null && !token.equals(instance.probeToken())) {
                merged = merged.withProbeToken(token);
            }
            if (!apps.equals(instance.apps())) {
                merged = merged.withApps(apps);
            }
            instance = merged;
        }
        instances.put(instance.instanceId(), instance);
        return instance;
    }

    public boolean heartbeat(String instanceId) {
        return heartbeat(instanceId, null, null);
    }

    public boolean heartbeat(String instanceId, String probeToken) {
        return heartbeat(instanceId, probeToken, null);
    }

    public boolean heartbeat(String instanceId, String probeToken, List<ProbeJvmApp> apps) {
        ProbeInstance existing = instances.get(instanceId);
        if (existing == null) {
            return false;
        }
        existing.touchHeartbeat();
        ProbeInstance updated = existing;
        if (probeToken != null && !probeToken.isBlank()) {
            String current = existing.probeToken();
            if (current == null || current.isBlank() || !current.equals(probeToken)) {
                updated = updated.withProbeToken(probeToken);
            }
        }
        if (apps != null) {
            updated = updated.withApps(apps);
        }
        if (updated != existing) {
            instances.put(instanceId, updated);
        }
        return true;
    }

    public boolean deregister(String instanceId) {
        return instances.remove(instanceId) != null;
    }

    public Optional<ProbeInstance> get(String instanceId) {
        refresh();
        return Optional.ofNullable(instances.get(instanceId))
                .filter(i -> i.status() == ProbeStatus.HEALTHY);
    }

    public List<ProbeInstance> list(String serviceName, Map<String, String> tagEquals, boolean healthyOnly) {
        return list(serviceName, null, tagEquals, healthyOnly);
    }

    public List<ProbeInstance> list(
            String serviceName, String appFilter, Map<String, String> tagEquals, boolean healthyOnly) {
        refresh();
        List<ProbeInstance> out = new ArrayList<>();
        for (ProbeInstance instance : instances.values()) {
            if (healthyOnly && instance.status() != ProbeStatus.HEALTHY) {
                continue;
            }
            if (serviceName != null && !serviceName.isBlank()
                    && !instance.serviceName().equalsIgnoreCase(serviceName)) {
                continue;
            }
            if (appFilter != null && !appFilter.isBlank() && !instance.hasMatchingApp(appFilter)) {
                continue;
            }
            if (tagEquals != null && !tagEquals.isEmpty()) {
                boolean ok = true;
                for (Map.Entry<String, String> e : tagEquals.entrySet()) {
                    String actual = instance.tags().get(e.getKey());
                    if (actual == null || !actual.equals(e.getValue())) {
                        ok = false;
                        break;
                    }
                }
                if (!ok) {
                    continue;
                }
            }
            out.add(instance);
        }
        out.sort((a, b) -> a.instanceId().compareToIgnoreCase(b.instanceId()));
        return out;
    }

    public void refresh() {
        Instant cutoff = Instant.now().minus(ttl);
        for (ProbeInstance instance : instances.values()) {
            if (instance.lastHeartbeat().isBefore(cutoff)) {
                instance.markUnhealthy();
            }
        }
    }

    public int size() {
        return instances.size();
    }
}

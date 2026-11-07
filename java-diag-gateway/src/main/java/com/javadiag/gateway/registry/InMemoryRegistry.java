package com.javadiag.gateway.registry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
            if (token != null && !token.equals(instance.probeToken())) {
                instance = instance.withProbeToken(token);
            }
        }
        instances.put(instance.instanceId(), instance);
        return instance;
    }

    public boolean heartbeat(String instanceId) {
        return heartbeat(instanceId, null);
    }

    public boolean heartbeat(String instanceId, String probeToken) {
        ProbeInstance existing = instances.get(instanceId);
        if (existing == null) {
            return false;
        }
        existing.touchHeartbeat();
        if (probeToken != null && !probeToken.isBlank()) {
            String current = existing.probeToken();
            if (current == null || current.isBlank() || !current.equals(probeToken)) {
                instances.put(instanceId, existing.withProbeToken(probeToken));
            }
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

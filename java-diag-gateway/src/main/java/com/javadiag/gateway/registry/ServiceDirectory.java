package com.javadiag.gateway.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves business/JVM names against the latest {@link ProbeJvmApp} directory on each probe.
 */
public final class ServiceDirectory {

    private final InMemoryRegistry registry;

    public ServiceDirectory(InMemoryRegistry registry) {
        this.registry = registry;
    }

    public record Match(ProbeInstance instance, ProbeJvmApp app) {}

    public List<Match> resolve(String name, Map<String, String> tags, boolean healthyOnly) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        String query = name.trim();
        List<ProbeInstance> probes = registry.list(null, null, tags == null ? Map.of() : tags, healthyOnly);
        List<Match> matches = new ArrayList<>();
        for (ProbeInstance probe : probes) {
            for (ProbeJvmApp app : probe.apps()) {
                if (app.matchesQuery(query)) {
                    matches.add(new Match(probe, app));
                }
            }
        }
        matches.sort((a, b) -> {
            int c = a.instance().instanceId().compareToIgnoreCase(b.instance().instanceId());
            if (c != 0) {
                return c;
            }
            return Long.compare(a.app().pid(), b.app().pid());
        });
        return List.copyOf(matches);
    }

    public static Map<String, Object> toCandidateMap(Match match) {
        ProbeInstance probe = match.instance();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("instanceId", probe.instanceId());
        map.put("serviceName", probe.serviceName());
        map.put("host", probe.host());
        map.put("port", probe.port());
        map.put("mcpBaseUrl", probe.mcpBaseUrl());
        map.put("tags", probe.tags());
        map.put("app", match.app().toPublicMap());
        return map;
    }

    public static boolean exactNameMatch(ProbeJvmApp app, String name) {
        if (app == null || name == null) {
            return false;
        }
        return app.name().equalsIgnoreCase(name.trim());
    }

    public static String normalizeQuery(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}

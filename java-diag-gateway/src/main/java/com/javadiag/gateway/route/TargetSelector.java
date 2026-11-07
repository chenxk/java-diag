package com.javadiag.gateway.route;

import com.javadiag.gateway.registry.InMemoryRegistry;
import com.javadiag.gateway.registry.ProbeInstance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves {@code target} to zero, one, or many healthy probes.
 * Multi-match never auto-picks — Agent must choose {@code instanceId}
 * (advertise {@code host:port}) or {@code mcpBaseUrl}.
 */
public final class TargetSelector {

    public enum Outcome {
        NONE,
        SINGLE,
        MANY
    }

    public record Result(Outcome outcome, List<ProbeInstance> candidates) {
    }

    private final InMemoryRegistry registry;

    public TargetSelector(InMemoryRegistry registry) {
        this.registry = registry;
    }

    @SuppressWarnings("unchecked")
    public Result resolve(Map<String, Object> args) {
        Object rawTarget = args == null ? null : args.get("target");
        Map<String, Object> target = Map.of();
        if (rawTarget instanceof Map<?, ?> map) {
            Map<String, Object> tmp = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) {
                    tmp.put(e.getKey().toString(), e.getValue());
                }
            }
            target = tmp;
        }

        String instanceId = stringVal(target.get("instanceId"));
        if (instanceId != null) {
            return registry.get(instanceId)
                    .<Result>map(p -> new Result(Outcome.SINGLE, List.of(p)))
                    .orElseGet(() -> new Result(Outcome.NONE, List.of()));
        }

        String mcpBaseUrl = stringVal(target.get("mcpBaseUrl"));
        if (mcpBaseUrl != null) {
            String normalized = trimSlash(mcpBaseUrl);
            List<ProbeInstance> byUrl = registry.list(null, Map.of(), true).stream()
                    .filter(p -> normalized.equalsIgnoreCase(trimSlash(p.mcpBaseUrl())))
                    .toList();
            if (byUrl.isEmpty()) {
                return new Result(Outcome.NONE, List.of());
            }
            if (byUrl.size() == 1) {
                return new Result(Outcome.SINGLE, byUrl);
            }
            return new Result(Outcome.MANY, byUrl);
        }

        String serviceName = stringVal(target.get("serviceName"));
        Map<String, String> tags = castTags(target.get("tags"));
        List<ProbeInstance> matches = registry.list(serviceName, tags, true);
        if (matches.isEmpty()) {
            return new Result(Outcome.NONE, List.of());
        }
        if (matches.size() == 1) {
            return new Result(Outcome.SINGLE, matches);
        }
        return new Result(Outcome.MANY, matches);
    }

    private static String trimSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    public static Map<String, Object> stripTarget(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>(args);
        copy.remove("target");
        return copy;
    }

    private static String stringVal(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Map<String, String> castTags(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                out.put(e.getKey().toString(), e.getValue().toString());
            }
        }
        return out;
    }
}

package com.javadiag.gateway.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** One JVM workload reported by a probe heartbeat (public-safe fields only). */
public final class ProbeJvmApp {

    private final String name;
    private final long pid;
    private final String mainClass;
    private final String displayName;
    private final String commandLineSummary;
    private final Long startTimeEpochMs;
    private final List<Integer> ports;

    public ProbeJvmApp(
            String name,
            long pid,
            String mainClass,
            String displayName,
            String commandLineSummary,
            Long startTimeEpochMs,
            List<Integer> ports) {
        this.name = name == null ? "" : name;
        this.pid = pid;
        this.mainClass = mainClass == null ? "" : mainClass;
        this.displayName = displayName == null ? "" : displayName;
        this.commandLineSummary = commandLineSummary;
        this.startTimeEpochMs = startTimeEpochMs;
        this.ports = List.copyOf(ports == null ? List.of() : ports);
    }

    public String name() {
        return name;
    }

    public long pid() {
        return pid;
    }

    public String mainClass() {
        return mainClass;
    }

    public String displayName() {
        return displayName;
    }

    public String commandLineSummary() {
        return commandLineSummary;
    }

    public Long startTimeEpochMs() {
        return startTimeEpochMs;
    }

    public List<Integer> ports() {
        return ports;
    }

    public Map<String, Object> toPublicMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("pid", pid);
        map.put("mainClass", mainClass);
        map.put("displayName", displayName);
        if (commandLineSummary != null && !commandLineSummary.isBlank()) {
            map.put("commandLineSummary", commandLineSummary);
        }
        if (startTimeEpochMs != null) {
            map.put("startTimeEpochMs", startTimeEpochMs);
        }
        if (!ports.isEmpty()) {
            map.put("ports", ports);
        }
        return map;
    }

    public boolean matchesQuery(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(name, q)
                || containsIgnoreCase(mainClass, q)
                || containsIgnoreCase(displayName, q);
    }

    private static boolean containsIgnoreCase(String haystack, String needleLower) {
        if (haystack == null || haystack.isBlank()) {
            return false;
        }
        return haystack.toLowerCase(Locale.ROOT).contains(needleLower);
    }

    static List<ProbeJvmApp> parseList(Object raw, int maxItems) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        int limit = maxItems <= 0 ? 50 : maxItems;
        List<ProbeJvmApp> out = new ArrayList<>();
        for (Object item : list) {
            if (out.size() >= limit) {
                break;
            }
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            ProbeJvmApp app = fromMap(map);
            if (app != null) {
                out.add(app);
            }
        }
        return List.copyOf(out);
    }

    static ProbeJvmApp fromMap(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        Object pidRaw = map.get("pid");
        if (!(pidRaw instanceof Number n)) {
            return null;
        }
        long pid = n.longValue();
        if (pid <= 0) {
            return null;
        }
        String mainClass = str(map.get("mainClass"));
        if (mainClass == null) {
            mainClass = str(map.get("mainClassOrJar"));
        }
        String name = str(map.get("name"));
        if (name == null) {
            name = mainClass == null ? "" : mainClass;
        }
        String displayName = str(map.get("displayName"));
        if (displayName == null) {
            displayName = name;
        }
        String summary = str(map.get("commandLineSummary"));
        if (summary == null) {
            summary = str(map.get("commandLine"));
        }
        Long start = null;
        Object startRaw = map.get("startTimeEpochMs");
        if (startRaw instanceof Number sn) {
            start = sn.longValue();
        }
        List<Integer> ports = parsePorts(map.get("ports"));
        if (summary != null && summary.length() > 280) {
            summary = summary.substring(0, 280) + "…";
        }
        return new ProbeJvmApp(name, pid, mainClass == null ? "" : mainClass, displayName, summary, start, ports);
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> parsePorts(Object raw) {
        if (!(raw instanceof List<?> list)) {
            Object single = raw;
            if (single instanceof Number n) {
                return List.of(n.intValue());
            }
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Number n) {
                int p = n.intValue();
                if (p > 0 && p <= 65535 && !out.contains(p)) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String s = Objects.toString(value, "").trim();
        return s.isEmpty() ? null : s;
    }
}

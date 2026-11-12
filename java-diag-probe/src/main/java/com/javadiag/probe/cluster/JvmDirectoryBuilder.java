package com.javadiag.probe.cluster;

import com.javadiag.probe.process.AppNameMapper;
import com.javadiag.probe.process.JavaProcessInfo;
import com.javadiag.probe.process.JavaProcessService;
import com.javadiag.probe.process.JvmPortHints;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds registry-safe {@code apps[]} payloads from local JVM discovery. */
public final class JvmDirectoryBuilder {

    public static final int MAX_APPS = 50;
    public static final int COMMAND_LINE_SUMMARY_MAX = 240;

    private final JavaProcessService processService;
    private final AppNameMapper nameMapper;

    public JvmDirectoryBuilder(JavaProcessService processService, AppNameMapper nameMapper) {
        this.processService = processService;
        this.nameMapper = nameMapper == null ? AppNameMapper.empty() : nameMapper;
    }

    public List<Map<String, Object>> buildApps() {
        List<JavaProcessInfo> processes = new ArrayList<>(processService.listProcesses(null, null));
        return buildAppsFrom(processes);
    }

    List<Map<String, Object>> buildAppsFrom(List<JavaProcessInfo> processes) {
        List<JavaProcessInfo> sorted = new ArrayList<>(processes);
        sorted.sort(Comparator.comparingLong(JavaProcessInfo::pid));
        List<Map<String, Object>> apps = new ArrayList<>();
        for (JavaProcessInfo info : sorted) {
            if (apps.size() >= MAX_APPS) {
                break;
            }
            if (!JavaProcessService.isReportableJvm(info)) {
                continue;
            }
            Map<String, Object> app = toAppMap(info);
            Object name = app.get("name");
            if (name instanceof String s && "exe".equalsIgnoreCase(s)) {
                continue;
            }
            apps.add(app);
        }
        return apps;
    }

    private Map<String, Object> toAppMap(JavaProcessInfo info) {
        String main = info.mainClassOrJar() == null ? "" : info.mainClassOrJar();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("pid", info.pid());
        map.put("mainClass", main);
        map.put("name", nameMapper.resolveName(main));
        map.put("displayName", info.displayName() == null || info.displayName().isBlank() ? main : info.displayName());
        if (info.startTimeEpochMs() != null) {
            map.put("startTimeEpochMs", info.startTimeEpochMs());
        }
        String summary = summarizeCommandLine(info.commandLine());
        if (summary != null) {
            map.put("commandLineSummary", summary);
        }
        List<Integer> ports = JvmPortHints.fromCommandLine(info.commandLine());
        if (!ports.isEmpty()) {
            map.put("ports", ports);
        }
        return map;
    }

    static String summarizeCommandLine(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return null;
        }
        String trimmed = commandLine.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= COMMAND_LINE_SUMMARY_MAX) {
            return trimmed;
        }
        return trimmed.substring(0, COMMAND_LINE_SUMMARY_MAX) + "…";
    }
}

package com.javadiag.probe.process;

import java.util.LinkedHashMap;
import java.util.Map;

public final class JavaProcessInfo {

    private final long pid;
    private final String mainClassOrJar;
    private final String displayName;
    private final String user;
    private final Long startTimeEpochMs;
    private final Long rssKb;
    private final String commandLine;

    public JavaProcessInfo(
            long pid,
            String mainClassOrJar,
            String displayName,
            String user,
            Long startTimeEpochMs,
            Long rssKb,
            String commandLine) {
        this.pid = pid;
        this.mainClassOrJar = mainClassOrJar;
        this.displayName = displayName;
        this.user = user;
        this.startTimeEpochMs = startTimeEpochMs;
        this.rssKb = rssKb;
        this.commandLine = commandLine;
    }

    public long pid() {
        return pid;
    }

    public String mainClassOrJar() {
        return mainClassOrJar;
    }

    public String displayName() {
        return displayName;
    }

    public String user() {
        return user;
    }

    public Long startTimeEpochMs() {
        return startTimeEpochMs;
    }

    public Long rssKb() {
        return rssKb;
    }

    public String commandLine() {
        return commandLine;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("pid", pid);
        map.put("mainClass", mainClassOrJar);
        map.put("displayName", displayName);
        map.put("user", user);
        map.put("startTimeEpochMs", startTimeEpochMs);
        map.put("rssKb", rssKb);
        map.put("commandLine", commandLine);
        return map;
    }
}

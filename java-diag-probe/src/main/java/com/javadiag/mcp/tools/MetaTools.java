package com.javadiag.mcp.tools;

import com.javadiag.mcp.process.JavaProcessInfo;
import com.javadiag.mcp.tools.ToolSupport.ToolResult;

import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class MetaTools {

    public static final String PING = "diag.ping";
    public static final String LIST_PROCESSES = "diag.list_processes";
    public static final String RESOLVE_JAVA_PID = "diag.resolve_java_pid";

    private final ToolSupport support;

    public MetaTools(ToolSupport support) {
        this.support = support;
    }

    public ToolEnvelopeHolder ping(Map<String, Object> args) {
        return wrap(PING, args, a -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("version", support.config().version());
            data.put("host", safeHost());
            data.put("pid", ProcessHandle.current().pid());
            data.put("javaHome", System.getProperty("java.home"));
            data.put("javaVersion", System.getProperty("java.version"));
            data.put("instanceId", support.config().instanceId());
            data.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
            return ToolResult.of(data);
        });
    }

    public ToolEnvelopeHolder listProcesses(Map<String, Object> args) {
        return wrap(LIST_PROCESSES, args, a -> {
            String keyword = support.optString(a, "keyword");
            String user = support.optString(a, "user");
            List<JavaProcessInfo> list = support.processService().listProcesses(keyword, user);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("count", list.size());
            data.put("processes", list.stream().map(JavaProcessInfo::toMap).collect(Collectors.toList()));
            return ToolResult.of(data);
        });
    }

    public ToolEnvelopeHolder resolveJavaPid(Map<String, Object> args) {
        return wrap(RESOLVE_JAVA_PID, args, a -> {
            String mainClass = support.optString(a, "mainClass");
            String jar = support.optString(a, "jar");
            Integer port = null;
            if (a.get("port") != null) {
                port = support.optInt(a, "port", 0, 1, 65535);
            }
            List<JavaProcessInfo> list = support.processService().resolve(mainClass, jar, port);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("count", list.size());
            data.put("matches", list.stream().map(JavaProcessInfo::toMap).collect(Collectors.toList()));
            return ToolResult.of(data);
        });
    }

    private ToolEnvelopeHolder wrap(
            String tool,
            Map<String, Object> args,
            java.util.function.Function<Map<String, Object>, ToolResult> handler) {
        return new ToolEnvelopeHolder(support.invoke(tool, args, handler));
    }

    private static String safeHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "unknown";
        }
    }
}

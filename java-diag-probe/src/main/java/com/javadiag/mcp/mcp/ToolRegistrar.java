package com.javadiag.mcp.mcp;

import com.javadiag.mcp.model.ToolEnvelope;
import com.javadiag.mcp.platform.JsonCodec;
import com.javadiag.mcp.tools.JvmTools;
import com.javadiag.mcp.tools.LogTools;
import com.javadiag.mcp.tools.MetaTools;
import com.javadiag.mcp.tools.ToolEnvelopeHolder;
import com.javadiag.mcp.tools.ToolSupport;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Registers all P0 diag.* tools on the MCP sync server.
 */
public final class ToolRegistrar {

    private final ToolSupport support;
    private final MetaTools metaTools;
    private final LogTools logTools;
    private final JvmTools jvmTools;
    private final JsonCodec codec;

    public ToolRegistrar(ToolSupport support) {
        this.support = support;
        this.metaTools = new MetaTools(support);
        this.logTools = new LogTools(support);
        this.jvmTools = new JvmTools(support);
        this.codec = support.jsonCodec();
    }

    public List<String> toolNames() {
        List<String> names = new ArrayList<>();
        for (McpServerFeatures.SyncToolSpecification spec : specifications()) {
            names.add(spec.tool().name());
        }
        return names;
    }

    public void registerAll(McpSyncServer server) {
        for (McpServerFeatures.SyncToolSpecification spec : specifications()) {
            server.addTool(spec);
        }
    }

    public List<McpServerFeatures.SyncToolSpecification> specifications() {
        List<McpServerFeatures.SyncToolSpecification> specs = new ArrayList<>();

        specs.add(tool(MetaTools.PING, "Health check: version, host, pid, javaHome",
                SchemaUtil.emptyObject(), a -> metaTools.ping(a)));

        Map<String, Object> listProps = new LinkedHashMap<>();
        listProps.put("keyword", SchemaUtil.stringProp("Optional filter on main class / cmdline"));
        listProps.put("user", SchemaUtil.stringProp("Optional numeric UID filter"));
        specs.add(tool(MetaTools.LIST_PROCESSES, "List local Java processes",
                SchemaUtil.object(listProps, List.of()), a -> metaTools.listProcesses(a)));

        Map<String, Object> resolveProps = new LinkedHashMap<>();
        resolveProps.put("mainClass", SchemaUtil.stringProp("Main class substring"));
        resolveProps.put("jar", SchemaUtil.stringProp("Jar path/name substring"));
        resolveProps.put("port", SchemaUtil.integerProp("Listen port"));
        specs.add(tool(MetaTools.RESOLVE_JAVA_PID, "Resolve Java pid by mainClass/jar/port",
                SchemaUtil.object(resolveProps, List.of()), a -> metaTools.resolveJavaPid(a)));

        Map<String, Object> logPathsProps = new LinkedHashMap<>();
        logPathsProps.put("pid", SchemaUtil.integerProp("Optional pid context"));
        logPathsProps.put("app", SchemaUtil.stringProp("Optional app name filter"));
        specs.add(tool(LogTools.LOG_PATHS, "Infer candidate log paths under allowed dirs",
                SchemaUtil.object(logPathsProps, List.of()), a -> logTools.logPaths(a)));

        Map<String, Object> tailProps = new LinkedHashMap<>();
        tailProps.put("path", SchemaUtil.stringProp("Absolute log path under whitelist"));
        tailProps.put("lines", SchemaUtil.integerProp("Lines to return (1-500)"));
        tailProps.put("follow", SchemaUtil.booleanProp("Must be false in P0"));
        specs.add(tool(LogTools.TAIL_LOG, "Tail a whitelisted log file",
                SchemaUtil.object(tailProps, List.of("path")), a -> logTools.tailLog(a)));

        Map<String, Object> grepProps = new LinkedHashMap<>();
        grepProps.put("path", SchemaUtil.stringProp("Absolute log path under whitelist"));
        grepProps.put("pattern", SchemaUtil.stringProp("Java regex pattern"));
        grepProps.put("since", SchemaUtil.stringProp("Optional since hint"));
        grepProps.put("maxMatches", SchemaUtil.integerProp("Max matches (1-200)"));
        specs.add(tool(LogTools.GREP_LOG, "Grep a whitelisted log file with timeout/truncation",
                SchemaUtil.object(grepProps, List.of("path", "pattern")), a -> logTools.grepLog(a)));

        Map<String, Object> clusterProps = new LinkedHashMap<>();
        clusterProps.put("path", SchemaUtil.stringProp("Absolute log path under whitelist"));
        clusterProps.put("window", SchemaUtil.stringProp("Time window e.g. 15m (ignored if since set)"));
        clusterProps.put("since", SchemaUtil.stringProp("Optional absolute cutoff time (overrides window)"));
        specs.add(tool(LogTools.CLUSTER_ERRORS, "Cluster exception class names in a log window",
                SchemaUtil.object(clusterProps, List.of("path")), a -> logTools.clusterErrors(a)));

        Map<String, Object> pidProps = new LinkedHashMap<>();
        pidProps.put("pid", SchemaUtil.integerProp("Target Java process id"));
        specs.add(tool(JvmTools.RUNTIME_SUMMARY, "JVM runtime summary via jcmd VM.summary",
                SchemaUtil.object(pidProps, List.of("pid")), a -> jvmTools.runtimeSummary(a)));

        Map<String, Object> jstatProps = new LinkedHashMap<>(pidProps);
        jstatProps.put("option", SchemaUtil.stringProp("jstat option e.g. gc, gccapacity"));
        jstatProps.put("interval", SchemaUtil.integerProp("Optional sample interval ms"));
        jstatProps.put("count", SchemaUtil.integerProp("Optional sample count (max 20)"));
        specs.add(tool(JvmTools.JSTAT, "Run jstat against a Java pid",
                SchemaUtil.object(jstatProps, List.of("pid")), a -> jvmTools.jstat(a)));

        Map<String, Object> dumpProps = new LinkedHashMap<>(pidProps);
        dumpProps.put("maxBytes", SchemaUtil.integerProp("Max dump bytes to return"));
        specs.add(tool(JvmTools.THREAD_DUMP, "Thread dump via jcmd Thread.print or jstack",
                SchemaUtil.object(dumpProps, List.of("pid")), a -> jvmTools.threadDump(a)));

        specs.add(tool(JvmTools.VM_FLAGS, "Read-only VM flags via jcmd VM.flags",
                SchemaUtil.object(new LinkedHashMap<>(pidProps), List.of("pid")), a -> jvmTools.vmFlags(a)));

        Map<String, Object> histoProps = new LinkedHashMap<>(pidProps);
        histoProps.put("live", SchemaUtil.booleanProp("If true, may force full GC (disabled by default)"));
        specs.add(tool(JvmTools.GC_HISTO, "Class histogram via jcmd GC.class_histogram (default no full GC)",
                SchemaUtil.object(histoProps, List.of("pid")), a -> jvmTools.gcHisto(a)));

        Map<String, Object> heapProps = new LinkedHashMap<>(pidProps);
        heapProps.put("path", SchemaUtil.stringProp("Output .hprof path under allowedLogDirs"));
        specs.add(tool(JvmTools.HEAP_DUMP, "Write heap dump via jcmd GC.heap_dump (dangerous; gated)",
                SchemaUtil.object(heapProps, List.of("pid", "path")), a -> jvmTools.heapDump(a)));

        Map<String, Object> cpuProps = new LinkedHashMap<>(pidProps);
        cpuProps.put("pauseMs", SchemaUtil.integerProp("Pause between thread snapshots (100-5000 ms)"));
        specs.add(tool(JvmTools.CPU_SAMPLE, "Two thread snapshots with delta summary (no async-profiler)",
                SchemaUtil.object(cpuProps, List.of("pid")), a -> jvmTools.cpuSample(a)));

        specs.add(tool(JvmTools.VM_INFO, "Read-only VM.info + Compiler.codeheap_analytics",
                SchemaUtil.object(new LinkedHashMap<>(pidProps), List.of("pid")), a -> jvmTools.vmInfo(a)));

        return specs;
    }

    private McpServerFeatures.SyncToolSpecification tool(
            String name,
            String description,
            McpSchema.JsonSchema schema,
            Function<Map<String, Object>, ToolEnvelopeHolder> handler) {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(schema)
                .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    Map<String, Object> args = request.arguments() == null ? Map.of() : request.arguments();
                    ToolEnvelope envelope = handler.apply(args).envelope();
                    String json = codec.toJson(envelope.toMap());
                    return McpSchema.CallToolResult.builder()
                            .addTextContent(json)
                            .structuredContent(envelope.toMap())
                            .isError(!envelope.ok())
                            .build();
                })
                .build();
    }
}

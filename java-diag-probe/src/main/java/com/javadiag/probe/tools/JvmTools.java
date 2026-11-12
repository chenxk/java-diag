package com.javadiag.probe.tools;

import com.javadiag.probe.model.ErrorCode;
import com.javadiag.probe.platform.CommandResult;
import com.javadiag.probe.platform.DiagException;
import com.javadiag.probe.platform.JdkToolchain;
import com.javadiag.probe.platform.OsPlatform;
import com.javadiag.probe.platform.ProcessRunner;
import com.javadiag.probe.tools.ToolSupport.ToolResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class JvmTools {

    public static final String RUNTIME_SUMMARY = "diag.runtime_summary";
    public static final String JSTAT = "diag.jstat";
    public static final String THREAD_DUMP = "diag.thread_dump";
    public static final String VM_FLAGS = "diag.vm_flags";
    public static final String GC_HISTO = "diag.gc_histo";
    public static final String HEAP_DUMP = "diag.heap_dump";
    public static final String CPU_SAMPLE = "diag.cpu_sample";
    public static final String VM_INFO = "diag.vm_info";

    private static final Set<String> JSTAT_OPTIONS = Set.of(
            "gc", "gccapacity", "gccause", "gcnew", "gcold", "gcutil", "class", "compiler");

    private final ToolSupport support;

    public JvmTools(ToolSupport support) {
        this.support = support;
    }

    public ToolEnvelopeHolder runtimeSummary(Map<String, Object> args) {
        return wrap(RUNTIME_SUMMARY, args, a -> {
            long pid = support.requirePid(a);
            JdkToolchain.Toolchain tc = support.jdkToolchain().resolveForPid(pid);

            List<String> cmd = List.of(
                    tc.jcmd().toString(),
                    Long.toString(pid),
                    "VM.summary");
            CommandResult result = support.processRunner().run(cmd, support.config().toolTimeoutMs());
            Map<String, Object> parsed = enrichVmSummary(parseVmSummary(result.stdout()), result.stdout());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("pid", pid);
            data.put("javaHome", tc.javaHome().toString());
            data.put("jdkMajor", tc.majorVersion());
            data.put("summary", parsed);
            data.put("raw", truncate(result.stdout(), support.config().maxOutputBytes()));
            return ToolResult.of(data, result.truncated());
        });
    }

    public ToolEnvelopeHolder jstat(Map<String, Object> args) {
        return wrap(JSTAT, args, a -> {
            long pid = support.requirePid(a);
            String option = support.optString(a, "option");
            if (option == null || option.isBlank()) {
                option = "gc";
            }
            option = option.toLowerCase(Locale.ROOT);
            if (option.startsWith("-")) {
                option = option.substring(1);
            }
            if (!JSTAT_OPTIONS.contains(option)) {
                throw new DiagException(
                        ErrorCode.INVALID_ARGUMENT,
                        "unsupported jstat option: " + option + "; allowed=" + JSTAT_OPTIONS);
            }
            int interval = support.optInt(a, "interval", 0, 0, 10_000);
            int count = support.optInt(a, "count", 1, 1, 20);

            JdkToolchain.Toolchain tc = support.jdkToolchain().resolveForPid(pid);
            if (!OsPlatform.isRunnable(tc.jstat())) {
                throw new DiagException(ErrorCode.INTERNAL, "jstat not executable: " + tc.jstat());
            }

            List<String> cmd = new ArrayList<>();
            cmd.add(tc.jstat().toString());
            cmd.add("-" + option);
            cmd.add(Long.toString(pid));
            if (interval > 0) {
                cmd.add(Integer.toString(interval));
                cmd.add(Integer.toString(count));
            }

            CommandResult result = support.processRunner().run(cmd, support.config().toolTimeoutMs());
            String output = truncate(result.combinedOutput(), support.config().maxOutputBytes());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("pid", pid);
            data.put("option", option);
            data.put("exitCode", result.exitCode());
            data.put("output", output);
            data.put("summary", JvmOutputParser.parseJstatGcUtil(output, option));
            return ToolResult.of(data, result.truncated());
        });
    }

    public ToolEnvelopeHolder threadDump(Map<String, Object> args) {
        return wrap(THREAD_DUMP, args, a -> {
            long pid = support.requirePid(a);
            int maxBytes = support.optInt(a, "maxBytes", support.config().maxOutputBytes(), 1024, support.config().maxOutputBytes());

            JdkToolchain.Toolchain tc = support.jdkToolchain().resolveForPid(pid);
            CommandResult result;
            String method;
            if (support.jdkToolchain().preferJcmd(tc)) {
                method = "jcmd Thread.print";
                result = support.processRunner().run(
                        List.of(tc.jcmd().toString(), Long.toString(pid), "Thread.print"),
                        support.config().dumpTimeoutMs());
            } else if (OsPlatform.isRunnable(tc.jstack())) {
                method = "jstack";
                result = support.processRunner().run(
                        List.of(tc.jstack().toString(), Long.toString(pid)),
                        support.config().dumpTimeoutMs());
            } else {
                throw new DiagException(ErrorCode.INTERNAL, "neither jcmd nor jstack available");
            }

            String output = result.combinedOutput();
            boolean truncated = result.truncated();
            if (output.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBytes) {
                output = ProcessRunner.truncateUtf8(output, maxBytes);
                truncated = true;
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("pid", pid);
            data.put("method", method);
            data.put("jdkMajor", tc.majorVersion());
            data.put("dump", output);
            data.put("summary", JvmOutputParser.parseThreadDumpSummary(output));
            return ToolResult.of(data, truncated);
        });
    }

    public ToolEnvelopeHolder vmFlags(Map<String, Object> args) {
        return wrap(VM_FLAGS, args, a -> {
            long pid = support.requirePid(a);
            JdkToolchain.Toolchain tc = support.jdkToolchain().resolveForPid(pid);
            CommandResult result = support.processRunner().run(
                    List.of(tc.jcmd().toString(), Long.toString(pid), "VM.flags"),
                    support.config().toolTimeoutMs());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("pid", pid);
            data.put("flags", truncate(result.stdout(), support.config().maxOutputBytes()));
            data.put("exitCode", result.exitCode());
            return ToolResult.of(data, result.truncated());
        });
    }

    public ToolEnvelopeHolder gcHisto(Map<String, Object> args) {
        return wrap(GC_HISTO, args, a -> {
            long pid = support.requirePid(a);
            boolean live = support.optBoolean(a, "live", false);
            if (live && !support.config().dangerousToolsEnabled()) {
                throw new DiagException(
                        ErrorCode.TOOL_DISABLED,
                        "live=true forces a full GC; enable JAVA_DIAG_DANGEROUS_TOOLS_ENABLED=true to allow");
            }

            JdkToolchain.Toolchain tc = support.jdkToolchain().resolveForPid(pid);
            // live=true: force a GC first (gated by dangerousToolsEnabled), then histogram.
            // Previous code passed -all=false which is the jcmd default and did not force GC.
            if (live) {
                CommandResult gcRun = support.processRunner().run(
                        List.of(tc.jcmd().toString(), Long.toString(pid), "GC.run"),
                        support.config().dumpTimeoutMs());
                if (gcRun.exitCode() != 0) {
                    throw new DiagException(
                            ErrorCode.INTERNAL,
                            "GC.run failed before live histogram: " + gcRun.combinedOutput());
                }
            }

            CommandResult result = support.processRunner().run(
                    List.of(tc.jcmd().toString(), Long.toString(pid), "GC.class_histogram"),
                    support.config().dumpTimeoutMs());
            String output = truncate(result.combinedOutput(), support.config().maxOutputBytes());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("pid", pid);
            data.put("live", live);
            data.put("histogram", output);
            data.put("exitCode", result.exitCode());
            data.put("note", live ? "forced GC.run before histogram" : "default histo without forced full GC");
            return ToolResult.of(data, result.truncated());
        });
    }

    public ToolEnvelopeHolder heapDump(Map<String, Object> args) {
        return wrap(HEAP_DUMP, args, a -> {
            if (!support.config().dangerousToolsEnabled()) {
                throw new DiagException(
                        ErrorCode.TOOL_DISABLED,
                        "heap dump disabled; enable JAVA_DIAG_DANGEROUS_TOOLS_ENABLED=true");
            }
            long pid = support.requirePid(a);
            String pathArg = support.optString(a, "path");
            if (pathArg == null || pathArg.isBlank()) {
                throw new DiagException(ErrorCode.INVALID_ARGUMENT, "path is required (under allowedLogDirs)");
            }
            Path out = support.pathGuard().resolveAllowed(pathArg);
            if (out.getFileName() == null || !out.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".hprof")) {
                throw new DiagException(ErrorCode.INVALID_ARGUMENT, "path must end with .hprof");
            }
            Path parent = out.getParent();
            if (parent != null) {
                try {
                    Files.createDirectories(parent);
                } catch (java.io.IOException ex) {
                    throw new DiagException(ErrorCode.INTERNAL, "cannot create output dir: " + ex.getMessage(), ex);
                }
            }
            JdkToolchain.Toolchain tc = support.jdkToolchain().resolveForPid(pid);
            String abs = out.toAbsolutePath().normalize().toString();
            CommandResult result = support.processRunner().run(
                    List.of(tc.jcmd().toString(), Long.toString(pid), "GC.heap_dump", abs),
                    support.config().dumpTimeoutMs());
            if (result.exitCode() != 0) {
                throw new DiagException(
                        ErrorCode.INTERNAL,
                        "GC.heap_dump failed: " + result.combinedOutput());
            }
            long size = 0;
            try {
                size = Files.size(out);
            } catch (java.io.IOException ignored) {
                // best effort
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("pid", pid);
            data.put("path", abs);
            data.put("sizeBytes", size);
            data.put("exitCode", result.exitCode());
            data.put("note", "heap file written on probe host; not embedded in MCP response");
            return ToolResult.of(data, result.truncated());
        });
    }

    public ToolEnvelopeHolder cpuSample(Map<String, Object> args) {
        return wrap(CPU_SAMPLE, args, a -> {
            long pid = support.requirePid(a);
            int pauseMs = support.optInt(a, "pauseMs", 500, 100, 5_000);
            String first = captureThreadDump(pid);
            try {
                Thread.sleep(pauseMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new DiagException(ErrorCode.INTERNAL, "cpu_sample interrupted");
            }
            String second = captureThreadDump(pid);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("pid", pid);
            data.put("pauseMs", pauseMs);
            data.put("beforeSummary", JvmOutputParser.parseThreadDumpSummary(first));
            data.put("afterSummary", JvmOutputParser.parseThreadDumpSummary(second));
            data.put("delta", JvmOutputParser.compareThreadDumps(first, second));
            data.put("beforeSnippet", truncate(first, Math.min(8192, support.config().maxOutputBytes())));
            data.put("afterSnippet", truncate(second, Math.min(8192, support.config().maxOutputBytes())));
            return ToolResult.of(data, false);
        });
    }

    public ToolEnvelopeHolder vmInfo(Map<String, Object> args) {
        return wrap(VM_INFO, args, a -> {
            long pid = support.requirePid(a);
            JdkToolchain.Toolchain tc = support.jdkToolchain().resolveForPid(pid);
            CommandResult info = support.processRunner().run(
                    List.of(tc.jcmd().toString(), Long.toString(pid), "VM.info"),
                    support.config().toolTimeoutMs());
            CommandResult codeHeap = support.processRunner().run(
                    List.of(tc.jcmd().toString(), Long.toString(pid), "Compiler.codeheap_analytics"),
                    support.config().toolTimeoutMs());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("pid", pid);
            data.put("vmInfo", truncate(info.stdout(), support.config().maxOutputBytes()));
            data.put("codeHeapAnalytics", truncate(codeHeap.stdout(), support.config().maxOutputBytes()));
            data.put("exitCodes", Map.of("vmInfo", info.exitCode(), "codeHeap", codeHeap.exitCode()));
            return ToolResult.of(data, info.truncated() || codeHeap.truncated());
        });
    }

    private String captureThreadDump(long pid) {
        JdkToolchain.Toolchain tc = support.jdkToolchain().resolveForPid(pid);
        CommandResult result;
        if (support.jdkToolchain().preferJcmd(tc)) {
            result = support.processRunner().run(
                    List.of(tc.jcmd().toString(), Long.toString(pid), "Thread.print"),
                    support.config().dumpTimeoutMs());
        } else if (OsPlatform.isRunnable(tc.jstack())) {
            result = support.processRunner().run(
                    List.of(tc.jstack().toString(), Long.toString(pid)),
                    support.config().dumpTimeoutMs());
        } else {
            throw new DiagException(ErrorCode.INTERNAL, "neither jcmd nor jstack available");
        }
        if (result.exitCode() != 0) {
            throw new DiagException(ErrorCode.INTERNAL, "thread capture failed: " + result.combinedOutput());
        }
        return result.combinedOutput();
    }

    private static Map<String, Object> enrichVmSummary(Map<String, Object> base, String raw) {
        return JvmOutputParser.enrichVmSummary(base, raw);
    }

    private static Map<String, Object> parseVmSummary(String raw) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return map;
        }
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.toLowerCase(Locale.ROOT).contains("uptime")) {
                map.putIfAbsent("uptimeLine", trimmed);
            }
            if (trimmed.toLowerCase(Locale.ROOT).contains("thread")) {
                map.putIfAbsent("threadLine", trimmed);
            }
            if (trimmed.toLowerCase(Locale.ROOT).contains("heap")) {
                map.putIfAbsent("heapLine", trimmed);
            }
            if (trimmed.toLowerCase(Locale.ROOT).contains("metaspace")
                    || trimmed.toLowerCase(Locale.ROOT).contains("meta space")) {
                map.putIfAbsent("metaspaceLine", trimmed);
            }
        }
        map.put("lineCount", raw.split("\\R").length);
        return map;
    }

    private static String truncate(String text, int maxBytes) {
        return ProcessRunner.truncateUtf8(text == null ? "" : text, maxBytes);
    }

    private ToolEnvelopeHolder wrap(
            String tool,
            Map<String, Object> args,
            java.util.function.Function<Map<String, Object>, ToolResult> handler) {
        return new ToolEnvelopeHolder(support.invoke(tool, args, handler));
    }
}

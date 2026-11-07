package com.javadiag.mcp.tools;

import com.javadiag.mcp.model.ErrorCode;
import com.javadiag.mcp.platform.CommandResult;
import com.javadiag.mcp.platform.DiagException;
import com.javadiag.mcp.platform.JdkToolchain;
import com.javadiag.mcp.platform.ProcessRunner;
import com.javadiag.mcp.tools.ToolSupport.ToolResult;

import java.nio.file.Files;
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
            Map<String, Object> parsed = parseVmSummary(result.stdout());

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
            if (!Files.isExecutable(tc.jstat())) {
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
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("pid", pid);
            data.put("option", option);
            data.put("exitCode", result.exitCode());
            data.put("output", truncate(result.combinedOutput(), support.config().maxOutputBytes()));
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
            } else if (Files.isExecutable(tc.jstack())) {
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
            List<String> cmd = new ArrayList<>();
            cmd.add(tc.jcmd().toString());
            cmd.add(Long.toString(pid));
            cmd.add("GC.class_histogram");
            if (live) {
                cmd.add("-all=false");
            }

            CommandResult result = support.processRunner().run(cmd, support.config().dumpTimeoutMs());
            String output = truncate(result.combinedOutput(), support.config().maxOutputBytes());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("pid", pid);
            data.put("live", live);
            data.put("histogram", output);
            data.put("exitCode", result.exitCode());
            data.put("note", live ? "live histogram may force full GC" : "default histo without forced full GC");
            return ToolResult.of(data, result.truncated());
        });
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

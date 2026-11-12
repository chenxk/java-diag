package com.javadiag.probe.tools;

import com.javadiag.probe.model.ErrorCode;
import com.javadiag.probe.platform.DiagException;
import com.javadiag.probe.platform.LogTimestampParser;
import com.javadiag.probe.process.JavaProcessInfo;
import com.javadiag.probe.tools.ToolSupport.ToolResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class LogTools {

    public static final String LOG_PATHS = "diag.log_paths";
    public static final String TAIL_LOG = "diag.tail_log";
    public static final String GREP_LOG = "diag.grep_log";
    public static final String CLUSTER_ERRORS = "diag.cluster_errors";

    private static final Pattern EXCEPTION_PATTERN = Pattern.compile(
            "([a-zA-Z_][\\w.$]*(?:Exception|Error|Throwable))(?::\\s*(.*))?");

    private final ToolSupport support;

    public LogTools(ToolSupport support) {
        this.support = support;
    }

    public ToolEnvelopeHolder logPaths(Map<String, Object> args) {
        return wrap(LOG_PATHS, args, a -> {
            String app = support.optString(a, "app");
            Long pid = null;
            if (a.get("pid") != null) {
                pid = support.requirePid(a);
            }

            List<Map<String, Object>> candidates = new ArrayList<>();
            for (Path dir : support.pathGuard().allowedDirs()) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try (var stream = Files.list(dir)) {
                    stream.filter(Files::isRegularFile)
                            .filter(p -> looksLikeLog(p.getFileName().toString()))
                            .filter(p -> app == null || app.isBlank()
                                    || p.getFileName().toString().toLowerCase(Locale.ROOT)
                                    .contains(app.toLowerCase(Locale.ROOT)))
                            .limit(50)
                            .forEach(p -> {
                                Map<String, Object> item = new LinkedHashMap<>();
                                item.put("path", p.toString());
                                try {
                                    item.put("sizeBytes", Files.size(p));
                                    item.put("lastModifiedEpochMs", Files.getLastModifiedTime(p).toMillis());
                                } catch (IOException ex) {
                                    item.put("sizeBytes", null);
                                }
                                candidates.add(item);
                            });
                } catch (IOException ignored) {
                    // skip unreadable dirs
                }
            }

            // Non-recursive: also check common subdirs one level under allowed roots
            for (Path dir : support.pathGuard().allowedDirs()) {
                Path logs = dir.resolve("logs");
                if (Files.isDirectory(logs) && support.pathGuard().isUnderAllowed(logs)) {
                    addLogsFromDir(logs, app, candidates, 50);
                }
            }

            List<Map<String, Object>> inferred = new ArrayList<>();
            if (pid != null) {
                support.processService().lookup(pid).ifPresent(info -> inferred.addAll(inferLogPathsFromCmdline(info)));
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("pid", pid);
            data.put("app", app);
            data.put("paths", candidates);
            data.put("inferredFromPid", inferred);
            data.put("note", "Scan allowedLogDirs (+ logs/); inferredFromPid from JVM cmdline/system props");
            return ToolResult.of(data);
        });
    }

    public ToolEnvelopeHolder tailLog(Map<String, Object> args) {
        return wrap(TAIL_LOG, args, a -> {
            String pathArg = support.optString(a, "path");
            int lines = support.optInt(a, "lines", 100, 1, 500);
            boolean follow = support.optBoolean(a, "follow", false);
            if (follow) {
                throw new DiagException(ErrorCode.INVALID_ARGUMENT, "follow=true is not supported in P0");
            }
            Path path = support.pathGuard().resolveAllowed(pathArg);
            if (!Files.isRegularFile(path)) {
                throw new DiagException(ErrorCode.INVALID_ARGUMENT, "path is not a regular file: " + path);
            }

            List<String> tail = readLastLines(path, lines, support.config().maxOutputBytes());
            boolean truncated = false;
            String joined = String.join("\n", tail);
            byte[] bytes = joined.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > support.config().maxOutputBytes()) {
                truncated = true;
                joined = com.javadiag.probe.platform.ProcessRunner.truncateUtf8(
                        joined, support.config().maxOutputBytes());
                tail = List.of(joined.split("\n", -1));
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", path.toString());
            data.put("lines", tail.size());
            data.put("content", tail);
            return ToolResult.of(data, truncated);
        });
    }

    public ToolEnvelopeHolder grepLog(Map<String, Object> args) {
        return wrap(GREP_LOG, args, a -> {
            String pathArg = support.optString(a, "path");
            String patternText = support.optString(a, "pattern");
            if (patternText == null || patternText.isBlank()) {
                throw new DiagException(ErrorCode.INVALID_ARGUMENT, "pattern is required");
            }
            int maxMatches = support.optInt(a, "maxMatches", 50, 1, 200);
            String since = support.optString(a, "since");
            Instant sinceInstant = LogTimestampParser.parseSince(since);

            Path path = support.pathGuard().resolveAllowed(pathArg);
            if (!Files.isRegularFile(path)) {
                throw new DiagException(ErrorCode.INVALID_ARGUMENT, "path is not a regular file: " + path);
            }

            Pattern pattern;
            try {
                pattern = Pattern.compile(patternText);
            } catch (Exception ex) {
                throw new DiagException(ErrorCode.INVALID_ARGUMENT, "invalid regex pattern: " + ex.getMessage());
            }

            long deadline = System.currentTimeMillis() + Math.min(10_000L, support.config().toolTimeoutMs());
            List<Map<String, Object>> matches = new ArrayList<>();
            boolean truncated = false;
            int lineNo = 0;
            int bytesRead = 0;
            int maxBytes = support.config().maxOutputBytes();

            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineNo++;
                    if (System.currentTimeMillis() > deadline) {
                        throw new DiagException(ErrorCode.TIMEOUT, "grep_log timed out after 10s");
                    }
                    bytesRead += line.length() + 1;
                    if (bytesRead > maxBytes * 8L) {
                        // safety bound on scanned input volume
                        truncated = true;
                        break;
                    }
                    if (sinceInstant != null) {
                        Instant lineTs = LogTimestampParser.parseLineTimestamp(line);
                        if (lineTs != null && lineTs.isBefore(sinceInstant)) {
                            continue;
                        }
                    }
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        Map<String, Object> hit = new LinkedHashMap<>();
                        hit.put("line", lineNo);
                        hit.put("text", truncateLine(line, 2000));
                        matches.add(hit);
                        if (matches.size() >= maxMatches) {
                            truncated = true;
                            break;
                        }
                    }
                }
            } catch (DiagException ex) {
                throw ex;
            } catch (IOException ex) {
                throw new DiagException(ErrorCode.INTERNAL, "failed to read log: " + ex.getMessage(), ex);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", path.toString());
            data.put("pattern", patternText);
            data.put("since", since);
            data.put("sinceApplied", sinceInstant != null);
            data.put("matchCount", matches.size());
            data.put("matches", matches);
            return ToolResult.of(data, truncated);
        });
    }

    public ToolEnvelopeHolder clusterErrors(Map<String, Object> args) {
        return wrap(CLUSTER_ERRORS, args, a -> {
            String pathArg = support.optString(a, "path");
            String window = support.optString(a, "window");
            String since = support.optString(a, "since");
            Instant sinceInstant = LogTimestampParser.parseSince(since);
            Instant cutoff;
            boolean sinceApplied;
            if (sinceInstant != null) {
                cutoff = sinceInstant;
                sinceApplied = true;
                if (window == null || window.isBlank()) {
                    window = null;
                }
            } else {
                if (window == null || window.isBlank()) {
                    window = "15m";
                }
                cutoff = Instant.now().minus(parseWindow(window));
                sinceApplied = false;
            }

            Path path = support.pathGuard().resolveAllowed(pathArg);
            if (!Files.isRegularFile(path)) {
                throw new DiagException(ErrorCode.INVALID_ARGUMENT, "path is not a regular file: " + path);
            }

            long deadline = System.currentTimeMillis() + Math.min(10_000L, support.config().toolTimeoutMs());
            Map<String, ClusterBucket> buckets = new HashMap<>();
            int scanned = 0;
            boolean truncated = false;

            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    scanned++;
                    if (System.currentTimeMillis() > deadline) {
                        truncated = true;
                        break;
                    }
                    Instant lineTs = LogTimestampParser.parseLineTimestamp(line);
                    if (lineTs != null && lineTs.isBefore(cutoff)) {
                        continue;
                    }
                    Matcher matcher = EXCEPTION_PATTERN.matcher(line);
                    if (matcher.find()) {
                        String type = matcher.group(1);
                        String message = matcher.group(2) == null ? "" : matcher.group(2).trim();
                        String key = type + "|" + normalizeMessage(message);
                        ClusterBucket bucket = buckets.computeIfAbsent(key, k -> new ClusterBucket(type, message));
                        bucket.count++;
                        if (bucket.samples.size() < 3) {
                            bucket.samples.add(truncateLine(line, 500));
                        }
                    }
                }
            } catch (IOException ex) {
                throw new DiagException(ErrorCode.INTERNAL, "failed to read log: " + ex.getMessage(), ex);
            }

            List<Map<String, Object>> top = buckets.values().stream()
                    .sorted(Comparator.comparingInt((ClusterBucket b) -> b.count).reversed())
                    .limit(20)
                    .map(ClusterBucket::toMap)
                    .collect(Collectors.toList());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", path.toString());
            data.put("window", window);
            data.put("since", since);
            data.put("sinceApplied", sinceApplied);
            data.put("scannedLines", scanned);
            data.put("clusters", top);
            return ToolResult.of(data, truncated);
        });
    }

    private void addLogsFromDir(Path dir, String app, List<Map<String, Object>> out, int limit) {
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> looksLikeLog(p.getFileName().toString()))
                    .filter(p -> app == null || app.isBlank()
                            || p.getFileName().toString().toLowerCase(Locale.ROOT)
                            .contains(app.toLowerCase(Locale.ROOT)))
                    .limit(limit)
                    .forEach(p -> {
                        boolean exists = out.stream().anyMatch(m -> p.toString().equals(m.get("path")));
                        if (exists) {
                            return;
                        }
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("path", p.toString());
                        try {
                            item.put("sizeBytes", Files.size(p));
                            item.put("lastModifiedEpochMs", Files.getLastModifiedTime(p).toMillis());
                        } catch (IOException ex) {
                            item.put("sizeBytes", null);
                        }
                        out.add(item);
                    });
        } catch (IOException ignored) {
            // skip
        }
    }

    private static boolean looksLikeLog(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".log")
                || lower.endsWith(".out")
                || lower.endsWith(".err")
                || lower.contains("gc.log")
                || lower.endsWith(".log.gz");
    }

    static List<String> readLastLines(Path path, int lines, int maxBytes) throws DiagException {
        ArrayDeque<String> deque = new ArrayDeque<>(lines);
        long fileSize;
        try {
            fileSize = Files.size(path);
        } catch (IOException ex) {
            throw new DiagException(ErrorCode.INTERNAL, "cannot stat file: " + ex.getMessage(), ex);
        }
        long readFrom = Math.max(0, fileSize - Math.min(fileSize, Math.max(maxBytes, lines * 256L)));
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            raf.seek(readFrom);
            if (readFrom > 0) {
                raf.readLine(); // discard partial
            }
            String line;
            while ((line = raf.readLine()) != null) {
                deque.addLast(new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));
                if (deque.size() > lines) {
                    deque.removeFirst();
                }
            }
        } catch (IOException ex) {
            throw new DiagException(ErrorCode.INTERNAL, "failed to tail log: " + ex.getMessage(), ex);
        }
        return new ArrayList<>(deque);
    }

    static Duration parseWindow(String window) {
        String w = window.trim().toLowerCase(Locale.ROOT);
        try {
            if (w.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(w.substring(0, w.length() - 1)));
            }
            if (w.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(w.substring(0, w.length() - 1)));
            }
            if (w.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(w.substring(0, w.length() - 1)));
            }
            return Duration.ofMinutes(Long.parseLong(w));
        } catch (NumberFormatException ex) {
            throw new DiagException(ErrorCode.INVALID_ARGUMENT, "invalid window: " + window);
        }
    }

    private List<Map<String, Object>> inferLogPathsFromCmdline(JavaProcessInfo info) {
        String cmd = info.commandLine();
        if (cmd == null || cmd.isBlank()) {
            return List.of();
        }
        List<String> tokens = extractPathHints(cmd);
        List<Map<String, Object>> out = new ArrayList<>();
        for (String hint : tokens) {
            Path candidate = Paths.get(hint.trim()).toAbsolutePath().normalize();
            if (!support.pathGuard().isUnderAllowed(candidate)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("path", candidate.toString());
            item.put("source", "cmdline");
            item.put("exists", Files.exists(candidate));
            if (Files.isRegularFile(candidate)) {
                try {
                    item.put("sizeBytes", Files.size(candidate));
                } catch (IOException ignored) {
                    // skip
                }
            } else if (Files.isDirectory(candidate)) {
                item.put("isDirectory", true);
            }
            out.add(item);
        }
        return out;
    }

    private static List<String> extractPathHints(String cmdline) {
        List<String> hints = new ArrayList<>();
        String[] keys = {
                "-Dlogging.path=", "-Dlogging.file.name=", "-Dlogging.file.path=",
                "-DLOG_FILE=", "-Dcatalina.base=", "-Dcatalina.home=",
                "--logging.path=", "--logging.file.path="
        };
        for (String key : keys) {
            int idx = 0;
            while ((idx = cmdline.indexOf(key, idx)) >= 0) {
                idx += key.length();
                String rest = cmdline.substring(idx);
                String value = readPropValue(rest);
                if (!value.isBlank()) {
                    hints.add(value);
                }
            }
        }
        return hints;
    }

    private static String readPropValue(String rest) {
        if (rest.isEmpty()) {
            return "";
        }
        if (rest.charAt(0) == '"') {
            int end = rest.indexOf('"', 1);
            return end > 0 ? rest.substring(1, end) : rest.substring(1);
        }
        int space = rest.indexOf(' ');
        return space > 0 ? rest.substring(0, space) : rest;
    }

    private static String normalizeMessage(String message) {
        if (message == null) {
            return "";
        }
        return message
                .replaceAll("\\b\\d+\\b", "#")
                .replaceAll("0x[0-9a-fA-F]+", "0x#")
                .trim();
    }

    private static String truncateLine(String line, int maxChars) {
        if (line.length() <= maxChars) {
            return line;
        }
        return line.substring(0, maxChars) + "...";
    }

    private ToolEnvelopeHolder wrap(
            String tool,
            Map<String, Object> args,
            java.util.function.Function<Map<String, Object>, ToolResult> handler) {
        return new ToolEnvelopeHolder(support.invoke(tool, args, handler));
    }

    private static final class ClusterBucket {
        private final String type;
        private final String messageSample;
        private int count;
        private final List<String> samples = new ArrayList<>();

        private ClusterBucket(String type, String messageSample) {
            this.type = type;
            this.messageSample = messageSample;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("exceptionClass", type);
            map.put("messageSample", messageSample);
            map.put("count", count);
            map.put("samples", samples);
            return map;
        }
    }
}

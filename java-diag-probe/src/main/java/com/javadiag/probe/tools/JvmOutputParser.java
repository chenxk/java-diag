package com.javadiag.probe.tools;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Structured summaries from jcmd/jstack/jstat text output (best-effort).
 */
public final class JvmOutputParser {

    private static final Pattern THREAD_LINE = Pattern.compile(
            "\"([^\"]+)\"\\s+#\\d+\\s+(\\w+)", Pattern.MULTILINE);
    private static final Pattern DEADLOCK = Pattern.compile(
            "Found\\s+(?:one|\\d+)\\s+Java-level deadlock", Pattern.CASE_INSENSITIVE);
    private static final Pattern JNI_DEADLOCK = Pattern.compile(
            "Found\\s+JNI\\s+global\\s+reference", Pattern.CASE_INSENSITIVE);

    private JvmOutputParser() {
    }

    public static Map<String, Object> parseThreadDumpSummary(String dump) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (dump == null || dump.isBlank()) {
            summary.put("threadCount", 0);
            return summary;
        }
        int runnable = 0;
        int blocked = 0;
        int waiting = 0;
        int timedWaiting = 0;
        int other = 0;
        Matcher m = THREAD_LINE.matcher(dump);
        int threads = 0;
        while (m.find()) {
            threads++;
            String state = m.group(2).toLowerCase(Locale.ROOT);
            switch (state) {
                case "runnable" -> runnable++;
                case "blocked" -> blocked++;
                case "waiting" -> waiting++;
                case "timed_waiting" -> timedWaiting++;
                default -> other++;
            }
        }
        boolean deadlock = DEADLOCK.matcher(dump).find() || dump.toLowerCase(Locale.ROOT).contains("deadlock");
        summary.put("threadCount", threads);
        summary.put("runnableCount", runnable);
        summary.put("blockedCount", blocked);
        summary.put("waitingCount", waiting);
        summary.put("timedWaitingCount", timedWaiting);
        summary.put("otherStateCount", other);
        summary.put("deadlockDetected", deadlock);
        summary.put("jniGlobalRefsMentioned", JNI_DEADLOCK.matcher(dump).find());
        return summary;
    }

    public static Map<String, Object> parseJstatGcUtil(String output, String option) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("option", option);
        if (output == null || output.isBlank() || !"gcutil".equalsIgnoreCase(option)) {
            summary.put("parsed", false);
            return summary;
        }
        String[] lines = output.split("\\R");
        String header = null;
        String lastData = null;
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("Timestamp") || t.startsWith("Time")) {
                continue;
            }
            if (t.contains("S0") && t.contains("YGC")) {
                header = t;
            } else if (header != null && t.matches("[-+]?[\\d.eE]+.*")) {
                lastData = t;
            }
        }
        if (header == null || lastData == null) {
            summary.put("parsed", false);
            return summary;
        }
        String[] cols = header.trim().split("\\s+");
        String[] vals = lastData.trim().split("\\s+");
        if (vals.length >= cols.length) {
            Map<String, Object> metrics = new LinkedHashMap<>();
            int offset = vals.length - cols.length;
            for (int i = 0; i < cols.length; i++) {
                String key = cols[i];
                String raw = vals[offset + i];
                try {
                    if (key.equals("YGC") || key.equals("FGC") || key.equals("YGCT") || key.equals("FGCT") || key.equals("GCT")) {
                        metrics.put(key, raw.contains(".") ? Double.parseDouble(raw) : Long.parseLong(raw));
                    } else {
                        metrics.put(key, Double.parseDouble(raw));
                    }
                } catch (NumberFormatException ex) {
                    metrics.put(key, raw);
                }
            }
            summary.put("parsed", true);
            summary.put("metrics", metrics);
            return summary;
        }
        summary.put("parsed", false);
        return summary;
    }

    public static Map<String, Object> enrichVmSummary(Map<String, Object> parsed, String raw) {
        Map<String, Object> extra = new LinkedHashMap<>();
        if (raw != null) {
            for (String line : raw.split("\\R")) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.contains("deadlock")) {
                    extra.put("deadlockMentioned", true);
                }
                if (lower.contains("thread count") || lower.contains("threads:")) {
                    Long n = firstLongInLine(line);
                    if (n != null) {
                        extra.put("threadCountHint", n);
                    }
                }
                if (lower.contains("heap usage") || lower.startsWith("heap")) {
                    extra.put("heapLine", line.trim());
                }
            }
        }
        if (!extra.isEmpty()) {
            parsed = new LinkedHashMap<>(parsed);
            parsed.put("structured", extra);
        }
        return parsed;
    }

    public static Map<String, Object> compareThreadDumps(String before, String after) {
        Map<String, Object> beforeSum = parseThreadDumpSummary(before);
        Map<String, Object> afterSum = parseThreadDumpSummary(after);
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("threadCountBefore", beforeSum.get("threadCount"));
        delta.put("threadCountAfter", afterSum.get("threadCount"));
        delta.put("threadCountDelta",
                ((Number) afterSum.get("threadCount")).intValue()
                        - ((Number) beforeSum.get("threadCount")).intValue());
        delta.put("blockedDelta",
                ((Number) afterSum.get("blockedCount")).intValue()
                        - ((Number) beforeSum.get("blockedCount")).intValue());
        delta.put("runnableDelta",
                ((Number) afterSum.get("runnableCount")).intValue()
                        - ((Number) beforeSum.get("runnableCount")).intValue());
        delta.put("deadlockAfter", afterSum.get("deadlockDetected"));
        return delta;
    }

    private static Long firstLongInLine(String line) {
        Matcher m = Pattern.compile("\\b(\\d+)\\b").matcher(line);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}

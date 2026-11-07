package com.javadiag.mcp.process;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Best-effort listen-port hints from a JVM command line (no syscall). */
public final class JvmPortHints {

    private static final Pattern[] PATTERNS = {
        Pattern.compile("(?:-D|--)(?:server\\.)?port=(\\d{2,5})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:-D|--)(?:http\\.)?port=(\\d{2,5})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:-Dspring\\.application\\.json=.*?\"server\\.port\"\\s*:\\s*)(\\d{2,5})", Pattern.CASE_INSENSITIVE),
    };

    private JvmPortHints() {}

    public static List<Integer> fromCommandLine(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return List.of();
        }
        Set<Integer> ports = new LinkedHashSet<>();
        for (Pattern pattern : PATTERNS) {
            Matcher m = pattern.matcher(commandLine);
            while (m.find()) {
                try {
                    int p = Integer.parseInt(m.group(1));
                    if (p > 0 && p <= 65535) {
                        ports.add(p);
                    }
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
        }
        if (!ports.isEmpty()) {
            return List.copyOf(ports);
        }
        // Windows / generic: -Dserver.port is common; also match " --8080 " style cautiously
        String lower = commandLine.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf("server.port");
        if (idx >= 0) {
            extractNearbyDigits(commandLine, idx, ports);
        }
        return List.copyOf(ports);
    }

    private static void extractNearbyDigits(String cmdline, int anchor, Set<Integer> ports) {
        int start = Math.max(0, anchor);
        int end = Math.min(cmdline.length(), anchor + 40);
        String slice = cmdline.substring(start, end);
        Matcher m = Pattern.compile("(\\d{2,5})").matcher(slice);
        while (m.find()) {
            int p = Integer.parseInt(m.group(1));
            if (p >= 1024 && p <= 65535) {
                ports.add(p);
                break;
            }
        }
    }
}

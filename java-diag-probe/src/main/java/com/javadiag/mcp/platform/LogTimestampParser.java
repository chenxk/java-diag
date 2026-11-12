package com.javadiag.mcp.platform;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort parsing of common log line timestamps (ISO-8601, logback-style).
 */
public final class LogTimestampParser {

    private static final Pattern LOGBACK =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}[.,]\\d{3}");
    private static final Pattern LOGBACK_NO_MS =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}\\b");
    private static final Pattern APACHE =
            Pattern.compile("^\\d{2}/[A-Za-z]{3}/\\d{4}:\\d{2}:\\d{2}:\\d{2}");

    private static final DateTimeFormatter LOGBACK_FMT = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd[['T'][' ']]HH:mm:ss")
            .optionalStart()
            .appendPattern("[.,]")
            .appendValue(ChronoField.MILLI_OF_SECOND, 3)
            .optionalEnd()
            .toFormatter(Locale.ROOT);

    private static final DateTimeFormatter APACHE_FMT =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss", Locale.ENGLISH);

    private LogTimestampParser() {
    }

    public static Instant parseSince(String since) {
        if (since == null || since.isBlank()) {
            return null;
        }
        String s = since.trim();
        try {
            if (s.endsWith("Z") || s.contains("T")) {
                return Instant.parse(s.endsWith("Z") ? s : s + "Z");
            }
            return LocalDateTime.parse(s, LOGBACK_FMT).atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception ex) {
            throw new DiagException(
                    com.javadiag.mcp.model.ErrorCode.INVALID_ARGUMENT,
                    "invalid since timestamp: " + since);
        }
    }

    public static Instant parseLineTimestamp(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        String trimmed = line.stripLeading();
        if (trimmed.length() >= 20) {
            int t = trimmed.indexOf('T');
            if (t > 4 && t < 24) {
                try {
                    String iso = trimmed.substring(0, Math.min(trimmed.length(), 30)).split("[\\s,]", 2)[0];
                    if (iso.contains("T")) {
                        return Instant.parse(iso.endsWith("Z") ? iso : iso + "Z");
                    }
                } catch (Exception ignored) {
                    // fall through
                }
            }
        }
        Matcher lb = LOGBACK.matcher(trimmed);
        if (lb.find()) {
            try {
                String token = lb.group();
                return LocalDateTime.parse(token, LOGBACK_FMT).atZone(ZoneId.systemDefault()).toInstant();
            } catch (Exception ignored) {
                // fall through
            }
        }
        Matcher lb2 = LOGBACK_NO_MS.matcher(trimmed);
        if (lb2.find()) {
            try {
                String token = lb2.group().trim();
                return LocalDateTime.parse(token, LOGBACK_FMT).atZone(ZoneId.systemDefault()).toInstant();
            } catch (Exception ignored) {
                // fall through
            }
        }
        Matcher ap = APACHE.matcher(trimmed);
        if (ap.find()) {
            try {
                return LocalDateTime.parse(ap.group(), APACHE_FMT).atZone(ZoneId.systemDefault()).toInstant();
            } catch (Exception ignored) {
                // fall through
            }
        }
        return null;
    }
}

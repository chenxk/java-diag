package com.javadiag.probe.process;

import com.javadiag.probe.platform.OsPlatform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Maps JVM main class / jar tokens to a stable business service name for registry apps[].
 */
public final class AppNameMapper {

    public record Rule(String pattern, Pattern regex, String serviceName) {
        boolean matches(String mainClassOrJar) {
            if (mainClassOrJar == null || mainClassOrJar.isBlank()) {
                return false;
            }
            if (regex != null) {
                return regex.matcher(mainClassOrJar).find();
            }
            return mainClassOrJar.toLowerCase(Locale.ROOT).contains(pattern.toLowerCase(Locale.ROOT));
        }
    }

    private final List<Rule> rules;

    public AppNameMapper(List<Rule> rules) {
        this.rules = List.copyOf(rules == null ? List.of() : rules);
    }

    public static AppNameMapper empty() {
        return new AppNameMapper(List.of());
    }

    public static AppNameMapper parse(String envValue, List<String> cliEntries) {
        Map<String, String> ordered = new LinkedHashMap<>();
        parseInto(envValue, ordered);
        if (cliEntries != null) {
            for (String entry : cliEntries) {
                parseInto(entry, ordered);
            }
        }
        List<Rule> rules = new ArrayList<>();
        for (Map.Entry<String, String> e : ordered.entrySet()) {
            String pattern = e.getKey();
            String service = e.getValue();
            if (pattern.isBlank() || service.isBlank()) {
                continue;
            }
            Pattern regex = null;
            String literal = pattern;
            if (pattern.regionMatches(true, 0, "regex:", 0, 6)) {
                regex = Pattern.compile(pattern.substring(6));
                literal = pattern.substring(6);
            }
            rules.add(new Rule(literal, regex, service));
        }
        return new AppNameMapper(rules);
    }

    private static void parseInto(String raw, Map<String, String> target) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String part : raw.split(",")) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            int eq = item.indexOf('=');
            if (eq <= 0 || eq >= item.length() - 1) {
                continue;
            }
            String key = item.substring(0, eq).trim();
            String value = item.substring(eq + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                target.put(key, value);
            }
        }
    }

    public String resolveName(String mainClassOrJar) {
        if (mainClassOrJar != null) {
            for (Rule rule : rules) {
                if (rule.matches(mainClassOrJar)) {
                    return rule.serviceName();
                }
            }
        }
        return fallbackShortName(mainClassOrJar);
    }

    public List<Rule> rules() {
        return rules;
    }

    static String fallbackShortName(String mainClassOrJar) {
        if (mainClassOrJar == null || mainClassOrJar.isBlank()) {
            return "unknown";
        }
        String token = mainClassOrJar;
        int slash = Math.max(token.lastIndexOf('/'), token.lastIndexOf('\\'));
        if (slash >= 0 && slash < token.length() - 1) {
            token = token.substring(slash + 1);
        }
        if (OsPlatform.looksLikeJavaBinary(token) || "exe".equalsIgnoreCase(token)) {
            return "unknown";
        }
        if (token.endsWith(".jar")) {
            return token;
        }
        int dot = token.lastIndexOf('.');
        if (dot > 0 && dot < token.length() - 1) {
            String simple = token.substring(dot + 1);
            if ("exe".equalsIgnoreCase(simple)) {
                return "unknown";
            }
            return simple;
        }
        return token;
    }
}

package com.javadiag.probe.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Loads {@code .properties} settings and resolves values: file &lt; env (env wins over file).
 */
public final class SettingsProperties {

    public static final String DEFAULT_FILENAME = "java-diag-probe.properties";

    private SettingsProperties() {}

    public record ConfigLayer(Map<String, String> fileSettings, List<String> cliArgsWithoutConfig) {}

    public static ConfigLayer loadConfigLayer(List<String> cliArgs) {
        return loadConfigLayer(cliArgs, DEFAULT_FILENAME);
    }

    public static ConfigLayer loadConfigLayer(List<String> cliArgs, String defaultFilename) {
        List<String> args = new ArrayList<>(cliArgs);
        Path explicit = null;
        for (int i = 0; i < args.size(); i++) {
            if ("--config".equals(args.get(i))) {
                if (i + 1 >= args.size() || args.get(i + 1).startsWith("-")) {
                    throw new IllegalArgumentException("--config requires a file path");
                }
                explicit = Path.of(args.get(i + 1)).toAbsolutePath().normalize();
                args.remove(i + 1);
                args.remove(i);
                break;
            }
        }
        Map<String, String> fileSettings = new LinkedHashMap<>();
        if (explicit != null) {
            if (!Files.isRegularFile(explicit)) {
                throw new IllegalArgumentException(
                        "Config file not found or not a file: " + explicit
                                + " (copy from java-diag-probe.properties.example)");
            }
            fileSettings.putAll(readPropertiesFile(explicit));
        } else {
            Path defaultPath = Path.of(defaultFilename).toAbsolutePath().normalize();
            if (Files.isRegularFile(defaultPath)) {
                fileSettings.putAll(readPropertiesFile(defaultPath));
            }
        }
        return new ConfigLayer(Map.copyOf(fileSettings), List.copyOf(args));
    }

    public static Map<String, String> readPropertiesFile(Path path) {
        Properties raw = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            raw.load(in);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read config file: " + path + " — " + ex.getMessage(), ex);
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : raw.stringPropertyNames()) {
            String value = raw.getProperty(name);
            if (value != null) {
                out.put(name.trim(), value.trim());
            }
        }
        return out;
    }

    /**
     * Env overrides file; {@code null} if neither set (caller uses builtin default).
     */
    public static String resolve(
            Map<String, String> fileSettings, Map<String, String> env, String envKey, String... propertyKeys) {
        if (env != null) {
            String fromEnv = env.get(envKey);
            if (fromEnv != null && !fromEnv.isBlank()) {
                return fromEnv.trim();
            }
        }
        if (fileSettings != null && propertyKeys != null) {
            for (String key : propertyKeys) {
                String v = fileSettings.get(key);
                if (v != null && !v.isBlank()) {
                    return v.trim();
                }
            }
        }
        return null;
    }
}

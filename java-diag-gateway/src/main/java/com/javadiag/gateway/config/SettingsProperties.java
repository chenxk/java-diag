package com.javadiag.gateway.config;

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
 *
 * <p>With no {@code --config} the file order (low to high) is classpath
 * {@code /java-diag-gateway.properties}, then {@code ./java-diag-gateway.properties}.
 * An explicit {@code --config} replaces that file layer.
 */
public final class SettingsProperties {

    public static final String DEFAULT_FILENAME = "java-diag-gateway.properties";

    /** Classpath resource name (no leading slash). */
    public static final String CLASSPATH_RESOURCE = DEFAULT_FILENAME;

    private SettingsProperties() {}

    public record ConfigLayer(Map<String, String> fileSettings, List<String> cliArgsWithoutConfig) {}

    public static ConfigLayer loadConfigLayer(List<String> cliArgs) {
        return loadConfigLayer(cliArgs, DEFAULT_FILENAME);
    }

    public static ConfigLayer loadConfigLayer(List<String> cliArgs, String defaultFilename) {
        ClassLoader loader = SettingsProperties.class.getClassLoader();
        if (loader == null) {
            loader = ClassLoader.getSystemClassLoader();
        }
        return loadConfigLayer(cliArgs, defaultFilename, Path.of("").toAbsolutePath(), loader);
    }

    static ConfigLayer loadConfigLayer(List<String> cliArgs, Path cwd, ClassLoader loader) {
        return loadConfigLayer(cliArgs, DEFAULT_FILENAME, cwd, loader);
    }

    static ConfigLayer loadConfigLayer(
            List<String> cliArgs, String defaultFilename, Path cwd, ClassLoader loader) {
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
                                + " (copy from java-diag-gateway.properties.example)");
            }
            fileSettings.putAll(readPropertiesFile(explicit));
        } else {
            String resource = classpathResourceName(defaultFilename);
            fileSettings.putAll(readClasspath(loader, resource));
            Path cwdFile = cwd.toAbsolutePath().normalize().resolve(defaultFilename);
            if (Files.isRegularFile(cwdFile)) {
                fileSettings.putAll(readPropertiesFile(cwdFile));
            }
        }
        return new ConfigLayer(Map.copyOf(fileSettings), List.copyOf(args));
    }

    private static String classpathResourceName(String defaultFilename) {
        Path name = Path.of(defaultFilename).getFileName();
        return name == null ? defaultFilename : name.toString();
    }

    private static Map<String, String> readClasspath(ClassLoader loader, String resourceName) {
        InputStream in = loader == null ? null : loader.getResourceAsStream(resourceName);
        if (in == null) {
            return Map.of();
        }
        Properties raw = new Properties();
        try (InputStream stream = in) {
            raw.load(stream);
        } catch (IOException ex) {
            throw new IllegalArgumentException(
                    "Failed to read classpath config: /" + resourceName + " — " + ex.getMessage(), ex);
        }
        return toMap(raw);
    }

    private static Map<String, String> toMap(Properties raw) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : raw.stringPropertyNames()) {
            String value = raw.getProperty(name);
            if (value != null) {
                out.put(name.trim(), value.trim());
            }
        }
        return out;
    }

    public static Map<String, String> readPropertiesFile(Path path) {
        Properties raw = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            raw.load(in);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read config file: " + path + " — " + ex.getMessage(), ex);
        }
        return toMap(raw);
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

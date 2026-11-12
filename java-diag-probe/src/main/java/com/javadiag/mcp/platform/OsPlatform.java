package com.javadiag.mcp.platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cross-platform helpers for Windows / macOS / Linux.
 */
public final class OsPlatform {

    private static final String OS_NAME =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

    private OsPlatform() {
    }

    public static boolean isWindows() {
        return OS_NAME.contains("win");
    }

    public static boolean isMac() {
        return OS_NAME.contains("mac") || OS_NAME.contains("darwin");
    }

    public static boolean isLinux() {
        return OS_NAME.contains("linux");
    }

    public static boolean hasProcFs() {
        return Files.isDirectory(Path.of("/proc"));
    }

    /** PATH entries using the native path-list separator. */
    public static List<String> pathEntries() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return List.of();
        }
        String sep = java.io.File.pathSeparator;
        List<String> out = new ArrayList<>();
        for (String dir : pathEnv.split(java.util.regex.Pattern.quote(sep))) {
            if (!dir.isBlank()) {
                out.add(dir);
            }
        }
        return out;
    }

    /**
     * Resolve a JDK tool basename for the current OS ({@code jcmd} → {@code jcmd.exe} on Windows).
     */
    public static String executableName(String basename) {
        if (basename == null || basename.isBlank()) {
            return basename;
        }
        if (isWindows()) {
            String lower = basename.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".exe") || lower.endsWith(".bat") || lower.endsWith(".cmd")) {
                return basename;
            }
            return basename + ".exe";
        }
        return basename;
    }

    /**
     * Strip Windows executable suffix for whitelist matching ({@code jcmd.exe} → {@code jcmd}).
     */
    public static String toolBasename(String fileName) {
        if (fileName == null) {
            return "";
        }
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".exe")) {
            return name.substring(0, name.length() - 4);
        }
        if (name.endsWith(".bat") || name.endsWith(".cmd")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    /**
     * Whether a tool binary is usable. On Windows, {@link Files#isExecutable} is unreliable;
     * existence of a regular file is enough.
     */
    public static boolean isRunnable(Path path) {
        if (path == null) {
            return false;
        }
        try {
            if (!Files.isRegularFile(path)) {
                return false;
            }
            if (isWindows()) {
                return true;
            }
            return Files.isExecutable(path);
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Default log-dir whitelist entries that make sense on the current OS.
     * Always includes cwd, user home, and the JVM temp dir; Unix also adds {@code /var/log} when present.
     */
    public static List<Path> defaultAllowedLogDirs() {
        List<Path> dirs = new ArrayList<>();
        dirs.add(Path.of(".").toAbsolutePath().normalize());
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            dirs.add(Path.of(home).toAbsolutePath().normalize());
        }
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp != null && !tmp.isBlank()) {
            dirs.add(Path.of(tmp).toAbsolutePath().normalize());
        }
        if (!isWindows()) {
            Path varLog = Path.of("/var/log");
            if (Files.isDirectory(varLog)) {
                dirs.add(varLog.toAbsolutePath().normalize());
            }
            // Keep /tmp explicitly when distinct from java.io.tmpdir (rare but possible)
            Path slashTmp = Path.of("/tmp");
            if (Files.isDirectory(slashTmp)) {
                Path normalized = slashTmp.toAbsolutePath().normalize();
                if (!dirs.contains(normalized)) {
                    dirs.add(normalized);
                }
            }
        }
        return dirs;
    }

    /** True when {@code command} looks like a Java launcher binary path or name. */
    public static boolean looksLikeJavaBinary(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        String lower = command.toLowerCase(Locale.ROOT).replace('/', '\\');
        String leaf = lower;
        int slash = Math.max(lower.lastIndexOf('\\'), lower.lastIndexOf('/'));
        if (slash >= 0 && slash + 1 < lower.length()) {
            leaf = lower.substring(slash + 1);
        }
        return leaf.equals("java")
                || leaf.equals("java.exe")
                || leaf.equals("javaw")
                || leaf.equals("javaw.exe");
    }
}

package com.javadiag.mcp.platform;

import com.javadiag.mcp.model.ErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves JDK diagnostic binaries, preferring the target process toolchain.
 */
public final class JdkToolchain {

    public record Toolchain(Path javaHome, Path jcmd, Path jstack, Path jstat, Path jps, int majorVersion) {
    }

    public Toolchain resolveForPid(long pid) {
        Optional<Path> targetHome = findJavaHomeForPid(pid);
        if (targetHome.isPresent()) {
            Optional<Toolchain> tc = fromJavaHome(targetHome.get());
            if (tc.isPresent()) {
                return tc.get();
            }
        }
        return resolveFallback();
    }

    public Toolchain resolveFallback() {
        String javaHomeProp = System.getProperty("java.home");
        if (javaHomeProp != null) {
            Optional<Toolchain> fromProp = fromJavaHome(Path.of(javaHomeProp));
            if (fromProp.isPresent()) {
                return fromProp.get();
            }
        }
        String envHome = System.getenv("JAVA_HOME");
        if (envHome != null && !envHome.isBlank()) {
            Optional<Toolchain> fromEnv = fromJavaHome(Path.of(envHome));
            if (fromEnv.isPresent()) {
                return fromEnv.get();
            }
        }
        // PATH lookup for jcmd only — version unknown
        Path jcmd = which("jcmd").orElseThrow(() ->
                new DiagException(ErrorCode.INTERNAL, "jcmd not found on PATH or JAVA_HOME"));
        Path home = jcmd.getParent() == null ? Path.of(".") : jcmd.getParent().getParent();
        return new Toolchain(
                home,
                jcmd,
                which("jstack").orElse(jcmd.getParent().resolve("jstack")),
                which("jstat").orElse(jcmd.getParent().resolve("jstat")),
                which("jps").orElse(jcmd.getParent().resolve("jps")),
                detectMajorVersion(home));
    }

    public Optional<Toolchain> fromJavaHome(Path javaHome) {
        if (javaHome == null || !Files.isDirectory(javaHome)) {
            return Optional.empty();
        }
        Path home = javaHome;
        // jre nested under jdk
        if (home.getFileName() != null && "jre".equalsIgnoreCase(home.getFileName().toString())) {
            Path parent = home.getParent();
            if (parent != null && Files.isDirectory(parent.resolve("bin"))) {
                home = parent;
            }
        }
        Path bin = home.resolve("bin");
        Path jcmd = bin.resolve("jcmd");
        if (!Files.isExecutable(jcmd)) {
            return Optional.empty();
        }
        return Optional.of(new Toolchain(
                home,
                jcmd,
                bin.resolve("jstack"),
                bin.resolve("jstat"),
                bin.resolve("jps"),
                detectMajorVersion(home)));
    }

    public Optional<Path> findJavaHomeForPid(long pid) {
        Path exe = Path.of("/proc", Long.toString(pid), "exe");
        try {
            if (Files.exists(exe)) {
                Path real = exe.toRealPath();
                Path bin = real.getParent();
                if (bin != null) {
                    Path home = bin.getParent();
                    if (home != null && Files.isDirectory(home.resolve("bin"))) {
                        return Optional.of(home);
                    }
                }
            }
        } catch (IOException ignored) {
            // fall through to environ
        }

        Path environ = Path.of("/proc", Long.toString(pid), "environ");
        try {
            if (Files.isReadable(environ)) {
                byte[] raw = Files.readAllBytes(environ);
                String[] parts = new String(raw).split("\0");
                for (String part : parts) {
                    if (part.startsWith("JAVA_HOME=")) {
                        Path home = Path.of(part.substring("JAVA_HOME=".length()));
                        if (Files.isDirectory(home)) {
                            return Optional.of(home);
                        }
                    }
                }
            }
        } catch (IOException ignored) {
            // ignore
        }
        return Optional.empty();
    }

    public int detectMajorVersion(Path javaHome) {
        Path release = javaHome.resolve("release");
        if (Files.isReadable(release)) {
            try {
                for (String line : Files.readAllLines(release)) {
                    if (line.startsWith("JAVA_VERSION=")) {
                        String v = line.substring("JAVA_VERSION=".length()).replace("\"", "").trim();
                        return parseMajor(v);
                    }
                }
            } catch (IOException ignored) {
                // fall through
            }
        }
        return parseMajor(System.getProperty("java.specification.version", "17"));
    }

    static int parseMajor(String version) {
        if (version == null || version.isBlank()) {
            return 17;
        }
        String v = version.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("1.")) {
            int dot = v.indexOf('.', 2);
            String minor = dot < 0 ? v.substring(2) : v.substring(2, dot);
            try {
                return Integer.parseInt(minor);
            } catch (NumberFormatException ex) {
                return 8;
            }
        }
        int cut = 0;
        while (cut < v.length() && Character.isDigit(v.charAt(cut))) {
            cut++;
        }
        if (cut == 0) {
            return 17;
        }
        try {
            return Integer.parseInt(v.substring(0, cut));
        } catch (NumberFormatException ex) {
            return 17;
        }
    }

    private Optional<Path> which(String name) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return Optional.empty();
        }
        for (String dir : pathEnv.split(":")) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(dir, name);
            if (Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Prefer jcmd for modern JDKs; fall back to jstack when jcmd is missing.
     */
    public boolean preferJcmd(Toolchain toolchain) {
        return toolchain.majorVersion() >= 8 && Files.isExecutable(toolchain.jcmd());
    }
}

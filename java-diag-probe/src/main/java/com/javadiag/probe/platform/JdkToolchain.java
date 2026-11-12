package com.javadiag.probe.platform;

import com.javadiag.probe.model.ErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves JDK diagnostic binaries, preferring the target process toolchain.
 * Works on Windows, macOS, and Linux.
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
        Path bin = jcmd.getParent();
        return new Toolchain(
                home,
                jcmd,
                which("jstack").orElse(bin == null ? Path.of("jstack") : bin.resolve(OsPlatform.executableName("jstack"))),
                which("jstat").orElse(bin == null ? Path.of("jstat") : bin.resolve(OsPlatform.executableName("jstat"))),
                which("jps").orElse(bin == null ? Path.of("jps") : bin.resolve(OsPlatform.executableName("jps"))),
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
        Path jcmd = bin.resolve(OsPlatform.executableName("jcmd"));
        if (!OsPlatform.isRunnable(jcmd)) {
            return Optional.empty();
        }
        return Optional.of(new Toolchain(
                home,
                jcmd,
                bin.resolve(OsPlatform.executableName("jstack")),
                bin.resolve(OsPlatform.executableName("jstat")),
                bin.resolve(OsPlatform.executableName("jps")),
                detectMajorVersion(home)));
    }

    public Optional<Path> findJavaHomeForPid(long pid) {
        if (OsPlatform.hasProcFs()) {
            Optional<Path> fromProc = findJavaHomeFromProc(pid);
            if (fromProc.isPresent()) {
                return fromProc;
            }
        }
        return findJavaHomeFromProcessHandle(pid);
    }

    private Optional<Path> findJavaHomeFromProc(long pid) {
        Path exe = Path.of("/proc", Long.toString(pid), "exe");
        try {
            if (Files.exists(exe)) {
                Path real = exe.toRealPath();
                Optional<Path> home = javaHomeFromJavaBinary(real);
                if (home.isPresent()) {
                    return home;
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

    private Optional<Path> findJavaHomeFromProcessHandle(long pid) {
        try {
            Optional<ProcessHandle> handle = ProcessHandle.of(pid);
            if (handle.isEmpty()) {
                return Optional.empty();
            }
            Optional<String> command = handle.get().info().command();
            if (command.isPresent()) {
                Optional<Path> home = javaHomeFromJavaBinary(Path.of(command.get()));
                if (home.isPresent()) {
                    return home;
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return Optional.empty();
    }

    static Optional<Path> javaHomeFromJavaBinary(Path javaBinary) {
        if (javaBinary == null) {
            return Optional.empty();
        }
        Path bin = javaBinary.getParent();
        if (bin == null) {
            return Optional.empty();
        }
        Path home = bin.getParent();
        if (home != null && Files.isDirectory(home.resolve("bin"))) {
            return Optional.of(home);
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

    public Optional<Path> which(String name) {
        String exeName = OsPlatform.executableName(name);
        for (String dir : OsPlatform.pathEntries()) {
            Path candidate = Path.of(dir, exeName);
            if (OsPlatform.isRunnable(candidate)) {
                return Optional.of(candidate);
            }
            // Also try bare name on Unix in case PATH entry is unusual
            if (!exeName.equals(name)) {
                Path bare = Path.of(dir, name);
                if (OsPlatform.isRunnable(bare)) {
                    return Optional.of(bare);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Prefer jcmd for modern JDKs; fall back to jstack when jcmd is missing.
     */
    public boolean preferJcmd(Toolchain toolchain) {
        return toolchain.majorVersion() >= 8 && OsPlatform.isRunnable(toolchain.jcmd());
    }
}

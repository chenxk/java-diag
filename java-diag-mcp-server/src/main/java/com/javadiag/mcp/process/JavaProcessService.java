package com.javadiag.mcp.process;

import com.javadiag.mcp.model.ErrorCode;
import com.javadiag.mcp.platform.DiagException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Discovers local Java processes via {@code /proc} (Linux).
 */
public final class JavaProcessService {

    private final Optional<Pattern> allowedMainClassRegex;

    public JavaProcessService(Optional<Pattern> allowedMainClassRegex) {
        this.allowedMainClassRegex = allowedMainClassRegex;
    }

    public List<JavaProcessInfo> listProcesses(String keyword, String userFilter) {
        List<JavaProcessInfo> all = scanProc();
        return all.stream()
                .filter(p -> matchesKeyword(p, keyword))
                .filter(p -> matchesUser(p, userFilter))
                .filter(this::matchesMainClassPolicy)
                .collect(Collectors.toList());
    }

    public List<JavaProcessInfo> resolve(String mainClass, String jar, Integer port) {
        if ((mainClass == null || mainClass.isBlank())
                && (jar == null || jar.isBlank())
                && port == null) {
            throw new DiagException(
                    ErrorCode.INVALID_ARGUMENT,
                    "at least one of mainClass, jar, or port is required");
        }
        List<JavaProcessInfo> matches = new ArrayList<>();
        for (JavaProcessInfo info : scanProc()) {
            if (!matchesMainClassPolicy(info)) {
                continue;
            }
            boolean ok = true;
            if (mainClass != null && !mainClass.isBlank()) {
                ok = containsIgnoreCase(info.mainClassOrJar(), mainClass)
                        || containsIgnoreCase(info.commandLine(), mainClass);
            }
            if (ok && jar != null && !jar.isBlank()) {
                ok = containsIgnoreCase(info.commandLine(), jar)
                        || containsIgnoreCase(info.mainClassOrJar(), jar);
            }
            if (ok && port != null) {
                ok = listensOnPort(info.pid(), port) || containsIgnoreCase(info.commandLine(), ":" + port);
            }
            if (ok) {
                matches.add(info);
            }
        }
        return matches;
    }

    public void requireAlive(long pid) {
        if (!Files.exists(Path.of("/proc", Long.toString(pid)))) {
            throw new DiagException(ErrorCode.PID_NOT_FOUND, "pid not found: " + pid);
        }
        Optional<JavaProcessInfo> info = readPid(pid);
        if (info.isEmpty() || !isJavaProcess(info.get().commandLine())) {
            // Still allow attach attempts for non-/proc-visible edge cases if /proc exists,
            // but require java-like cmdline when readable.
            if (info.isPresent() && !isJavaProcess(info.get().commandLine())) {
                throw new DiagException(ErrorCode.PID_NOT_FOUND, "pid is not a Java process: " + pid);
            }
            if (info.isEmpty()) {
                throw new DiagException(ErrorCode.PID_NOT_FOUND, "unable to read pid: " + pid);
            }
        }
        if (!matchesMainClassPolicy(info.get())) {
            throw new DiagException(ErrorCode.UNAUTHORIZED, "pid blocked by allowedMainClassRegex: " + pid);
        }
    }

    private List<JavaProcessInfo> scanProc() {
        List<JavaProcessInfo> result = new ArrayList<>();
        Path proc = Path.of("/proc");
        if (!Files.isDirectory(proc)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(proc)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (!name.chars().allMatch(Character::isDigit)) {
                    continue;
                }
                long pid = Long.parseLong(name);
                readPid(pid).ifPresent(info -> {
                    if (isJavaProcess(info.commandLine())) {
                        result.add(info);
                    }
                });
            }
        } catch (IOException ignored) {
            // empty
        }
        return result;
    }

    private Optional<JavaProcessInfo> readPid(long pid) {
        Path procDir = Path.of("/proc", Long.toString(pid));
        if (!Files.isDirectory(procDir)) {
            return Optional.empty();
        }
        String cmdline = readCmdline(procDir.resolve("cmdline"));
        String main = extractMain(cmdline);
        String user = readUser(procDir);
        Long start = readStartTimeMs(procDir);
        Long rss = readRssKb(procDir.resolve("status"));
        return Optional.of(new JavaProcessInfo(pid, main, main, user, start, rss, cmdline));
    }

    private String readCmdline(Path cmdlinePath) {
        try {
            byte[] raw = Files.readAllBytes(cmdlinePath);
            if (raw.length == 0) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < raw.length; i++) {
                if (raw[i] == 0) {
                    sb.append(' ');
                } else {
                    sb.append((char) (raw[i] & 0xFF));
                }
            }
            return sb.toString().trim();
        } catch (IOException ex) {
            return "";
        }
    }

    static String extractMain(String cmdline) {
        if (cmdline == null || cmdline.isBlank()) {
            return "";
        }
        String[] parts = cmdline.split("\\s+");
        boolean jarNext = false;
        for (String part : parts) {
            if (jarNext) {
                return part;
            }
            if ("-jar".equals(part)) {
                jarNext = true;
                continue;
            }
            if (part.startsWith("-")) {
                continue;
            }
            String lower = part.toLowerCase(Locale.ROOT);
            if (lower.endsWith("/java") || lower.equals("java") || lower.endsWith("\\java.exe")) {
                continue;
            }
            // skip module path style launcher tokens that look like paths to java binary already handled
            if (!part.contains("/") && part.contains(".")) {
                return part;
            }
            if (lower.endsWith(".jar")) {
                return part;
            }
        }
        // fallback: last non-option token
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].startsWith("-")) {
                return parts[i];
            }
        }
        return parts[0];
    }

    private boolean isJavaProcess(String cmdline) {
        if (cmdline == null || cmdline.isBlank()) {
            return false;
        }
        String lower = cmdline.toLowerCase(Locale.ROOT);
        return lower.contains("/java ")
                || lower.startsWith("java ")
                || lower.contains("/java\0")
                || Pattern.compile("(^|/)java(\\s|$)").matcher(lower).find()
                || lower.contains("org.apache.catalina")
                || lower.contains("org.springframework.boot");
    }

    private String readUser(Path procDir) {
        try {
            Path status = procDir.resolve("status");
            for (String line : Files.readAllLines(status, StandardCharsets.UTF_8)) {
                if (line.startsWith("Uid:")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length > 1) {
                        return parts[1];
                    }
                }
            }
        } catch (IOException ignored) {
            // ignore
        }
        return null;
    }

    private Long readStartTimeMs(Path procDir) {
        try {
            Optional<ProcessHandle> handle = ProcessHandle.of(Long.parseLong(procDir.getFileName().toString()));
            return handle.map(ProcessHandle::info)
                    .flatMap(ProcessHandle.Info::startInstant)
                    .map(i -> i.toEpochMilli())
                    .orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private Long readRssKb(Path statusPath) {
        try {
            for (String line : Files.readAllLines(statusPath, StandardCharsets.UTF_8)) {
                if (line.startsWith("VmRSS:")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length > 1) {
                        return Long.parseLong(parts[1]);
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    private boolean matchesKeyword(JavaProcessInfo info, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return containsIgnoreCase(info.mainClassOrJar(), keyword)
                || containsIgnoreCase(info.commandLine(), keyword)
                || containsIgnoreCase(info.displayName(), keyword);
    }

    private boolean matchesUser(JavaProcessInfo info, String userFilter) {
        if (userFilter == null || userFilter.isBlank()) {
            return true;
        }
        return userFilter.equals(info.user());
    }

    private boolean matchesMainClassPolicy(JavaProcessInfo info) {
        if (allowedMainClassRegex.isEmpty()) {
            return true;
        }
        Pattern pattern = allowedMainClassRegex.get();
        String main = info.mainClassOrJar() == null ? "" : info.mainClassOrJar();
        return pattern.matcher(main).find() || pattern.matcher(info.commandLine()).find();
    }

    private boolean listensOnPort(long pid, int port) {
        // Best-effort via /proc/net/tcp — match inode from fd
        try {
            Path fdDir = Path.of("/proc", Long.toString(pid), "fd");
            if (!Files.isDirectory(fdDir)) {
                return false;
            }
            List<String> inodes = new ArrayList<>();
            try (DirectoryStream<Path> fds = Files.newDirectoryStream(fdDir)) {
                for (Path fd : fds) {
                    try {
                        Path target = Files.readSymbolicLink(fd);
                        String t = target.toString();
                        if (t.startsWith("socket:[")) {
                            inodes.add(t.substring("socket:[".length(), t.length() - 1));
                        }
                    } catch (IOException ignored) {
                        // ignore
                    }
                }
            }
            if (inodes.isEmpty()) {
                return false;
            }
            String hexPort = String.format("%04X", port);
            return portInTable(Path.of("/proc/net/tcp"), hexPort, inodes)
                    || portInTable(Path.of("/proc/net/tcp6"), hexPort, inodes);
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean portInTable(Path table, String hexPort, List<String> inodes) throws IOException {
        if (!Files.isReadable(table)) {
            return false;
        }
        List<String> lines = Files.readAllLines(table, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            String[] cols = lines.get(i).trim().split("\\s+");
            if (cols.length < 10) {
                continue;
            }
            String local = cols[1];
            int idx = local.indexOf(':');
            if (idx < 0) {
                continue;
            }
            if (!hexPort.equalsIgnoreCase(local.substring(idx + 1))) {
                continue;
            }
            String inode = cols[9];
            if (inodes.contains(inode)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) {
            return false;
        }
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
}

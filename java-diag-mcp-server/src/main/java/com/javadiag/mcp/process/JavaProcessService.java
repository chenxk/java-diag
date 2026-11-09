package com.javadiag.mcp.process;

import com.javadiag.mcp.model.ErrorCode;
import com.javadiag.mcp.platform.CommandResult;
import com.javadiag.mcp.platform.DiagException;
import com.javadiag.mcp.platform.JdkToolchain;
import com.javadiag.mcp.platform.OsPlatform;
import com.javadiag.mcp.platform.ProcessRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Discovers local Java processes on Windows, macOS, and Linux.
 * <p>
 * Strategy: on Linux, merge {@code /proc} and {@code jps -lvm} by pid (union);
 * elsewhere prefer {@code jps}; finally {@link ProcessHandle} as a last resort.
 */
public final class JavaProcessService {

    private final Optional<Pattern> allowedMainClassRegex;
    private final ProcessRunner processRunner;
    private final JdkToolchain jdkToolchain;

    public JavaProcessService(Optional<Pattern> allowedMainClassRegex) {
        this(allowedMainClassRegex, null, null);
    }

    public JavaProcessService(
            Optional<Pattern> allowedMainClassRegex,
            ProcessRunner processRunner,
            JdkToolchain jdkToolchain) {
        this.allowedMainClassRegex = allowedMainClassRegex;
        this.processRunner = processRunner;
        this.jdkToolchain = jdkToolchain;
    }

    public List<JavaProcessInfo> listProcesses(String keyword, String userFilter) {
        List<JavaProcessInfo> all = discoverAll();
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
        for (JavaProcessInfo info : discoverAll()) {
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

    public Optional<JavaProcessInfo> lookup(long pid) {
        return findByPid(pid);
    }

    public void requireAlive(long pid) {
        Optional<JavaProcessInfo> info = findByPid(pid);
        if (info.isEmpty()) {
            if (!isProcessAlive(pid)) {
                throw new DiagException(ErrorCode.PID_NOT_FOUND, "pid not found: " + pid);
            }
            // Alive but not recognized as Java — still allow attach for edge cases where
            // discovery missed it (permissions), unless we can positively prove non-Java.
            Optional<String> cmd = ProcessHandle.of(pid).flatMap(h -> h.info().command());
            if (cmd.isPresent() && !OsPlatform.looksLikeJavaBinary(cmd.get()) && !isJavaProcess(cmd.get())) {
                throw new DiagException(ErrorCode.PID_NOT_FOUND, "pid is not a Java process: " + pid);
            }
            return;
        }
        if (!isJavaProcess(info.get().commandLine())
                && !isJavaMainToken(info.get().mainClassOrJar())
                && !OsPlatform.looksLikeJavaBinary(info.get().commandLine())) {
            throw new DiagException(ErrorCode.PID_NOT_FOUND, "pid is not a Java process: " + pid);
        }
        if (!matchesMainClassPolicy(info.get())) {
            throw new DiagException(ErrorCode.UNAUTHORIZED, "pid blocked by allowedMainClassRegex: " + pid);
        }
    }

    private List<JavaProcessInfo> discoverAll() {
        if (OsPlatform.hasProcFs()) {
            // Union /proc and jps by pid — do not short-circuit when /proc is non-empty.
            // Host jps often sees JVMs that /proc cmdline heuristics miss (and vice versa).
            List<JavaProcessInfo> merged = mergeByPid(scanProc(), scanJps());
            if (!merged.isEmpty()) {
                return filterAndEnrich(merged);
            }
        } else {
            List<JavaProcessInfo> fromJps = scanJps();
            if (!fromJps.isEmpty()) {
                return filterAndEnrich(fromJps);
            }
        }
        return filterAndEnrich(scanProcessHandles());
    }

    /**
     * Union two discovery sources by pid. {@code primary} wins on conflict
     * (typically {@code /proc}, which carries user/rss/full cmdline), except when
     * primary's main is not reportable and secondary's is — then adopt secondary's main
     * while keeping primary metadata.
     */
    static List<JavaProcessInfo> mergeByPid(
            List<JavaProcessInfo> primary, List<JavaProcessInfo> secondary) {
        Map<Long, JavaProcessInfo> byPid = new LinkedHashMap<>();
        if (primary != null) {
            for (JavaProcessInfo info : primary) {
                if (info != null) {
                    byPid.put(info.pid(), info);
                }
            }
        }
        if (secondary != null) {
            for (JavaProcessInfo info : secondary) {
                if (info == null) {
                    continue;
                }
                JavaProcessInfo existing = byPid.get(info.pid());
                if (existing == null) {
                    byPid.put(info.pid(), info);
                } else {
                    byPid.put(info.pid(), preferReportableMain(existing, info));
                }
            }
        }
        return new ArrayList<>(byPid.values());
    }

    /**
     * Keep {@code primary} metadata on conflict. If its stored main is not a valid jar/FQCN,
     * try repairing from primary cmdline; if still unusable and {@code secondary} has a valid
     * main (e.g. jps), adopt secondary's main/display name.
     */
    static JavaProcessInfo preferReportableMain(JavaProcessInfo primary, JavaProcessInfo secondary) {
        if (primary == null) {
            return secondary;
        }
        String primaryMain = resolveReportableMain(primary);
        if (isJavaMainToken(primaryMain)) {
            if (primaryMain.equals(primary.mainClassOrJar())) {
                return primary;
            }
            return copyWithMain(primary, primaryMain);
        }
        if (secondary == null) {
            return primary;
        }
        String secondaryMain = resolveReportableMain(secondary);
        if (!isJavaMainToken(secondaryMain)) {
            return primary;
        }
        String cmd = primary.commandLine();
        if (cmd == null || cmd.isBlank()) {
            cmd = secondary.commandLine();
        }
        return new JavaProcessInfo(
                primary.pid(),
                secondaryMain,
                secondaryMain,
                primary.user() != null ? primary.user() : secondary.user(),
                primary.startTimeEpochMs() != null ? primary.startTimeEpochMs() : secondary.startTimeEpochMs(),
                primary.rssKb() != null ? primary.rssKb() : secondary.rssKb(),
                cmd);
    }

    private static String resolveReportableMain(JavaProcessInfo info) {
        if (info == null) {
            return "";
        }
        String main = normalizeMainToken(info.mainClassOrJar());
        if (isJavaMainToken(main)) {
            return main;
        }
        return extractMain(info.commandLine());
    }

    /**
     * True when the process has an identifiable JVM entry point (jar or main class), not merely {@code java.exe}.
     */
    public static boolean isReportableJvm(JavaProcessInfo info) {
        if (info == null) {
            return false;
        }
        String main = normalizeMainToken(info.mainClassOrJar());
        if (isJavaMainToken(main)) {
            return true;
        }
        return isJavaMainToken(extractMain(info.commandLine()));
    }

    static JavaProcessInfo enrichMainFromCommandLine(JavaProcessInfo info) {
        if (info == null) {
            return info;
        }
        String main = normalizeMainToken(info.mainClassOrJar());
        if (isJavaMainToken(main)) {
            if (main.equals(info.mainClassOrJar())) {
                return info;
            }
            return copyWithMain(info, main);
        }
        String fromCmd = extractMain(info.commandLine());
        if (isJavaMainToken(fromCmd)) {
            return copyWithMain(info, fromCmd);
        }
        return info;
    }

    private static JavaProcessInfo copyWithMain(JavaProcessInfo info, String main) {
        String cmd = info.commandLine();
        if (cmd == null || cmd.isBlank()) {
            cmd = main;
        }
        return new JavaProcessInfo(
                info.pid(), main, main, info.user(), info.startTimeEpochMs(), info.rssKb(), cmd);
    }

    private static String normalizeMainToken(String main) {
        if (main == null || main.isBlank()) {
            return "";
        }
        String token = main.trim();
        if (OsPlatform.looksLikeJavaBinary(token)) {
            return "";
        }
        String lower = token.toLowerCase(Locale.ROOT);
        if (lower.endsWith("\\java.exe") || lower.endsWith("/java.exe") || lower.equals("java.exe")) {
            return "";
        }
        if (lower.endsWith("\\javaw.exe") || lower.endsWith("/javaw.exe") || lower.equals("javaw.exe")) {
            return "";
        }
        return token;
    }

    private List<JavaProcessInfo> filterAndEnrich(List<JavaProcessInfo> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<JavaProcessInfo> out = new ArrayList<>();
        for (JavaProcessInfo info : raw) {
            JavaProcessInfo enriched = enrichMainFromCommandLine(info);
            if (isReportableJvm(enriched)) {
                out.add(enriched);
            }
        }
        return out;
    }

    private Optional<JavaProcessInfo> findByPid(long pid) {
        for (JavaProcessInfo info : discoverAll()) {
            if (info.pid() == pid) {
                return Optional.of(info);
            }
        }
        if (OsPlatform.hasProcFs()) {
            return readPid(pid).filter(i -> isJavaProcess(i.commandLine()) || isJavaMainToken(i.mainClassOrJar()));
        }
        return Optional.empty();
    }

    private static boolean isProcessAlive(long pid) {
        try {
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        } catch (Exception ex) {
            return false;
        }
    }

    private List<JavaProcessInfo> scanJps() {
        if (processRunner == null || jdkToolchain == null) {
            return List.of();
        }
        try {
            JdkToolchain.Toolchain tc = jdkToolchain.resolveFallback();
            List<Path> candidates = new ArrayList<>();
            if (OsPlatform.isRunnable(tc.jps())) {
                candidates.add(tc.jps());
            }
            jdkToolchain.which("jps").ifPresent(path -> {
                if (!candidates.contains(path)) {
                    candidates.add(path);
                }
            });
            for (Path jps : candidates) {
                List<JavaProcessInfo> parsed = runJpsExecutable(jps);
                if (!parsed.isEmpty()) {
                    return parsed;
                }
            }
            return List.of();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<JavaProcessInfo> runJpsExecutable(Path jps) {
        try {
            long timeoutMs = jpsListingTimeoutMs();
            CommandResult result = processRunner.run(List.of(jps.toString(), "-lvm"), timeoutMs);
            if (result.exitCode() != 0 && (result.stdout() == null || result.stdout().isBlank())) {
                result = processRunner.run(List.of(jps.toString(), "-l"), timeoutMs);
            }
            return parseJpsOutput(result.stdout());
        } catch (Exception ex) {
            return List.of();
        }
    }

    private long jpsListingTimeoutMs() {
        long configured = processRunnerDefaultTimeout();
        // jps can exceed ~5s on busy hosts; floor at 12s all platforms, never below tool.timeout.ms.
        return Math.max(configured, 12_000L);
    }

    private long processRunnerDefaultTimeout() {
        if (processRunner == null) {
            return 15_000L;
        }
        return processRunner.defaultTimeoutMs();
    }

    /**
     * Parse {@code jps -l} / {@code jps -lvm} lines: {@code <pid> <main> [args...]}.
     */
    static List<JavaProcessInfo> parseJpsOutput(String stdout) {
        List<JavaProcessInfo> result = new ArrayList<>();
        if (stdout == null || stdout.isBlank()) {
            return result;
        }
        Map<Long, JavaProcessInfo> byPid = new LinkedHashMap<>();
        for (String line : stdout.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int sp = indexOfWhitespace(trimmed);
            if (sp <= 0) {
                continue;
            }
            long pid;
            try {
                pid = Long.parseLong(trimmed.substring(0, sp));
            } catch (NumberFormatException ex) {
                continue;
            }
            String rest = trimmed.substring(sp).trim();
            String main;
            String cmdline;
            if (rest.isEmpty() || rest.equalsIgnoreCase(" -- process information unavailable")) {
                main = "";
                cmdline = "java";
            } else {
                int next = indexOfWhitespace(rest);
                if (next < 0) {
                    main = rest;
                    cmdline = rest;
                } else {
                    main = rest.substring(0, next);
                    cmdline = rest;
                }
            }
            // Skip the jps tool itself
            if (main.contains("sun.tools.jps") || main.contains("jdk.jcmd/sun.tools.jps")) {
                continue;
            }
            Long start = ProcessHandle.of(pid)
                    .flatMap(h -> h.info().startInstant())
                    .map(i -> i.toEpochMilli())
                    .orElse(null);
            byPid.put(pid, new JavaProcessInfo(pid, main, main, null, start, null, cmdline));
        }
        result.addAll(byPid.values());
        return result;
    }

    private static int indexOfWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private List<JavaProcessInfo> scanProcessHandles() {
        List<JavaProcessInfo> result = new ArrayList<>();
        try {
            ProcessHandle.allProcesses().forEach(handle -> {
                try {
                    if (!handle.isAlive()) {
                        return;
                    }
                    ProcessHandle.Info info = handle.info();
                    String command = info.command().orElse("");
                    String[] args = info.arguments().orElse(new String[0]);
                    String cmdline = info.commandLine().orElseGet(() -> joinCommand(command, args));
                    if (!isJavaProcess(cmdline) && !OsPlatform.looksLikeJavaBinary(command)) {
                        return;
                    }
                    String main = extractMain(cmdline);
                    if (main.isBlank() && args.length > 0) {
                        main = extractMain(joinCommand(command, args));
                    }
                    Long start = info.startInstant().map(i -> i.toEpochMilli()).orElse(null);
                    String user = info.user().orElse(null);
                    result.add(new JavaProcessInfo(
                            handle.pid(),
                            main,
                            main.isBlank() ? command : main,
                            user,
                            start,
                            null,
                            cmdline.isBlank() ? command : cmdline));
                } catch (Exception ignored) {
                    // skip inaccessible processes
                }
            });
        } catch (Exception ignored) {
            // empty
        }
        return result;
    }

    private static String joinCommand(String command, String[] args) {
        StringBuilder sb = new StringBuilder();
        if (command != null && !command.isBlank()) {
            sb.append(command);
        }
        if (args != null) {
            for (String arg : args) {
                if (arg == null) {
                    continue;
                }
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(arg);
            }
        }
        return sb.toString();
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
                    if (isJavaProcess(info.commandLine()) || looksLikeJavaExe(pid)) {
                        result.add(info);
                    }
                });
            }
        } catch (IOException ignored) {
            // empty
        }
        return result;
    }

    /**
     * Fallback when {@code /proc/<pid>/cmdline} does not look like a Java launcher
     * (e.g. truncated or rewritten args): resolve {@code /proc/<pid>/exe}.
     */
    private static boolean looksLikeJavaExe(long pid) {
        Path exe = Path.of("/proc", Long.toString(pid), "exe");
        try {
            if (!Files.exists(exe)) {
                return false;
            }
            Path real = exe.toRealPath();
            return OsPlatform.looksLikeJavaBinary(real.toString());
        } catch (Exception ex) {
            return false;
        }
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
        String trimmed = cmdline.trim();
        if (isLauncherOnlyCommandLine(trimmed)) {
            return "";
        }
        String[] parts = trimmed.split("\\s+");
        boolean jarNext = false;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (jarNext) {
                // Misplaced JVM flags after -jar (e.g. java -jar -XX:+PrintGCDetails app.jar):
                // skip option tokens (and their values) until the real jar/main path.
                if (part.startsWith("-")) {
                    if (isJvmOptionTakingValue(part)
                            && i + 1 < parts.length
                            && !parts[i + 1].startsWith("-")) {
                        i++;
                    }
                    continue;
                }
                return part;
            }
            if ("-jar".equals(part)) {
                jarNext = true;
                continue;
            }
            // -m / --module value is the entry point (module[/main]), not an option payload to skip.
            if ("-m".equals(part) || "--module".equals(part)) {
                if (i + 1 < parts.length && !parts[i + 1].startsWith("-")) {
                    return parts[i + 1];
                }
                continue;
            }
            if (part.startsWith("-")) {
                // Critical: -cp / -classpath value is often a single argv token ending in ".jar"
                // (colon/semicolon-separated). Skipping only the flag would mis-identify that
                // classpath blob as the main, e.g. java -classpath /a:/b/lib.jar com.foo.Main.
                if (isJvmOptionTakingValue(part)
                        && i + 1 < parts.length
                        && !parts[i + 1].startsWith("-")) {
                    i++;
                }
                continue;
            }
            String lower = part.toLowerCase(Locale.ROOT);
            if (lower.endsWith("/java")
                    || lower.equals("java")
                    || lower.endsWith("\\java")
                    || lower.endsWith("/java.exe")
                    || lower.endsWith("\\java.exe")
                    || lower.equals("java.exe")
                    || lower.endsWith("\\javaw.exe")
                    || lower.equals("javaw.exe")
                    || lower.endsWith("/javaw")
                    || lower.equals("javaw")) {
                continue;
            }
            if (!part.contains("/") && !part.contains("\\") && part.contains(".")) {
                return part;
            }
            if (lower.endsWith(".jar")) {
                return part;
            }
        }
        // fallback: last non-option, non-launcher token
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].startsWith("-")) {
                continue;
            }
            if (OsPlatform.looksLikeJavaBinary(parts[i])) {
                continue;
            }
            return parts[i];
        }
        return "";
    }

    /** JVM launcher options whose next argv token is a value, not the program entry. */
    private static boolean isJvmOptionTakingValue(String option) {
        if (option == null || option.isBlank()) {
            return false;
        }
        // Attached forms (-Dfoo=bar, -Xmx1g, -XX:..., -javaagent:path) do not consume a next token.
        if (option.contains("=") || option.contains(":")) {
            return false;
        }
        return switch (option) {
            case "-cp",
                    "-classpath",
                    "--class-path",
                    "-p",
                    "--module-path",
                    "-m",
                    "--module",
                    "--add-opens",
                    "--add-exports",
                    "--add-reads",
                    "--add-modules",
                    "--limit-modules",
                    "--patch-module",
                    "-javaagent",
                    "-agentpath",
                    "-agentlib" -> true;
            default -> false;
        };
    }

    private static boolean isLauncherOnlyCommandLine(String cmdline) {
        if (cmdline == null || cmdline.isBlank()) {
            return true;
        }
        String lower = cmdline.toLowerCase(Locale.ROOT);
        if (OsPlatform.looksLikeJavaBinary(cmdline.trim())) {
            return true;
        }
        boolean hasEntry = lower.contains("-jar");
        if (!hasEntry) {
            int cp = lower.indexOf("-cp");
            int classpath = lower.indexOf("-classpath");
            hasEntry = cp >= 0 || classpath >= 0;
        }
        if (!hasEntry) {
            String normalized = lower.replace('/', '\\');
            return normalized.endsWith("\\java.exe") || normalized.endsWith("\\javaw.exe");
        }
        return false;
    }

    static boolean isJavaProcess(String cmdline) {
        if (cmdline == null || cmdline.isBlank()) {
            return false;
        }
        String lower = cmdline.toLowerCase(Locale.ROOT);
        if (OsPlatform.looksLikeJavaBinary(lower.split("\\s+")[0])) {
            return true;
        }
        return lower.contains("/java ")
                || lower.contains("\\java ")
                || lower.contains("/java.exe ")
                || lower.contains("\\java.exe ")
                || lower.startsWith("java ")
                || lower.startsWith("java.exe ")
                || lower.contains("/javaw.exe ")
                || lower.contains("\\javaw.exe ")
                || lower.contains("/java\0")
                || Pattern.compile("(^|[/\\\\])java(\\.exe)?(\\s|$)", Pattern.CASE_INSENSITIVE)
                        .matcher(cmdline).find()
                || lower.contains("org.apache.catalina")
                || lower.contains("org.springframework.boot");
    }

    /**
     * True for jps-style main tokens: {@code *.jar} or FQCN ({@code com.foo.Bar}).
     * Rejects ordinary filenames like {@code server.py}, and classpath lists that happen
     * to end with {@code .jar} (e.g. {@code /a:/b/lib.jar} mistaken as a single jar path).
     */
    static boolean isJavaMainToken(String main) {
        if (main == null || main.isBlank()) {
            return false;
        }
        String lower = main.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jar")) {
            return isSingleJarPath(main);
        }
        if (main.indexOf('/') >= 0 || main.indexOf('\\') >= 0 || main.indexOf(' ') >= 0) {
            return false;
        }
        if (!main.contains(".") || lower.contains("://")) {
            return false;
        }
        int lastDot = main.lastIndexOf('.');
        String simple = main.substring(lastDot + 1);
        // File extensions are short and all-lowercase; Java simple class names usually aren't.
        if (simple.length() <= 5
                && simple.equals(simple.toLowerCase(Locale.ROOT))
                && simple.chars().allMatch(Character::isLetter)) {
            return false;
        }
        return main.matches("[\\w.$]+");
    }

    /**
     * A single jar path may be {@code app.jar} or {@code /path/app.jar} / {@code C:\path\app.jar}.
     * Classpath lists ({@code a.jar:b.jar} / {@code a.jar;b.jar}) are not a main token.
     */
    private static boolean isSingleJarPath(String token) {
        if (token.indexOf(';') >= 0) {
            return false;
        }
        int colon = token.indexOf(':');
        if (colon < 0) {
            return true;
        }
        // Allow a single Windows drive prefix (C:\... or C:/...), but not Unix classpath colons.
        boolean windowsDrive = colon == 1
                && Character.isLetter(token.charAt(0))
                && token.length() > 2
                && (token.charAt(2) == '\\' || token.charAt(2) == '/');
        if (!windowsDrive) {
            return false;
        }
        return token.indexOf(':', 2) < 0;
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
        if (OsPlatform.hasProcFs()) {
            return listensOnPortProc(pid, port);
        }
        // Non-Linux: best-effort via discovered cmdline only (caller also checks ":" + port).
        return false;
    }

    private boolean listensOnPortProc(long pid, int port) {
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

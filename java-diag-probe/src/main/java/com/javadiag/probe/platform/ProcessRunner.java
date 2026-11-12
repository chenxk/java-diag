package com.javadiag.probe.platform;

import com.javadiag.probe.model.ErrorCode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Runs external JDK diagnostic tools with ArgumentList, timeout, and max bytes.
 * No shell interpolation; executable basename must be on the whitelist.
 */
public final class ProcessRunner {

    private static final Set<String> ALLOWED_BASENAMES = Set.of(
            "jcmd", "jstack", "jstat", "jps", "jinfo");

    private final int maxOutputBytes;
    private final long defaultTimeoutMs;

    public ProcessRunner(int maxOutputBytes, long defaultTimeoutMs) {
        this.maxOutputBytes = maxOutputBytes;
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    public long defaultTimeoutMs() {
        return defaultTimeoutMs;
    }

    public CommandResult run(List<String> command) {
        return run(command, defaultTimeoutMs);
    }

    public CommandResult run(List<String> command, long timeoutMs) {
        if (command == null || command.isEmpty()) {
            throw new DiagException(ErrorCode.INVALID_ARGUMENT, "command is empty");
        }
        List<String> args = List.copyOf(command);
        validateCommand(args);

        long start = System.currentTimeMillis();
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(false);

        Process process;
        try {
            process = pb.start();
        } catch (IOException ex) {
            throw new DiagException(ErrorCode.INTERNAL, "failed to start process: " + args.get(0), ex);
        }

        StreamCollector stdout = new StreamCollector(process.getInputStream(), maxOutputBytes);
        StreamCollector stderr = new StreamCollector(process.getErrorStream(), maxOutputBytes);
        Thread outThread = new Thread(stdout, "diag-stdout");
        Thread errThread = new Thread(stderr, "diag-stderr");
        outThread.setDaemon(true);
        errThread.setDaemon(true);
        outThread.start();
        errThread.start();

        boolean finished;
        try {
            finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new DiagException(ErrorCode.INTERNAL, "interrupted while waiting for process", ex);
        }

        if (!finished) {
            process.destroyForcibly();
            joinQuietly(outThread);
            joinQuietly(errThread);
            long took = System.currentTimeMillis() - start;
            throw new DiagException(
                    ErrorCode.TIMEOUT,
                    "command timed out after " + timeoutMs + "ms: " + args.get(0));
        }

        joinQuietly(outThread);
        joinQuietly(errThread);
        long took = System.currentTimeMillis() - start;
        boolean truncated = stdout.truncated() || stderr.truncated();
        return new CommandResult(
                process.exitValue(),
                stdout.text(),
                stderr.text(),
                truncated,
                false,
                took);
    }

    private void validateCommand(List<String> args) {
        String executable = args.get(0);
        Path path = Path.of(executable);
        String basename = OsPlatform.toolBasename(path.getFileName().toString());
        if (!ALLOWED_BASENAMES.contains(basename)) {
            throw new DiagException(
                    ErrorCode.COMMAND_NOT_ALLOWED,
                    "executable not on whitelist: " + basename);
        }
        if (path.isAbsolute() && !OsPlatform.isRunnable(path)) {
            throw new DiagException(
                    ErrorCode.INTERNAL,
                    "executable not found or not executable: " + executable);
        }
        for (int i = 1; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg == null) {
                throw new DiagException(ErrorCode.INVALID_ARGUMENT, "null argument at index " + i);
            }
            if (arg.indexOf('\0') >= 0) {
                throw new DiagException(ErrorCode.INVALID_ARGUMENT, "argument contains NUL");
            }
        }
    }

    private static void joinQuietly(Thread thread) {
        try {
            thread.join(2_000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Visible for tests: truncate UTF-8 text to max bytes without splitting mid-output carelessly.
     */
    public static String truncateUtf8(String text, int maxBytes) {
        if (text == null) {
            return "";
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return text;
        }
        int cut = maxBytes;
        while (cut > 0 && (bytes[cut] & 0xC0) == 0x80) {
            cut--;
        }
        return new String(bytes, 0, cut, StandardCharsets.UTF_8);
    }

    private static final class StreamCollector implements Runnable {
        private final InputStream in;
        private final int maxBytes;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private volatile boolean truncated;
        private volatile String text = "";

        private StreamCollector(InputStream in, int maxBytes) {
            this.in = in;
            this.maxBytes = maxBytes;
        }

        @Override
        public void run() {
            byte[] chunk = new byte[8192];
            try {
                int read;
                while ((read = in.read(chunk)) >= 0) {
                    int remaining = maxBytes - buffer.size();
                    if (remaining <= 0) {
                        truncated = true;
                        // drain remainder
                        while (in.read(chunk) >= 0) {
                            // discard
                        }
                        break;
                    }
                    if (read > remaining) {
                        buffer.write(chunk, 0, remaining);
                        truncated = true;
                        while (in.read(chunk) >= 0) {
                            // discard
                        }
                        break;
                    }
                    buffer.write(chunk, 0, read);
                }
            } catch (IOException ignored) {
                // process may have been destroyed
            } finally {
                text = buffer.toString(StandardCharsets.UTF_8);
            }
        }

        boolean truncated() {
            return truncated;
        }

        String text() {
            return text;
        }
    }

    public List<String> allowedBasenames() {
        return new ArrayList<>(ALLOWED_BASENAMES);
    }
}

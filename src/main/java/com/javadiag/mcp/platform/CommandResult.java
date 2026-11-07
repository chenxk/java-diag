package com.javadiag.mcp.platform;

/**
 * Result of a whitelisted external process invocation.
 */
public final class CommandResult {

    private final int exitCode;
    private final String stdout;
    private final String stderr;
    private final boolean truncated;
    private final boolean timedOut;
    private final long durationMs;

    public CommandResult(
            int exitCode,
            String stdout,
            String stderr,
            boolean truncated,
            boolean timedOut,
            long durationMs) {
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
        this.truncated = truncated;
        this.timedOut = timedOut;
        this.durationMs = durationMs;
    }

    public int exitCode() {
        return exitCode;
    }

    public String stdout() {
        return stdout;
    }

    public String stderr() {
        return stderr;
    }

    public boolean truncated() {
        return truncated;
    }

    public boolean timedOut() {
        return timedOut;
    }

    public long durationMs() {
        return durationMs;
    }

    public String combinedOutput() {
        if (stderr == null || stderr.isBlank()) {
            return stdout == null ? "" : stdout;
        }
        if (stdout == null || stdout.isBlank()) {
            return stderr;
        }
        return stdout + "\n" + stderr;
    }
}

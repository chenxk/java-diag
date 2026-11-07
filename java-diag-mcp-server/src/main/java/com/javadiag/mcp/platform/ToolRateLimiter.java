package com.javadiag.mcp.platform;

import com.javadiag.mcp.model.ErrorCode;
import com.javadiag.mcp.tools.JvmTools;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-tool minimum interval (cooldown) for expensive JVM operations.
 */
public final class ToolRateLimiter {

    private static final Set<String> DUMP_CLASS_TOOLS = Set.of(
            JvmTools.THREAD_DUMP,
            JvmTools.GC_HISTO,
            JvmTools.HEAP_DUMP,
            JvmTools.CPU_SAMPLE);

    private final long dumpCooldownMs;
    private final ConcurrentHashMap<String, Long> lastInvocationEpochMs = new ConcurrentHashMap<>();

    public ToolRateLimiter(long dumpCooldownMs) {
        this.dumpCooldownMs = Math.max(0, dumpCooldownMs);
    }

    public void check(String tool) {
        if (dumpCooldownMs <= 0 || !DUMP_CLASS_TOOLS.contains(tool)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long prev = lastInvocationEpochMs.put(tool, now);
        if (prev != null && now - prev < dumpCooldownMs) {
            lastInvocationEpochMs.put(tool, prev);
            long retryAfterMs = dumpCooldownMs - (now - prev);
            throw new DiagException(
                    ErrorCode.RATE_LIMITED,
                    "tool " + tool + " is cooling down; retry after ~" + retryAfterMs + "ms");
        }
    }
}

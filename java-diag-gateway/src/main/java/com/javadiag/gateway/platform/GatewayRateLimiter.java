package com.javadiag.gateway.platform;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Per probe instance + tool cooldown for expensive proxied calls. */
public final class GatewayRateLimiter {

    private static final Set<String> COOLED_TOOLS = Set.of(
            "diag.thread_dump",
            "diag.gc_histo",
            "diag.heap_dump",
            "diag.cpu_sample");

    private final long dumpCooldownMs;
    private final ConcurrentHashMap<String, Long> lastCall = new ConcurrentHashMap<>();

    public GatewayRateLimiter(long dumpCooldownMs) {
        this.dumpCooldownMs = Math.max(0, dumpCooldownMs);
    }

    public boolean tryAcquire(String instanceId, String tool) {
        if (dumpCooldownMs <= 0 || !COOLED_TOOLS.contains(tool)) {
            return true;
        }
        String key = instanceId + "|" + tool;
        long now = System.currentTimeMillis();
        Long prev = lastCall.put(key, now);
        if (prev != null && now - prev < dumpCooldownMs) {
            lastCall.put(key, prev);
            return false;
        }
        return true;
    }

    public long retryAfterMs(String instanceId, String tool) {
        String key = instanceId + "|" + tool;
        Long prev = lastCall.get(key);
        if (prev == null) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - prev;
        return Math.max(0, dumpCooldownMs - elapsed);
    }
}

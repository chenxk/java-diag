package com.javadiag.gateway.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayRateLimiterTest {

    @Test
    void perInstanceCooldown() {
        GatewayRateLimiter limiter = new GatewayRateLimiter(60_000);
        assertTrue(limiter.tryAcquire("10.0.0.1:8765", "diag.thread_dump"));
        assertFalse(limiter.tryAcquire("10.0.0.1:8765", "diag.thread_dump"));
        assertTrue(limiter.tryAcquire("10.0.0.2:8765", "diag.thread_dump"));
    }
}

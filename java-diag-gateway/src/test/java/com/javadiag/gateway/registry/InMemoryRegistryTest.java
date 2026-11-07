package com.javadiag.gateway.registry;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRegistryTest {

    @Test
    void expiresWithoutHeartbeat() throws Exception {
        InMemoryRegistry registry = new InMemoryRegistry(Duration.ofMillis(50));
        registry.register(new ProbeInstance(
                "x", "svc", "127.0.0.1", 1,
                "http://127.0.0.1:1", "/sse", "/mcp",
                null, Map.of(), "0.1.0", Instant.now(), ProbeStatus.HEALTHY));
        Thread.sleep(80);
        registry.refresh();
        assertTrue(registry.list("svc", Map.of(), true).isEmpty());
        assertEquals(1, registry.list("svc", Map.of(), false).size());
        assertEquals(ProbeStatus.UNHEALTHY, registry.list("svc", Map.of(), false).get(0).status());
    }

    @Test
    void heartbeatKeepsHealthy() throws Exception {
        InMemoryRegistry registry = new InMemoryRegistry(Duration.ofMillis(200));
        registry.register(new ProbeInstance(
                "x", "svc", "127.0.0.1", 1,
                "http://127.0.0.1:1", "/sse", "/mcp",
                null, Map.of(), "0.1.0", Instant.now(), ProbeStatus.HEALTHY));
        Thread.sleep(80);
        assertTrue(registry.heartbeat("x"));
        Thread.sleep(80);
        registry.refresh();
        assertEquals(1, registry.list("svc", Map.of(), true).size());
    }
}

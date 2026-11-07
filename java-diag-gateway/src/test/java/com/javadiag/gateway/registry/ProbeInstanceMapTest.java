package com.javadiag.gateway.registry;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbeInstanceMapTest {

    @Test
    void toAdminMapExposesAuthTokenNotProbeTokenKey() {
        ProbeInstance inst = new ProbeInstance(
                "h:8765",
                "svc",
                "h",
                8765,
                "http://h:8765",
                "/sse",
                "/mcp",
                "secret-probe",
                Map.of(),
                "1.0",
                Instant.now(),
                ProbeStatus.HEALTHY);

        Map<String, Object> pub = inst.toPublicMap();
        assertFalse(pub.containsKey("authToken"));
        assertFalse(pub.containsKey("probeToken"));

        Map<String, Object> admin = inst.toAdminMap();
        assertEquals("secret-probe", admin.get("authToken"));
        assertFalse(admin.containsKey("probeToken"));
    }

    @Test
    void toAdminMapOmitsAuthTokenWhenProbeTokenUnset() {
        ProbeInstance inst = new ProbeInstance(
                "h:8765",
                "svc",
                "h",
                8765,
                "http://h:8765",
                "/sse",
                "/mcp",
                null,
                Map.of(),
                "1.0",
                Instant.now(),
                ProbeStatus.HEALTHY);

        Map<String, Object> admin = inst.toAdminMap();
        assertFalse(admin.containsKey("authToken"));
    }

    @Test
    void registerPreservesProbeTokenOnReRegisterWithoutToken() {
        InMemoryRegistry registry = new InMemoryRegistry(java.time.Duration.ofSeconds(90));
        ProbeInstance first = new ProbeInstance(
                "a",
                "svc",
                "127.0.0.1",
                8765,
                "http://127.0.0.1:8765",
                "/sse",
                "/mcp",
                "keep-me",
                Map.of(),
                "1",
                Instant.now(),
                ProbeStatus.HEALTHY);
        registry.register(first);

        ProbeInstance second = new ProbeInstance(
                "a",
                "svc",
                "127.0.0.1",
                8765,
                "http://127.0.0.1:8765",
                "/sse",
                "/mcp",
                null,
                Map.of(),
                "1",
                Instant.now(),
                ProbeStatus.HEALTHY);
        registry.register(second);

        assertEquals("keep-me", registry.list(null, Map.of(), false).get(0).probeToken());
        assertEquals("keep-me", registry.list(null, Map.of(), false).get(0).toAdminMap().get("authToken"));
    }

    @Test
    void heartbeatBackfillsProbeToken() {
        InMemoryRegistry registry = new InMemoryRegistry(java.time.Duration.ofSeconds(90));
        registry.register(new ProbeInstance(
                "b",
                "svc",
                "127.0.0.1",
                8765,
                "http://127.0.0.1:8765",
                "/sse",
                "/mcp",
                null,
                Map.of(),
                "1",
                Instant.now(),
                ProbeStatus.HEALTHY));

        assertTrue(registry.heartbeat("b", "from-heartbeat"));
        ProbeInstance updated = registry.list(null, Map.of(), false).get(0);
        assertEquals("from-heartbeat", updated.probeToken());
        assertEquals("from-heartbeat", updated.toAdminMap().get("authToken"));
    }
}

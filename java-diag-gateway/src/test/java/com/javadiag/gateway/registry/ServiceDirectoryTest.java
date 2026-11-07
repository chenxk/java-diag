package com.javadiag.gateway.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceDirectoryTest {

    private InMemoryRegistry registry;
    private ServiceDirectory directory;

    @BeforeEach
    void setUp() {
        registry = new InMemoryRegistry(Duration.ofSeconds(90));
        directory = new ServiceDirectory(registry);
    }

    @Test
    void resolveReturnsEmptyWhenNoMatch() {
        registry.register(probe("p1", "host-a", List.of(app("order-service", 1001, "order-service.jar"))));
        assertTrue(directory.resolve("missing", Map.of(), true).isEmpty());
    }

    @Test
    void resolveSingleMatch() {
        registry.register(probe("p1", "host-a", List.of(app("order-service", 1001, "order-service.jar"))));
        List<ServiceDirectory.Match> matches = directory.resolve("order-service", Map.of(), true);
        assertEquals(1, matches.size());
        assertEquals("p1", matches.get(0).instance().instanceId());
        assertEquals(1001L, matches.get(0).app().pid());
    }

    @Test
    void resolveManyMatches() {
        registry.register(probe("p1", "host-a", List.of(app("order-service", 1001, "order-service.jar"))));
        registry.register(probe("p2", "host-b", List.of(app("order-service", 2002, "order-service.jar"))));
        assertEquals(2, directory.resolve("order-service", Map.of(), true).size());
    }

    @Test
    void listFiltersByApp() {
        registry.register(probe("p1", "host-a", List.of(app("pay-service", 10, "com.pay.App"))));
        registry.register(probe("p2", "host-a", List.of(app("order-service", 20, "order.jar"))));
        assertEquals(1, registry.list(null, "pay", Map.of(), true).size());
        assertEquals("p1", registry.list(null, "pay", Map.of(), true).get(0).instanceId());
    }

    @Test
    void heartbeatUpdatesApps() {
        registry.register(probe("p1", "host-a", List.of()));
        assertTrue(registry.heartbeat("p1", null, List.of(app("a", 1, "a.jar"))));
        assertEquals(1, registry.list(null, null, Map.of(), true).get(0).apps().size());
    }

    private static ProbeJvmApp app(String name, long pid, String main) {
        return new ProbeJvmApp(name, pid, main, name, "java -jar " + main, null, List.of(8080));
    }

    private static ProbeInstance probe(String id, String host, List<ProbeJvmApp> apps) {
        return new ProbeInstance(
                id,
                host,
                host,
                8765,
                "http://" + host + ":8765",
                "/sse",
                "/mcp",
                "tok",
                Map.of("env", "prod"),
                apps,
                "0.1.0",
                Instant.now(),
                ProbeStatus.HEALTHY);
    }
}

package com.javadiag.gateway.route;

import com.javadiag.gateway.registry.InMemoryRegistry;
import com.javadiag.gateway.registry.ProbeInstance;
import com.javadiag.gateway.registry.ProbeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TargetSelectorTest {

    private InMemoryRegistry registry;
    private TargetSelector selector;

    @BeforeEach
    void setUp() {
        registry = new InMemoryRegistry(Duration.ofSeconds(90));
        selector = new TargetSelector(registry);
        registry.register(probe("a", "order", Map.of("env", "prod")));
        registry.register(probe("b", "order", Map.of("env", "prod")));
        registry.register(probe("c", "payment", Map.of("env", "prod")));
    }

    @Test
    void multiMatchReturnsMany() {
        TargetSelector.Result result = selector.resolve(Map.of(
                "target", Map.of("serviceName", "order", "tags", Map.of("env", "prod"))));
        assertEquals(TargetSelector.Outcome.MANY, result.outcome());
        assertEquals(2, result.candidates().size());
    }

    @Test
    void instanceIdSelectsSingle() {
        TargetSelector.Result result = selector.resolve(Map.of(
                "target", Map.of("instanceId", "b")));
        assertEquals(TargetSelector.Outcome.SINGLE, result.outcome());
        assertEquals("b", result.candidates().get(0).instanceId());
    }

    @Test
    void noneWhenMissing() {
        TargetSelector.Result result = selector.resolve(Map.of(
                "target", Map.of("instanceId", "missing")));
        assertEquals(TargetSelector.Outcome.NONE, result.outcome());
    }

    private static ProbeInstance probe(String id, String service, Map<String, String> tags) {
        return new ProbeInstance(
                id, service, "127.0.0.1", 8765,
                "http://127.0.0.1:8765", "/sse", "/mcp",
                "tok", tags, "0.1.0", Instant.now(), ProbeStatus.HEALTHY);
    }
}

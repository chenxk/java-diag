package com.javadiag.gateway.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayConfigTest {

    @Test
    void parsesWithoutRegisterToken() {
        GatewayConfig config = GatewayConfig.from(
                Map.of(),
                List.of("--token", "agent-secret", "--port", "8788"));
        assertEquals("agent-secret", config.authToken().orElseThrow());
        assertEquals(8788, config.httpPort());
    }

    @Test
    void ignoresLegacyRegisterTokenFlag() {
        GatewayConfig config = GatewayConfig.from(
                Map.of(),
                List.of("--register-token", "ignored", "--token", "agent"));
        assertEquals("agent", config.authToken().orElseThrow());
        assertTrue(config.authToken().isPresent());
    }
}

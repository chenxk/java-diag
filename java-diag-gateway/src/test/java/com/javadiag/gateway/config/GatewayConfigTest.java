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
    void parsesRegisterTokenFromCli() {
        GatewayConfig config = GatewayConfig.from(
                Map.of(),
                List.of("--register-token", "reg-secret", "--token", "agent"));
        assertEquals("agent", config.authToken().orElseThrow());
        assertEquals("reg-secret", config.registryRegisterToken().orElseThrow());
    }

    @Test
    void parsesRegisterTokenFromEnv() {
        GatewayConfig config = GatewayConfig.from(
                Map.of(GatewayConfig.ENV_REGISTRY_REGISTER_TOKEN, "env-reg"),
                List.of());
        assertEquals("env-reg", config.registryRegisterToken().orElseThrow());
    }

    @Test
    void defaultAdminCredentials() {
        GatewayConfig config = GatewayConfig.from(Map.of(), List.of());
        assertEquals(GatewayConfig.DEFAULT_ADMIN_USER, config.adminUser());
        assertEquals(GatewayConfig.DEFAULT_ADMIN_PASSWORD, config.adminPassword());
    }

    @Test
    void parsesAdminFromCli() {
        GatewayConfig config = GatewayConfig.from(
                Map.of(),
                List.of("--admin-user", "ops", "--admin-password", "s3cret"));
        assertEquals("ops", config.adminUser());
        assertEquals("s3cret", config.adminPassword());
    }
}

package com.javadiag.gateway.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayConfigTest {

    private static Map<String, String> requiredTokens() {
        return Map.of(
                GatewayConfig.ENV_AUTH_TOKEN, "agent-secret",
                GatewayConfig.ENV_REGISTRY_REGISTER_TOKEN, "reg-secret");
    }

    private static Map<String, String> withTokens(Map<String, String> extra) {
        Map<String, String> env = new HashMap<>(requiredTokens());
        env.putAll(extra);
        return env;
    }

    @Test
    void parsesWithRequiredTokens() {
        GatewayConfig config = GatewayConfig.from(
                requiredTokens(),
                List.of("--port", "8788"));
        assertEquals("agent-secret", config.authToken().orElseThrow());
        assertEquals("reg-secret", config.registryRegisterToken().orElseThrow());
        assertEquals(8788, config.httpPort());
    }

    @Test
    void parsesRegisterTokenFromCli() {
        GatewayConfig config = GatewayConfig.from(
                Map.of(GatewayConfig.ENV_AUTH_TOKEN, "agent"),
                List.of("--register-token", "reg-secret"));
        assertEquals("agent", config.authToken().orElseThrow());
        assertEquals("reg-secret", config.registryRegisterToken().orElseThrow());
    }

    @Test
    void parsesRegisterTokenFromEnv() {
        GatewayConfig config = GatewayConfig.from(requiredTokens(), List.of());
        assertEquals("reg-secret", config.registryRegisterToken().orElseThrow());
    }

    @Test
    void rejectsMissingAuthToken() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> GatewayConfig.from(
                        Map.of(GatewayConfig.ENV_REGISTRY_REGISTER_TOKEN, "reg-secret"), List.of()));
        assertTrue(ex.getMessage().contains("auth.token"));
    }

    @Test
    void rejectsMissingRegisterToken() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> GatewayConfig.from(Map.of(GatewayConfig.ENV_AUTH_TOKEN, "agent-secret"), List.of()));
        assertTrue(ex.getMessage().contains("register.token"));
    }

    @Test
    void defaultAdminCredentials() {
        GatewayConfig config = GatewayConfig.from(requiredTokens(), List.of());
        assertEquals(GatewayConfig.DEFAULT_ADMIN_USER, config.adminUser());
        assertEquals(GatewayConfig.DEFAULT_ADMIN_PASSWORD, config.adminPassword());
    }

    @Test
    void parsesAdminFromCli() {
        GatewayConfig config = GatewayConfig.from(
                requiredTokens(),
                List.of("--admin-user", "ops", "--admin-password", "s3cret"));
        assertEquals("ops", config.adminUser());
        assertEquals("s3cret", config.adminPassword());
    }

    @Test
    void cliTokenOverridesEnv() {
        GatewayConfig config = GatewayConfig.from(
                withTokens(Map.of()),
                List.of("--token", "from-cli", "--register-token", "reg-cli"));
        assertEquals("from-cli", config.authToken().orElseThrow());
        assertEquals("reg-cli", config.registryRegisterToken().orElseThrow());
    }
}

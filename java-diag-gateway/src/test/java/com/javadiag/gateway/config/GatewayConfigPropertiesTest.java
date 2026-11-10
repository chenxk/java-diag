package com.javadiag.gateway.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayConfigPropertiesTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsPortFromProperties() throws Exception {
        Path file = tempDir.resolve("gw.properties");
        Files.writeString(file, "http.port=8799\nauth.token=from-file\nregister.token=reg-file\n");
        GatewayConfig config = GatewayConfig.load(Map.of(), List.of("--config", file.toString()));
        assertEquals(8799, config.httpPort());
        assertEquals("from-file", config.authToken().orElseThrow());
        assertEquals("reg-file", config.registryRegisterToken().orElseThrow());
    }

    @Test
    void envOverridesPropertiesCliWins() throws Exception {
        Path file = tempDir.resolve("gw.properties");
        Files.writeString(file, "http.port=8799\nauth.token=file-auth\nregister.token=file-reg\n");
        GatewayConfig config = GatewayConfig.load(
                Map.of(GatewayConfig.ENV_HTTP_PORT, "8780"),
                List.of("--config", file.toString(), "--port", "8781"));
        assertEquals(8781, config.httpPort());
    }

    @Test
    void envOverridesPropertiesWhenNoCli() throws Exception {
        Path file = tempDir.resolve("gw.properties");
        Files.writeString(file, "http.port=8799\nauth.token=file-auth\nregister.token=file-reg\n");
        GatewayConfig config = GatewayConfig.load(
                Map.of(
                        GatewayConfig.ENV_HTTP_PORT, "8780",
                        GatewayConfig.ENV_REGISTRY_REGISTER_TOKEN, "env-reg"),
                List.of("--config", file.toString()));
        assertEquals(8780, config.httpPort());
        assertEquals("env-reg", config.registryRegisterToken().orElseThrow());
    }

    @Test
    void missingAuthTokenInFileFails() throws Exception {
        Path file = tempDir.resolve("gw.properties");
        Files.writeString(file, "http.port=8799\nregister.token=reg-only\n");
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> GatewayConfig.load(Map.of(), List.of("--config", file.toString())));
        assertTrue(ex.getMessage().contains("auth.token"));
    }

    @Test
    void explicitConfigMissingFails() {
        Path missing = tempDir.resolve("missing.properties");
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> GatewayConfig.load(Map.of(), List.of("--config", missing.toString())));
        assertTrue(ex.getMessage().contains("Config file not found"));
    }
}

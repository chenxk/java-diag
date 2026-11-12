package com.javadiag.probe.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagConfigPropertiesTest {

    @TempDir
    Path tempDir;

    private static Map<String, String> requiredTokens() {
        return Map.of(
                DiagConfig.ENV_AUTH_TOKEN, "test-auth",
                DiagConfig.ENV_REGISTRY_REGISTER_TOKEN, "test-reg");
    }

    @Test
    void defaultsWithoutConfigFile() {
        DiagConfig config = DiagConfig.load(requiredTokens(), List.of());
        assertEquals(8765, config.httpPort());
        assertEquals(DiagConfig.Transport.HTTP, config.transport());
    }

    @Test
    void loadsFromTempPropertiesFile() throws Exception {
        Path file = tempDir.resolve("probe.properties");
        Files.writeString(
                file,
                """
                        http.port=9901
                        transport=stdio
                        auth.token=file-secret
                        register.token=file-reg
                        """);
        DiagConfig config = DiagConfig.load(Map.of(), List.of("--config", file.toString()));
        assertEquals(9901, config.httpPort());
        assertEquals(DiagConfig.Transport.STDIO, config.transport());
        assertEquals("file-secret", config.authToken().orElseThrow());
        assertEquals("file-reg", config.registryRegisterToken().orElseThrow());
    }

    @Test
    void envOverridesProperties() throws Exception {
        Path file = tempDir.resolve("probe.properties");
        Files.writeString(file, "http.port=9901\nauth.token=file-secret\nregister.token=file-reg\n");
        DiagConfig config = DiagConfig.load(
                Map.of(DiagConfig.ENV_HTTP_PORT, "8800", DiagConfig.ENV_AUTH_TOKEN, "env-secret"),
                List.of("--config", file.toString()));
        assertEquals(8800, config.httpPort());
        assertEquals("env-secret", config.authToken().orElseThrow());
        assertEquals("file-reg", config.registryRegisterToken().orElseThrow());
    }

    @Test
    void cliOverridesPropertiesAndEnv() throws Exception {
        Path file = tempDir.resolve("probe.properties");
        Files.writeString(file, "http.port=9901\nauth.token=file-auth\nregister.token=file-reg\n");
        DiagConfig config = DiagConfig.load(
                Map.of(DiagConfig.ENV_HTTP_PORT, "8800"),
                List.of("--config", file.toString(), "--port", "7700"));
        assertEquals(7700, config.httpPort());
    }

    @Test
    void rejectsMissingAuthTokenInProperties() throws Exception {
        Path file = tempDir.resolve("probe.properties");
        Files.writeString(file, "http.port=9901\nregister.token=file-reg\n");
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> DiagConfig.load(Map.of(), List.of("--config", file.toString())));
        assertTrue(ex.getMessage().contains("auth.token"));
    }

    @Test
    void explicitConfigMissingFails() {
        Path missing = tempDir.resolve("no-such.properties");
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> DiagConfig.load(requiredTokens(), List.of("--config", missing.toString())));
        assertTrue(ex.getMessage().contains("Config file not found"));
    }

    @Test
    void fromEnvironmentAcceptsFileSettingsDirectly() {
        DiagConfig config = DiagConfig.fromEnvironment(
                requiredTokens(),
                List.of(),
                Map.of(DiagConfig.PROP_REGISTER_URL, "http://gateway:8787"));
        assertEquals("http://gateway:8787", config.registerUrl().orElseThrow());
    }

    @Test
    void dumpTimeoutFromPropertiesFile() throws Exception {
        Path file = tempDir.resolve("probe.properties");
        Files.writeString(file, "dump.timeout.ms=45000\nauth.token=file-auth\nregister.token=file-reg\n");
        DiagConfig config = DiagConfig.load(Map.of(), List.of("--config", file.toString()));
        assertEquals(45_000L, config.dumpTimeoutMs());
    }

    @Test
    void legacyEnvKeyInPropertiesFile() throws Exception {
        Path file = tempDir.resolve("probe.properties");
        Files.writeString(
                file,
                "JAVA_DIAG_HTTP_PORT=9910\nJAVA_DIAG_AUTH_TOKEN=legacy-auth\nJAVA_DIAG_REGISTRY_REGISTER_TOKEN=legacy-reg\n");
        DiagConfig config = DiagConfig.load(Map.of(), List.of("--config", file.toString()));
        assertEquals(9910, config.httpPort());
        assertEquals("legacy-auth", config.authToken().orElseThrow());
        assertEquals("legacy-reg", config.registryRegisterToken().orElseThrow());
    }

    @Test
    void loadsEveryPropertyKeyFromFile() throws Exception {
        Path logDir = tempDir.resolve("logs");
        Files.createDirectories(logDir);
        Path audit = tempDir.resolve("custom-audit.log");
        Path file = tempDir.resolve("full.properties");
        Files.writeString(
                file,
                """
                        transport=stdio
                        http.host=127.0.0.1
                        http.port=9999
                        auth.token=probe-token
                        allowed.log.dirs=%s
                        allowed.main.class.regex=com\\.example\\..*
                        tool.timeout.ms=12000
                        dump.timeout.ms=25000
                        max.output.bytes=4096
                        dangerous.tools.enabled=true
                        audit.path=%s
                        dump.cooldown.ms=5000
                        service.name=my-service
                        register.url=http://gateway:8787
                        register.token=registry-secret
                        advertise.url=http://10.0.0.2:9999
                        heartbeat.interval.seconds=30
                        tags=env=prod,zone=a
                        app.name.map=OrderMain=order
                        """
                        .formatted(logDir.toString().replace('\\', '/'), audit.toString().replace('\\', '/')));
        DiagConfig config = DiagConfig.load(Map.of(), List.of("--config", file.toString()));

        assertEquals(DiagConfig.Transport.STDIO, config.transport());
        assertEquals("127.0.0.1", config.httpHost());
        assertEquals(9999, config.httpPort());
        assertEquals("probe-token", config.authToken().orElseThrow());
        assertEquals(1, config.allowedLogDirs().size());
        assertEquals(logDir.toAbsolutePath().normalize(), config.allowedLogDirs().get(0));
        assertTrue(config.allowedMainClassRegex().isPresent());
        assertEquals(12_000L, config.toolTimeoutMs());
        assertEquals(25_000L, config.dumpTimeoutMs());
        assertEquals(4096, config.maxOutputBytes());
        assertTrue(config.dangerousToolsEnabled());
        assertEquals(audit.toAbsolutePath().normalize(), config.auditPath());
        assertEquals(5000L, config.dumpCooldownMs());
        assertEquals("my-service", config.serviceName());
        assertEquals("http://gateway:8787", config.registerUrl().orElseThrow());
        assertEquals("registry-secret", config.registryRegisterToken().orElseThrow());
        assertEquals("http://10.0.0.2:9999", config.advertiseUrl().orElseThrow());
        assertEquals(30L, config.heartbeatIntervalSeconds());
        assertEquals("prod", config.tags().get("env"));
        assertEquals("a", config.tags().get("zone"));
        assertEquals(1, config.appNameMapper().rules().size());
        assertEquals("order", config.appNameMapper().rules().get(0).serviceName());
        assertEquals("10.0.0.2:9999", config.instanceId());
    }
}

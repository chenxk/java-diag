package com.javadiag.gateway.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayConfigClasspathTest {

    @TempDir
    Path tempDir;

    @Test
    void noConfigLoadsPortFromClasspath() throws Exception {
        GatewayConfig config = load(Map.of(), List.of(), tempDir, """
                http.port=8791
                auth.token=classpath-auth
                register.token=classpath-reg
                """);
        assertEquals(8791, config.httpPort());
        assertEquals("classpath-auth", config.authToken().orElseThrow());
    }

    @Test
    void cwdFileOverridesClasspathButKeepsUnsetKeys() throws Exception {
        Files.writeString(tempDir.resolve(SettingsProperties.DEFAULT_FILENAME), "http.port=8808\n");
        GatewayConfig config = load(Map.of(), List.of(), tempDir, """
                http.port=8791
                auth.token=classpath-auth
                register.token=classpath-reg
                """);
        assertEquals(8808, config.httpPort());
        assertEquals("classpath-auth", config.authToken().orElseThrow());
    }

    @Test
    void propertiesLocalNearCwdOverridesClasspath() throws Exception {
        Path moduleDir = Files.createDirectories(tempDir.resolve("java-diag-gateway"));
        Files.writeString(tempDir.resolve("java-diag-gateway.properties.local"), "http.port=8810\n");
        GatewayConfig config = load(Map.of(), List.of(), moduleDir, """
                http.port=8791
                auth.token=classpath-auth
                register.token=classpath-reg
                """);
        assertEquals(8810, config.httpPort());
        assertEquals("classpath-auth", config.authToken().orElseThrow());
    }

    @Test
    void nearerLocalOverridesParent() throws Exception {
        Path child = Files.createDirectories(tempDir.resolve("child"));
        Files.writeString(tempDir.resolve("java-diag-gateway.properties.local"), "http.port=8111\n");
        Files.writeString(child.resolve("java-diag-gateway.properties.local"), "http.port=8222\n");
        GatewayConfig config = load(Map.of(), List.of(), child, """
                http.port=8791
                auth.token=classpath-auth
                register.token=classpath-reg
                """);
        assertEquals(8222, config.httpPort());
    }

    @Test
    void envOverridesFileAndCliWins() throws Exception {
        String body = """
                http.port=8791
                auth.token=classpath-auth
                register.token=classpath-reg
                """;
        GatewayConfig fromEnv = load(
                Map.of(GatewayConfig.ENV_HTTP_PORT, "8780"), List.of(), tempDir, body);
        assertEquals(8780, fromEnv.httpPort());

        GatewayConfig fromCli = load(
                Map.of(GatewayConfig.ENV_HTTP_PORT, "8780"),
                List.of("--port", "8781"),
                tempDir,
                body);
        assertEquals(8781, fromCli.httpPort());
    }

    @Test
    void explicitConfigReplacesClasspathLayer() throws Exception {
        Path explicit = tempDir.resolve("explicit.properties");
        Files.writeString(explicit, """
                http.port=8802
                auth.token=explicit-auth
                register.token=explicit-reg
                """);
        GatewayConfig config = load(
                Map.of(),
                List.of("--config", explicit.toAbsolutePath().toString()),
                tempDir,
                """
                        http.port=8791
                        auth.token=classpath-auth
                        register.token=classpath-reg
                        admin.user=from-classpath
                        heartbeat.ttl.seconds=120
                        """);
        assertEquals(8802, config.httpPort());
        assertEquals("explicit-auth", config.authToken().orElseThrow());
        assertEquals(GatewayConfig.DEFAULT_ADMIN_USER, config.adminUser());
        assertEquals(90L, config.heartbeatTtlSeconds());
    }

    @Test
    void explicitConfigDoesNotKeepClasspathToken() throws Exception {
        Path explicit = tempDir.resolve("no-token.properties");
        Files.writeString(explicit, "http.port=8802\nregister.token=explicit-reg\n");
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> load(
                        Map.of(),
                        List.of("--config", explicit.toAbsolutePath().toString()),
                        tempDir,
                        "http.port=8791\nauth.token=classpath-auth\nregister.token=classpath-reg\n"));
        assertTrue(ex.getMessage().contains("auth.token"));
    }

    @Test
    void publicLoadExplicitConfigIgnoresBundledAdminPassword() throws Exception {
        Path explicit = tempDir.resolve("explicit.properties");
        Files.writeString(explicit, """
                http.port=8802
                auth.token=explicit-auth
                register.token=explicit-reg
                """);
        GatewayConfig config = GatewayConfig.load(Map.of(), List.of("--config", explicit.toAbsolutePath().toString()));
        assertEquals(8802, config.httpPort());
        assertEquals("java-diag", config.adminPassword());
    }

    private GatewayConfig load(Map<String, String> env, List<String> args, Path cwd, String body) throws Exception {
        try (URLClassLoader loader = classpathLoader(body)) {
            SettingsProperties.ConfigLayer layer = SettingsProperties.loadConfigLayer(args, cwd, loader);
            return GatewayConfig.from(env, layer.cliArgsWithoutConfig(), layer.fileSettings());
        }
    }

    private URLClassLoader classpathLoader(String body) throws Exception {
        Path cp = Files.createDirectories(tempDir.resolve("cp-" + System.nanoTime()));
        Files.writeString(cp.resolve(SettingsProperties.CLASSPATH_RESOURCE), body);
        return new URLClassLoader(new URL[] {cp.toUri().toURL()}, null);
    }
}

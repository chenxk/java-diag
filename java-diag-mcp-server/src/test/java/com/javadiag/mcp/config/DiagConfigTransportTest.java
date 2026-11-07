package com.javadiag.mcp.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagConfigTransportTest {

    @Test
    void defaultsToHttp() {
        DiagConfig config = DiagConfig.fromEnvironment(Map.of(), List.of());
        assertEquals(DiagConfig.Transport.HTTP, config.transport());
        assertEquals("0.0.0.0", config.httpHost());
        assertEquals(8765, config.httpPort());
    }

    @Test
    void cliStdioOverridesEnv() {
        DiagConfig config = DiagConfig.fromEnvironment(
                Map.of(DiagConfig.ENV_TRANSPORT, "http"),
                List.of("--stdio"));
        assertEquals(DiagConfig.Transport.STDIO, config.transport());
    }

    @Test
    void cliPortAndHost() {
        DiagConfig config = DiagConfig.fromEnvironment(
                Map.of(),
                List.of("--http", "--host", "127.0.0.1", "--port", "9999"));
        assertEquals(DiagConfig.Transport.HTTP, config.transport());
        assertEquals("127.0.0.1", config.httpHost());
        assertEquals(9999, config.httpPort());
    }

    @Test
    void cliTokenOverridesEnv() {
        DiagConfig config = DiagConfig.fromEnvironment(
                Map.of(DiagConfig.ENV_AUTH_TOKEN, "from-env"),
                List.of("--token", "from-cli"));
        assertEquals("from-cli", config.authToken().orElseThrow());
    }

    @Test
    void authTokenAliasWorks() {
        DiagConfig config = DiagConfig.fromEnvironment(
                Map.of(),
                List.of("--auth-token", "alias-secret"));
        assertEquals("alias-secret", config.authToken().orElseThrow());
    }

    @Test
    void tokenFlagRequiresValue() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> DiagConfig.fromEnvironment(Map.of(), List.of("--token")));
        assertTrue(ex.getMessage().contains("--token"));
    }

    @Test
    void registerUrlDoesNotRequireRegisterToken() {
        DiagConfig config = DiagConfig.fromEnvironment(
                Map.of(),
                List.of("--register-url", "http://gateway:8787", "--service-name", "order"));
        assertEquals("http://gateway:8787", config.registerUrl().orElseThrow());
        assertEquals("order", config.serviceName());
        assertTrue(config.registryRegisterToken().isEmpty());
    }

    @Test
    void parsesRegisterTokenFromCli() {
        DiagConfig config = DiagConfig.fromEnvironment(
                Map.of(),
                List.of("--register-token", "reg-secret", "--register-url", "http://gateway:8787"));
        assertEquals("reg-secret", config.registryRegisterToken().orElseThrow());
    }

    @Test
    void cliRegisterTokenOverridesEnv() {
        DiagConfig config = DiagConfig.fromEnvironment(
                Map.of(DiagConfig.ENV_REGISTRY_REGISTER_TOKEN, "from-env"),
                List.of("--register-token", "from-cli"));
        assertEquals("from-cli", config.registryRegisterToken().orElseThrow());
    }

    @Test
    void parsesRegisterTokenFromEnvWhenCliAbsent() {
        DiagConfig config = DiagConfig.fromEnvironment(
                Map.of(DiagConfig.ENV_REGISTRY_REGISTER_TOKEN, "env-reg"),
                List.of());
        assertEquals("env-reg", config.registryRegisterToken().orElseThrow());
    }

    @Test
    void registerTokenFlagRequiresValue() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> DiagConfig.fromEnvironment(Map.of(), List.of("--register-token")));
        assertTrue(ex.getMessage().contains("--register-token"));
    }

    @Test
    void serviceNameDefaultsToPrimaryIpWhenUnset() {
        DiagConfig config = DiagConfig.fromEnvironment(Map.of(), List.of());
        assertEquals(DiagConfig.primaryIpAddress(), config.serviceName());
        assertTrue(looksLikeIp(config.serviceName()),
                "default serviceName should be an IP, got: " + config.serviceName());
    }

    @Test
    void serviceNameFromCliOverridesDefault() {
        DiagConfig config = DiagConfig.fromEnvironment(
                Map.of(),
                List.of("--service-name", "order-service"));
        assertEquals("order-service", config.serviceName());
    }

    @Test
    void serviceNameFromEnvOverridesDefault() {
        DiagConfig config = DiagConfig.fromEnvironment(
                Map.of(DiagConfig.ENV_SERVICE_NAME, "from-env"),
                List.of());
        assertEquals("from-env", config.serviceName());
    }

    @Test
    void registerUrlWorksWithoutServiceName() {
        DiagConfig config = DiagConfig.fromEnvironment(
                Map.of(),
                List.of("--register-url", "http://gateway:8787"));
        assertEquals("http://gateway:8787", config.registerUrl().orElseThrow());
        assertEquals(DiagConfig.primaryIpAddress(), config.serviceName());
    }

    @Test
    void instanceIdDerivedFromAdvertiseUrl() {
        DiagConfig config = DiagConfig.fromEnvironment(
                Map.of(),
                List.of("--advertise-url", "http://10.0.1.11:8765"));
        assertEquals("10.0.1.11:8765", config.instanceId());
    }

    @Test
    void instanceIdDerivedFromHostPortWhenNoAdvertiseUrl() {
        DiagConfig config = DiagConfig.fromEnvironment(
                Map.of(),
                List.of("--host", "192.168.1.5", "--port", "9001"));
        assertEquals("192.168.1.5:9001", config.instanceId());
    }

    @Test
    void instanceIdUsesPrimaryIpAndPortWhenBoundAllInterfaces() {
        DiagConfig config = DiagConfig.fromEnvironment(Map.of(), List.of());
        assertEquals(DiagConfig.primaryIpAddress() + ":8765", config.instanceId());
    }

    @Test
    void instanceIdEnvAndCliRemoved_ignoredAsUnknown() {
        // JAVA_DIAG_INSTANCE_ID / --instance-id no longer exist; advertise URL wins.
        DiagConfig config = DiagConfig.fromEnvironment(
                Map.of("JAVA_DIAG_INSTANCE_ID", "should-be-ignored"),
                List.of("--instance-id", "also-ignored", "--advertise-url", "http://10.0.1.11:8765"));
        assertEquals("10.0.1.11:8765", config.instanceId());
    }

    @Test
    void registerUrlWorksWithoutSeparateInstanceId() {
        DiagConfig config = DiagConfig.fromEnvironment(
                Map.of(),
                List.of(
                        "--register-url", "http://gateway:8787",
                        "--advertise-url", "http://10.0.1.11:8765"));
        assertEquals("http://gateway:8787", config.registerUrl().orElseThrow());
        assertEquals("10.0.1.11:8765", config.instanceId());
        assertEquals(DiagConfig.primaryIpAddress(), config.serviceName());
    }

    private static boolean looksLikeIp(String value) {
        // IPv4 dotted-quad or IPv6 (contains ':'); reject bare hostnames
        if (value == null || value.isBlank()) {
            return false;
        }
        if (value.contains(":")) {
            return true;
        }
        String[] parts = value.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            try {
                int n = Integer.parseInt(part);
                if (n < 0 || n > 255) {
                    return false;
                }
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return true;
    }
}

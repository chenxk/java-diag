package com.javadiag.probe.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagConfigTransportTest {

    private static Map<String, String> requiredTokens() {
        return Map.of(DiagConfig.ENV_AUTH_TOKEN, "test-auth");
    }

    private static Map<String, String> withTokens(Map<String, String> extra) {
        Map<String, String> env = new HashMap<>(requiredTokens());
        env.putAll(extra);
        return env;
    }

    private static Map<String, String> withRegisterTokens(Map<String, String> extra) {
        Map<String, String> env = withTokens(Map.of(DiagConfig.ENV_REGISTRY_REGISTER_TOKEN, "test-reg"));
        env.putAll(extra);
        return env;
    }

    @Test
    void defaultsToHttp() {
        DiagConfig config = DiagConfig.fromEnvironment(requiredTokens(), List.of());
        assertEquals(DiagConfig.Transport.HTTP, config.transport());
        assertEquals("0.0.0.0", config.httpHost());
        assertEquals(8765, config.httpPort());
        assertEquals("test-auth", config.authToken().orElseThrow());
        assertTrue(config.registryRegisterToken().isEmpty());
        assertTrue(config.registerUrl().isEmpty());
    }

    @Test
    void rejectsMissingAuthToken() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> DiagConfig.fromEnvironment(Map.of(), List.of()));
        assertTrue(ex.getMessage().contains("auth.token"));
    }

    @Test
    void allowsMissingRegisterTokenWhenNoRegisterUrl() {
        DiagConfig config = DiagConfig.fromEnvironment(
                Map.of(DiagConfig.ENV_AUTH_TOKEN, "test-auth"), List.of());
        assertTrue(config.registerUrl().isEmpty());
        assertTrue(config.registryRegisterToken().isEmpty());
    }

    @Test
    void cliStdioOverridesEnv() {
        DiagConfig config = DiagConfig.fromEnvironment(
                withTokens(Map.of(DiagConfig.ENV_TRANSPORT, "http")),
                List.of("--stdio"));
        assertEquals(DiagConfig.Transport.STDIO, config.transport());
    }

    @Test
    void cliPortAndHost() {
        DiagConfig config = DiagConfig.fromEnvironment(
                requiredTokens(),
                List.of("--http", "--host", "127.0.0.1", "--port", "9999"));
        assertEquals(DiagConfig.Transport.HTTP, config.transport());
        assertEquals("127.0.0.1", config.httpHost());
        assertEquals(9999, config.httpPort());
    }

    @Test
    void cliAuthTokenOverridesEnv() {
        DiagConfig config = DiagConfig.fromEnvironment(
                withTokens(Map.of(DiagConfig.ENV_AUTH_TOKEN, "from-env")),
                List.of("--auth-token", "from-cli"));
        assertEquals("from-cli", config.authToken().orElseThrow());
    }

    @Test
    void authTokenFlagWorks() {
        DiagConfig config = DiagConfig.fromEnvironment(Map.of(), List.of("--auth-token", "alias-secret"));
        assertEquals("alias-secret", config.authToken().orElseThrow());
    }

    @Test
    void authTokenFlagRequiresValue() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> DiagConfig.fromEnvironment(Map.of(), List.of("--auth-token")));
        assertTrue(ex.getMessage().contains("--auth-token"));
    }

    @Test
    void registerUrlRequiresRegisterToken() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> DiagConfig.fromEnvironment(
                        Map.of(DiagConfig.ENV_AUTH_TOKEN, "test-auth"),
                        List.of("--register-url", "http://gateway:8787", "--service-name", "order")));
        assertTrue(ex.getMessage().contains("register.token"));
    }

    @Test
    void parsesRegisterTokenFromCli() {
        DiagConfig config = DiagConfig.fromEnvironment(
                Map.of(DiagConfig.ENV_AUTH_TOKEN, "test-auth"),
                List.of("--register-token", "reg-secret", "--register-url", "http://gateway:8787"));
        assertEquals("reg-secret", config.registryRegisterToken().orElseThrow());
    }

    @Test
    void cliRegisterTokenOverridesEnv() {
        DiagConfig config = DiagConfig.fromEnvironment(
                withRegisterTokens(Map.of(DiagConfig.ENV_REGISTRY_REGISTER_TOKEN, "from-env")),
                List.of("--register-token", "from-cli", "--register-url", "http://gateway:8787"));
        assertEquals("from-cli", config.registryRegisterToken().orElseThrow());
    }

    @Test
    void parsesRegisterTokenFromEnvWhenCliAbsent() {
        DiagConfig config = DiagConfig.fromEnvironment(
                withRegisterTokens(Map.of()),
                List.of("--register-url", "http://gateway:8787"));
        assertEquals("test-reg", config.registryRegisterToken().orElseThrow());
    }

    @Test
    void optionalRegisterTokenIgnoredWhenNoRegisterUrl() {
        DiagConfig config = DiagConfig.fromEnvironment(
                withRegisterTokens(Map.of()), List.of());
        assertTrue(config.registerUrl().isEmpty());
        assertEquals("test-reg", config.registryRegisterToken().orElseThrow());
    }

    @Test
    void registerTokenFlagRequiresValue() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> DiagConfig.fromEnvironment(
                        Map.of(DiagConfig.ENV_AUTH_TOKEN, "test-auth"), List.of("--register-token")));
        assertTrue(ex.getMessage().contains("--register-token"));
    }

    @Test
    void serviceNameDefaultsToPrimaryIpWhenUnset() {
        DiagConfig config = DiagConfig.fromEnvironment(requiredTokens(), List.of());
        assertEquals(DiagConfig.primaryIpAddress(), config.serviceName());
        assertTrue(looksLikeIp(config.serviceName()),
                "default serviceName should be an IP, got: " + config.serviceName());
    }

    @Test
    void serviceNameFromCliOverridesDefault() {
        DiagConfig config = DiagConfig.fromEnvironment(
                requiredTokens(),
                List.of("--service-name", "order-service"));
        assertEquals("order-service", config.serviceName());
    }

    @Test
    void serviceNameFromEnvOverridesDefault() {
        DiagConfig config = DiagConfig.fromEnvironment(
                withTokens(Map.of(DiagConfig.ENV_SERVICE_NAME, "from-env")),
                List.of());
        assertEquals("from-env", config.serviceName());
    }

    @Test
    void registerUrlWorksWithoutServiceName() {
        DiagConfig config = DiagConfig.fromEnvironment(
                withRegisterTokens(Map.of()),
                List.of("--register-url", "http://gateway:8787"));
        assertEquals("http://gateway:8787", config.registerUrl().orElseThrow());
        assertEquals(DiagConfig.primaryIpAddress(), config.serviceName());
    }

    @Test
    void instanceIdDerivedFromAdvertiseUrl() {
        DiagConfig config = DiagConfig.fromEnvironment(
                requiredTokens(),
                List.of("--advertise-url", "http://10.0.1.11:8765"));
        assertEquals("10.0.1.11:8765", config.instanceId());
    }

    @Test
    void instanceIdDerivedFromHostPortWhenNoAdvertiseUrl() {
        DiagConfig config = DiagConfig.fromEnvironment(
                requiredTokens(),
                List.of("--host", "192.168.1.5", "--port", "9001"));
        assertEquals("192.168.1.5:9001", config.instanceId());
    }

    @Test
    void instanceIdUsesPrimaryIpAndPortWhenBoundAllInterfaces() {
        DiagConfig config = DiagConfig.fromEnvironment(requiredTokens(), List.of());
        assertEquals(DiagConfig.primaryIpAddress() + ":8765", config.instanceId());
    }

    @Test
    void instanceIdEnvAndCliRemoved_ignoredAsUnknown() {
        DiagConfig config = DiagConfig.fromEnvironment(
                withTokens(Map.of("JAVA_DIAG_INSTANCE_ID", "should-be-ignored")),
                List.of("--instance-id", "also-ignored", "--advertise-url", "http://10.0.1.11:8765"));
        assertEquals("10.0.1.11:8765", config.instanceId());
    }

    @Test
    void registerUrlWorksWithoutSeparateInstanceId() {
        DiagConfig config = DiagConfig.fromEnvironment(
                withRegisterTokens(Map.of()),
                List.of(
                        "--register-url", "http://gateway:8787",
                        "--advertise-url", "http://10.0.1.11:8765"));
        assertEquals("http://gateway:8787", config.registerUrl().orElseThrow());
        assertEquals("10.0.1.11:8765", config.instanceId());
        assertEquals(DiagConfig.primaryIpAddress(), config.serviceName());
    }

    private static boolean looksLikeIp(String value) {
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

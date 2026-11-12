package com.javadiag.gateway.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledPropertiesExampleTest {

    private static final List<String> REQUIRED_KEYS = List.of(
            GatewayConfig.PROP_HTTP_HOST,
            GatewayConfig.PROP_HTTP_PORT,
            GatewayConfig.PROP_AUTH_TOKEN,
            GatewayConfig.PROP_HEARTBEAT_TTL,
            GatewayConfig.PROP_INSTANCE_ID,
            GatewayConfig.PROP_AUDIT_PATH,
            GatewayConfig.PROP_REGISTER_TOKEN,
            GatewayConfig.PROP_DUMP_COOLDOWN_MS,
            GatewayConfig.PROP_ADMIN_USER,
            GatewayConfig.PROP_ADMIN_PASSWORD);

    @Test
    void bundledExampleListsEveryPropertyKey() throws Exception {
        Properties props = loadBundledExample();
        for (String key : REQUIRED_KEYS) {
            assertTrue(props.containsKey(key), "missing key in java-diag-gateway.properties.example: " + key);
        }
    }

    @Test
    void bundledExampleDefaultsMatchBuiltinDefaults() throws Exception {
        Map<String, String> fromExample = loadBundledExample().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString()));
        assertFalse(fromExample.get(GatewayConfig.PROP_AUTH_TOKEN).isBlank(), "auth.token must be non-empty in example");
        assertFalse(
                fromExample.get(GatewayConfig.PROP_REGISTER_TOKEN).isBlank(),
                "register.token must be non-empty in example");

        GatewayConfig fromFile = GatewayConfig.from(Map.of(), List.of(), fromExample);
        GatewayConfig builtin = GatewayConfig.from(
                Map.of(
                        GatewayConfig.ENV_AUTH_TOKEN, "test-auth",
                        GatewayConfig.ENV_REGISTRY_REGISTER_TOKEN, "test-reg"),
                List.of(),
                Map.of());

        assertEquals(builtin.httpHost(), fromFile.httpHost());
        assertEquals(builtin.httpPort(), fromFile.httpPort());
        assertEquals(builtin.heartbeatTtlSeconds(), fromFile.heartbeatTtlSeconds());
        assertEquals(builtin.dumpCooldownMs(), fromFile.dumpCooldownMs());
        assertEquals(GatewayConfig.DEFAULT_ADMIN_USER, fromFile.adminUser());
        String examplePassword = fromExample.get(GatewayConfig.PROP_ADMIN_PASSWORD);
        assertFalse(examplePassword.isBlank(), "admin.password must be non-blank in example");
        assertEquals(
                GatewayConfig.EXAMPLE_ADMIN_PASSWORD_PLACEHOLDER,
                examplePassword,
                "example admin.password should be a CHANGE_ME placeholder, not the runtime default");
        assertTrue(GatewayConfig.isWeakAdminPassword(examplePassword));
    }

    private static Properties loadBundledExample() throws Exception {
        try (InputStream in =
                BundledPropertiesExampleTest.class.getResourceAsStream("/java-diag-gateway.properties.example")) {
            assertNotNull(in, "java-diag-gateway.properties.example missing from classpath");
            Properties props = new Properties();
            props.load(in);
            return props;
        }
    }
}

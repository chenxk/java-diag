package com.javadiag.probe.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledPropertiesExampleTest {

    @Test
    void bundledExampleListsEveryPropertyKey() throws Exception {
        List<String> requiredKeys = allPropertyKeysFromDiagConfig();
        assertEquals(19, requiredKeys.size(), "DiagConfig PROP_* count drifted; update properties.example");
        Properties props = loadBundledExample();
        for (String key : requiredKeys) {
            assertTrue(props.containsKey(key), "missing key in java-diag-probe.properties.example: " + key);
        }
    }

    @Test
    void envAndPropertyKeyCountsMatch() {
        assertEquals(
                allEnvKeysFromDiagConfig().size(),
                allPropertyKeysFromDiagConfig().size(),
                "each JAVA_DIAG_* should have a PROP_* dot-key in DiagConfig");
    }

    private static List<String> allPropertyKeysFromDiagConfig() {
        return Arrays.stream(DiagConfig.class.getFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()))
                .filter(f -> f.getName().startsWith("PROP_"))
                .filter(f -> f.getType() == String.class)
                .map(f -> {
                    try {
                        return (String) f.get(null);
                    } catch (ReflectiveOperationException ex) {
                        throw new RuntimeException(ex);
                    }
                })
                .sorted()
                .toList();
    }

    private static List<String> allEnvKeysFromDiagConfig() {
        return Arrays.stream(DiagConfig.class.getFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()))
                .filter(f -> f.getName().startsWith("ENV_"))
                .filter(f -> f.getType() == String.class)
                .map(f -> {
                    try {
                        return (String) f.get(null);
                    } catch (ReflectiveOperationException ex) {
                        throw new RuntimeException(ex);
                    }
                })
                .sorted()
                .toList();
    }

    @Test
    void bundledExampleDefaultsMatchBuiltinDefaults() throws Exception {
        Map<String, String> fromExample = loadBundledExample().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString()));
        assertFalse(
                fromExample.get(DiagConfig.PROP_AUTH_TOKEN).isBlank(), "auth.token must be non-empty in example");
        // register.url empty in example → register.token optional; sample value may still be present
        String exampleRegisterUrl = fromExample.getOrDefault(DiagConfig.PROP_REGISTER_URL, "");
        if (!exampleRegisterUrl.isBlank()) {
            assertFalse(
                    fromExample.get(DiagConfig.PROP_REGISTER_TOKEN).isBlank(),
                    "register.token must be non-empty when register.url is set in example");
        }

        DiagConfig fromFile = DiagConfig.fromEnvironment(Map.of(), List.of(), fromExample);
        DiagConfig builtin = DiagConfig.fromEnvironment(
                Map.of(DiagConfig.ENV_AUTH_TOKEN, "test-auth"),
                List.of(),
                Map.of());

        assertEquals(builtin.transport(), fromFile.transport());
        assertEquals(builtin.httpHost(), fromFile.httpHost());
        assertEquals(builtin.httpPort(), fromFile.httpPort());
        assertEquals(builtin.toolTimeoutMs(), fromFile.toolTimeoutMs());
        assertEquals(builtin.dumpTimeoutMs(), fromFile.dumpTimeoutMs());
        assertEquals(builtin.maxOutputBytes(), fromFile.maxOutputBytes());
        assertEquals(builtin.dangerousToolsEnabled(), fromFile.dangerousToolsEnabled());
        assertEquals(builtin.heartbeatIntervalSeconds(), fromFile.heartbeatIntervalSeconds());
        assertEquals(builtin.dumpCooldownMs(), fromFile.dumpCooldownMs());
        assertEquals("30000", fromExample.get(DiagConfig.PROP_DUMP_TIMEOUT_MS));
    }

    private static Properties loadBundledExample() throws Exception {
        try (InputStream in =
                BundledPropertiesExampleTest.class.getResourceAsStream("/java-diag-probe.properties.example")) {
            assertNotNull(in, "java-diag-probe.properties.example missing from classpath");
            Properties props = new Properties();
            props.load(in);
            return props;
        }
    }
}

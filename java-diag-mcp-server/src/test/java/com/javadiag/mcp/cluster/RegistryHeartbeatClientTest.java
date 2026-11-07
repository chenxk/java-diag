package com.javadiag.mcp.cluster;

import com.javadiag.mcp.config.DiagConfig;
import com.javadiag.mcp.platform.JsonCodec;
import com.javadiag.mcp.process.JavaProcessService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryHeartbeatClientTest {

    @Test
    void registerPayloadIncludesProbeTokenWhenConfigured() throws Exception {
        DiagConfig config = DiagConfig.fromEnvironment(
                Map.of(
                        DiagConfig.ENV_AUTH_TOKEN, "probe-secret",
                        DiagConfig.ENV_SERVICE_NAME, "order"),
                List.of("--port", "8765", "--register-url", "http://127.0.0.1:8787"));
        RegistryHeartbeatClient client = new RegistryHeartbeatClient(config, JsonCodec.createDefault());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload =
                (Map<String, Object>) invokeBuildPayload(client);

        assertEquals("probe-secret", payload.get("probeToken"));
        assertTrue(payload.containsKey("mcpBaseUrl"));
        assertEquals("order", payload.get("serviceName"));
    }

    @Test
    void registerPayloadOmitsProbeTokenWhenUnset() throws Exception {
        DiagConfig config = DiagConfig.fromEnvironment(
                Map.of(), List.of("--port", "8765", "--register-url", "http://127.0.0.1:8787"));
        RegistryHeartbeatClient client = new RegistryHeartbeatClient(config, JsonCodec.createDefault());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload =
                (Map<String, Object>) invokeBuildPayload(client);

        assertNull(payload.get("probeToken"));
    }

    @Test
    void registerPayloadMayIncludeAppsWhenProcessServiceAttached() throws Exception {
        DiagConfig config = DiagConfig.fromEnvironment(
                Map.of(
                        DiagConfig.ENV_APP_NAME_MAP, "java-diag=java-diag",
                        DiagConfig.ENV_SERVICE_NAME, "order"),
                List.of("--port", "8765", "--register-url", "http://127.0.0.1:8787"));
        JavaProcessService processes = new JavaProcessService(Optional.empty());
        RegistryHeartbeatClient client = new RegistryHeartbeatClient(config, JsonCodec.createDefault(), processes);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload =
                (Map<String, Object>) invokeBuildPayload(client);

        Object apps = payload.get("apps");
        if (apps instanceof List<?> list) {
            assertTrue(list.size() <= JvmDirectoryBuilder.MAX_APPS);
            for (Object item : list) {
                assertTrue(item instanceof Map<?, ?>);
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) item;
                assertTrue(map.get("pid") != null);
                assertTrue(map.containsKey("name"));
                assertFalse(map.containsKey("probeToken"));
            }
        }
    }

    private static Object invokeBuildPayload(RegistryHeartbeatClient client) throws Exception {
        Method m = RegistryHeartbeatClient.class.getDeclaredMethod("buildPayload");
        m.setAccessible(true);
        return m.invoke(client);
    }
}

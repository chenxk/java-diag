package com.javadiag.mcp.cluster;

import com.javadiag.mcp.config.DiagConfig;
import com.javadiag.mcp.platform.JsonCodec;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static Object invokeBuildPayload(RegistryHeartbeatClient client) throws Exception {
        Method m = RegistryHeartbeatClient.class.getDeclaredMethod("buildPayload");
        m.setAccessible(true);
        return m.invoke(client);
    }
}

package com.javadiag.mcp.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}

package com.javadiag.mcp.process;

import com.javadiag.mcp.cluster.JvmDirectoryBuilder;
import com.javadiag.mcp.config.DiagConfig;
import com.javadiag.mcp.platform.JsonCodec;
import com.javadiag.mcp.tools.ToolSupport;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveDiscoverySmokeTest {

    @Test
    void toolSupportDiscoveryIncludesReportableJvm() {
        Map<String, String> env = new HashMap<>(System.getenv());
        env.putIfAbsent(DiagConfig.ENV_AUTH_TOKEN, "test-auth");
        env.putIfAbsent(DiagConfig.ENV_REGISTRY_REGISTER_TOKEN, "test-reg");
        DiagConfig config = DiagConfig.load(env, List.of());
        ToolSupport support = ToolSupport.create(config, JsonCodec.createDefault());
        List<JavaProcessInfo> processes = support.processService().listProcesses(null, null);
        assertFalse(processes.isEmpty(), "expected jps-backed discovery on this host");

        boolean anyJarOrFqcn =
                processes.stream().anyMatch(p -> JavaProcessService.isJavaMainToken(p.mainClassOrJar()));
        assertTrue(anyJarOrFqcn, "expected at least one jar/FQCN main: " + processes);

        JvmDirectoryBuilder builder = new JvmDirectoryBuilder(support.processService(), AppNameMapper.empty());
        List<Map<String, Object>> apps = builder.buildApps();
        assertFalse(apps.isEmpty(), "heartbeat apps[] must not be empty when JVMs are present");
    }

    @Test
    void bareServiceWithoutJpsReturnsEmptyNotExeGarbage() {
        JavaProcessService bare = new JavaProcessService(Optional.empty());
        List<JavaProcessInfo> list = bare.listProcesses(null, null);
        for (JavaProcessInfo info : list) {
            assertTrue(
                    JavaProcessService.isReportableJvm(info),
                    "bare discovery must not surface exe-only rows: " + info);
        }
    }
}

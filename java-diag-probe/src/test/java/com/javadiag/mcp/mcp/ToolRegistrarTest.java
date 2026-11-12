package com.javadiag.mcp.mcp;

import com.javadiag.mcp.config.DiagConfig;
import com.javadiag.mcp.platform.JsonCodec;
import com.javadiag.mcp.tools.ToolSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistrarTest {

    @TempDir
    Path tempDir;

    @Test
    void registersAllP0Tools() {
        DiagConfig config = DiagConfig.fromEnvironment(Map.of(
                DiagConfig.ENV_AUTH_TOKEN, "test-auth",
                DiagConfig.ENV_REGISTRY_REGISTER_TOKEN, "test-reg",
                DiagConfig.ENV_ALLOWED_LOG_DIRS, tempDir.toString(),
                DiagConfig.ENV_AUDIT_PATH, tempDir.resolve("audit.log").toString()));
        ToolRegistrar registrar = new ToolRegistrar(ToolSupport.create(config, JsonCodec.createDefault()));
        List<String> names = registrar.toolNames();
        assertEquals(15, names.size());
        assertTrue(names.contains("diag.ping"));
        assertTrue(names.contains("diag.list_processes"));
        assertTrue(names.contains("diag.resolve_java_pid"));
        assertTrue(names.contains("diag.log_paths"));
        assertTrue(names.contains("diag.tail_log"));
        assertTrue(names.contains("diag.grep_log"));
        assertTrue(names.contains("diag.cluster_errors"));
        assertTrue(names.contains("diag.runtime_summary"));
        assertTrue(names.contains("diag.jstat"));
        assertTrue(names.contains("diag.thread_dump"));
        assertTrue(names.contains("diag.vm_flags"));
        assertTrue(names.contains("diag.gc_histo"));
        assertTrue(names.contains("diag.heap_dump"));
        assertTrue(names.contains("diag.cpu_sample"));
        assertTrue(names.contains("diag.vm_info"));
    }
}

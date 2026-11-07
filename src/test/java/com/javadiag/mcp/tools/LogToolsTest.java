package com.javadiag.mcp.tools;

import com.javadiag.mcp.config.DiagConfig;
import com.javadiag.mcp.model.ErrorCode;
import com.javadiag.mcp.model.ToolEnvelope;
import com.javadiag.mcp.platform.JsonCodec;
import com.javadiag.mcp.platform.PathGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogToolsTest {

    @TempDir
    Path tempDir;

    @Test
    void parseWindow() {
        assertEquals(Duration.ofMinutes(15), LogTools.parseWindow("15m"));
        assertEquals(Duration.ofHours(1), LogTools.parseWindow("1h"));
    }

    @Test
    void grepAndTailRespectWhitelistAndTruncate() throws Exception {
        Path logs = tempDir.resolve("logs");
        Files.createDirectories(logs);
        Path file = logs.resolve("app.log");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("line-").append(i).append(" ERROR boom\n");
        }
        Files.writeString(file, sb.toString());

        Map<String, String> env = Map.of(
                DiagConfig.ENV_ALLOWED_LOG_DIRS, logs.toString(),
                DiagConfig.ENV_MAX_OUTPUT_BYTES, "2048",
                DiagConfig.ENV_AUDIT_PATH, tempDir.resolve("audit.log").toString());
        DiagConfig config = DiagConfig.fromEnvironment(env);
        JsonCodec codec = JsonCodec.createDefault();
        ToolSupport support = ToolSupport.create(config, codec);
        LogTools logTools = new LogTools(support);

        ToolEnvelope tail = logTools.tailLog(Map.of("path", file.toString(), "lines", 20)).envelope();
        assertTrue(tail.ok());
        assertEquals("diag.tail_log", tail.tool());

        ToolEnvelope grep = logTools.grepLog(Map.of(
                "path", file.toString(),
                "pattern", "ERROR",
                "maxMatches", 5)).envelope();
        assertTrue(grep.ok());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) grep.data();
        assertEquals(5, data.get("matchCount"));
        assertTrue(grep.truncated());

        Path outside = tempDir.resolve("secret.log");
        Files.writeString(outside, "nope");
        ToolEnvelope denied = logTools.tailLog(Map.of("path", outside.toString(), "lines", 10)).envelope();
        assertFalse(denied.ok());
        assertEquals(ErrorCode.PATH_NOT_ALLOWED.name(), denied.error().code());
    }

    @Test
    void pathGuardUsedByConfigDefaults() {
        PathGuard guard = new PathGuard(List.of(tempDir));
        assertTrue(guard.isUnderAllowed(tempDir.resolve("a.log")));
    }
}

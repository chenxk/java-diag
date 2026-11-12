package com.javadiag.mcp.cluster;

import com.javadiag.mcp.process.AppNameMapper;
import com.javadiag.mcp.process.JavaProcessInfo;
import com.javadiag.mcp.process.JavaProcessService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmDirectoryBuilderTest {

    @Test
    void buildAppsFromDiscoveredJvmRows() {
        List<JavaProcessInfo> processes =
                List.of(
                        new JavaProcessInfo(
                                25096L,
                                "java-diag-probe.jar",
                                "java-diag-probe.jar",
                                null,
                                null,
                                null,
                                "java-diag-probe.jar"),
                        new JavaProcessInfo(
                                18780L,
                                "java-diag-gateway.jar",
                                "java-diag-gateway.jar",
                                null,
                                null,
                                null,
                                "java-diag-gateway.jar"),
                        new JavaProcessInfo(
                                28208L,
                                "com.intellij.idea.Main",
                                "com.intellij.idea.Main",
                                null,
                                null,
                                null,
                                "com.intellij.idea.Main -Xmx2048m"));
        JvmDirectoryBuilder builder = new JvmDirectoryBuilder(new JavaProcessService(Optional.empty()), AppNameMapper.empty());

        List<Map<String, Object>> apps = builder.buildAppsFrom(processes);

        assertEquals(3, apps.size());
        assertEquals(18780L, ((Number) apps.get(0).get("pid")).longValue());
        assertEquals("java-diag-gateway.jar", apps.get(0).get("name"));
        assertEquals("java-diag-probe.jar", apps.get(1).get("name"));
        assertEquals("Main", apps.get(2).get("name"));
        for (Map<String, Object> app : apps) {
            assertFalse("exe".equalsIgnoreCase(String.valueOf(app.get("name"))));
        }
    }

    @Test
    void buildAppsFiltersBareJavaExeEntries() {
        List<JavaProcessInfo> mixed = new ArrayList<>();
        mixed.add(
                new JavaProcessInfo(
                        99L,
                        "C:\\Program Files\\Java\\jdk-17\\bin\\java.exe",
                        "C:\\Program Files\\Java\\jdk-17\\bin\\java.exe",
                        null,
                        null,
                        null,
                        "C:\\Program Files\\Java\\jdk-17\\bin\\java.exe"));
        mixed.add(
                new JavaProcessInfo(
                        25096L,
                        "java-diag-probe.jar",
                        "java-diag-probe.jar",
                        null,
                        null,
                        null,
                        "java-diag-probe.jar"));

        JvmDirectoryBuilder builder = new JvmDirectoryBuilder(new JavaProcessService(Optional.empty()), AppNameMapper.empty());
        List<Map<String, Object>> apps = builder.buildAppsFrom(mixed);

        assertEquals(1, apps.size());
        assertEquals("java-diag-probe.jar", apps.get(0).get("name"));
    }

    @Test
    void buildAppsOmitsBareJavaExeFromLiveDiscovery() {
        JavaProcessService bare = new JavaProcessService(Optional.empty());
        JvmDirectoryBuilder builder = new JvmDirectoryBuilder(bare, AppNameMapper.empty());
        List<Map<String, Object>> apps = builder.buildApps();
        for (Map<String, Object> app : apps) {
            assertTrue(JavaProcessService.isReportableJvm(jvmRow(app)));
            assertFalse("exe".equalsIgnoreCase(String.valueOf(app.get("name"))));
        }
    }

    private static JavaProcessInfo jvmRow(Map<String, Object> app) {
        long pid = ((Number) app.get("pid")).longValue();
        String main = String.valueOf(app.get("mainClass"));
        Object summary = app.get("commandLineSummary");
        String cmd = summary == null ? main : String.valueOf(summary);
        return new JavaProcessInfo(pid, main, main, null, null, null, cmd);
    }
}

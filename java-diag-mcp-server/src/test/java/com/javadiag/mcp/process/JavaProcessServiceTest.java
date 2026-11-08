package com.javadiag.mcp.process;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaProcessServiceTest {

    @Test
    void extractMainFromJarAndClass() {
        assertEquals(
                "app.jar",
                JavaProcessService.extractMain("C:\\Java\\bin\\java.exe -Xmx1g -jar app.jar --spring.profiles.active=prod"));
        assertEquals(
                "com.example.Main",
                JavaProcessService.extractMain("/usr/bin/java -cp out com.example.Main arg"));
        assertEquals(
                "java-diag-mcp-server.jar",
                JavaProcessService.extractMain("java-diag-mcp-server.jar"));
        assertEquals(
                "",
                JavaProcessService.extractMain("C:\\Program Files\\Java\\jdk-17\\bin\\java.exe"));
        assertEquals(
                "C:\\apps\\java-diag-mcp-server.jar",
                JavaProcessService.extractMain(
                        "C:\\Program Files\\Java\\jdk-17\\bin\\java.exe -jar C:\\apps\\java-diag-mcp-server.jar"));
    }

    @Test
    void isReportableJvmRejectsBareLauncher() {
        JavaProcessInfo bare =
                new JavaProcessInfo(
                        1L,
                        "C:\\Program Files\\Java\\jdk-17\\bin\\java.exe",
                        "C:\\Program Files\\Java\\jdk-17\\bin\\java.exe",
                        null,
                        null,
                        null,
                        "C:\\Program Files\\Java\\jdk-17\\bin\\java.exe");
        assertFalse(JavaProcessService.isReportableJvm(bare));

        JavaProcessInfo jar =
                new JavaProcessInfo(2L, "java-diag-mcp-server.jar", "java-diag-mcp-server.jar", null, null, null, "…");
        assertTrue(JavaProcessService.isReportableJvm(jar));
    }

    @Test
    void isJavaProcessRecognizesWindowsAndUnixLaunchers() {
        assertTrue(JavaProcessService.isJavaProcess("C:\\Program Files\\Java\\jdk-17\\bin\\java.exe -jar app.jar"));
        assertTrue(JavaProcessService.isJavaProcess("/usr/lib/jvm/java-17/bin/java -cp . com.foo.Bar"));
        assertTrue(JavaProcessService.isJavaProcess("java -jar app.jar"));
        assertFalse(JavaProcessService.isJavaProcess("python server.py"));
        assertFalse(JavaProcessService.isJavaProcess(""));
    }

    @Test
    void isJavaMainTokenAcceptsJarAndFqcn() {
        assertTrue(JavaProcessService.isJavaMainToken("app.jar"));
        assertTrue(JavaProcessService.isJavaMainToken("com.intellij.idea.Main"));
        assertFalse(JavaProcessService.isJavaMainToken("server.py"));
        assertFalse(JavaProcessService.isJavaMainToken("run.sh"));
    }

    @Test
    void parseJpsOutput() {
        String stdout = """
                8228 java-diag-mcp-server.jar
                27576 com.intellij.idea.Main -Xmx2048m
                3844 jdk.jcmd/sun.tools.jps.Jps -lvm
                """;
        List<JavaProcessInfo> list = JavaProcessService.parseJpsOutput(stdout);
        assertEquals(2, list.size());
        assertEquals(8228L, list.get(0).pid());
        assertEquals("java-diag-mcp-server.jar", list.get(0).mainClassOrJar());
        assertEquals(27576L, list.get(1).pid());
        assertEquals("com.intellij.idea.Main", list.get(1).mainClassOrJar());
        assertTrue(list.get(1).commandLine().contains("-Xmx2048m"));
    }

    @Test
    void parseJpsOutputFeedsReportableRegistryApps() {
        String stdout = """
                25096 java-diag-mcp-server.jar
                18780 java-diag-gateway.jar
                """;
        List<JavaProcessInfo> fromJps = JavaProcessService.parseJpsOutput(stdout);
        assertEquals(2, fromJps.size());
        for (JavaProcessInfo info : fromJps) {
            assertTrue(JavaProcessService.isReportableJvm(info));
            assertFalse("exe".equalsIgnoreCase(AppNameMapper.fallbackShortName(info.mainClassOrJar())));
        }
    }

    @Test
    void listProcessesWithoutToolchainFallsBackGracefully() {
        JavaProcessService service = new JavaProcessService(Optional.empty());
        // Should not throw; may return empty or ProcessHandle results depending on OS permissions.
        List<JavaProcessInfo> list = service.listProcesses(null, null);
        assertTrue(list != null);
    }
}

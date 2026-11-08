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
    void extractMainSkipsJvmFlagsBetweenJarAndPath() {
        // Real-world JDK8 launch: flags incorrectly placed after -jar
        assertEquals(
                "/server/apps/springboot-storage/springboot-storage-1.0.0-SNAPSHOT.jar",
                JavaProcessService.extractMain(
                        "java -jar -XX:+PrintGCDetails -Xms2g"
                                + " /server/apps/springboot-storage/springboot-storage-1.0.0-SNAPSHOT.jar"));
        assertEquals(
                "/path/app.jar",
                JavaProcessService.extractMain("java -jar -XX:+PrintGCDetails -Xms2g /path/app.jar"));
        // Standard form must not regress
        assertEquals(
                "/path/app.jar",
                JavaProcessService.extractMain("java -jar /path/app.jar"));
        // Value-taking option misplaced after -jar
        assertEquals(
                "app.jar",
                JavaProcessService.extractMain("java -jar -cp /tmp/extra.jar app.jar"));
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
    void mergeByPidUnionsProcAndJpsThenFilterKeepsBoth() {
        // /proc sees container jar A; host jps sees JDK8 app B (e.g. springboot-storage) —
        // discoverAll must union by pid, not short-circuit on non-empty /proc.
        JavaProcessInfo fromProc =
                new JavaProcessInfo(
                        100L,
                        "/app/app.jar",
                        "/app/app.jar",
                        "0",
                        1L,
                        1024L,
                        "/usr/bin/java -jar /app/app.jar");
        JavaProcessInfo fromJps =
                new JavaProcessInfo(
                        1081256L,
                        "springboot-storage.jar",
                        "springboot-storage.jar",
                        null,
                        2L,
                        null,
                        "springboot-storage.jar -Xmx512m");
        List<JavaProcessInfo> merged =
                JavaProcessService.mergeByPid(List.of(fromProc), List.of(fromJps));
        assertEquals(2, merged.size());
        assertTrue(merged.stream().anyMatch(p -> p.pid() == 100L));
        assertTrue(merged.stream().anyMatch(p -> p.pid() == 1081256L));

        long reportable =
                merged.stream()
                        .map(JavaProcessService::enrichMainFromCommandLine)
                        .filter(JavaProcessService::isReportableJvm)
                        .count();
        assertEquals(2, reportable, "proc A + jps B must both survive filterAndEnrich");
    }

    @Test
    void mergeByPidPrimaryWinsOnConflict() {
        JavaProcessInfo proc =
                new JavaProcessInfo(42L, "from-proc.jar", "from-proc.jar", "uid", 1L, 10L, "java -jar from-proc.jar");
        JavaProcessInfo jps =
                new JavaProcessInfo(42L, "from-jps.jar", "from-jps.jar", null, 2L, null, "from-jps.jar");
        List<JavaProcessInfo> merged = JavaProcessService.mergeByPid(List.of(proc), List.of(jps));
        assertEquals(1, merged.size());
        assertEquals("from-proc.jar", merged.get(0).mainClassOrJar());
        assertEquals("uid", merged.get(0).user());
    }

    @Test
    void mergeByPidAdoptsSecondaryMainWhenPrimaryUnreportable() {
        // /proc stored wrong flag-as-main; cmdline still has the jar — merge repairs main, keeps proc meta.
        JavaProcessInfo fromProc =
                new JavaProcessInfo(
                        1081256L,
                        "-XX:+PrintGCDetails",
                        "-XX:+PrintGCDetails",
                        "0",
                        1L,
                        2048L,
                        "java -jar -XX:+PrintGCDetails -Xms2g"
                                + " /server/apps/springboot-storage/springboot-storage-1.0.0-SNAPSHOT.jar");
        JavaProcessInfo fromJps =
                new JavaProcessInfo(
                        1081256L,
                        "springboot-storage-1.0.0-SNAPSHOT.jar",
                        "springboot-storage-1.0.0-SNAPSHOT.jar",
                        null,
                        2L,
                        null,
                        "springboot-storage-1.0.0-SNAPSHOT.jar");
        List<JavaProcessInfo> merged =
                JavaProcessService.mergeByPid(List.of(fromProc), List.of(fromJps));
        assertEquals(1, merged.size());
        JavaProcessInfo one = merged.get(0);
        assertEquals("0", one.user());
        assertEquals(2048L, one.rssKb());
        assertEquals(
                "/server/apps/springboot-storage/springboot-storage-1.0.0-SNAPSHOT.jar",
                one.mainClassOrJar());
        assertTrue(JavaProcessService.isReportableJvm(one));

        // proc main illegal AND cmdline cannot yield a reportable entry → adopt jps main
        JavaProcessInfo badProcNoJarInCmd =
                new JavaProcessInfo(
                        99L,
                        "-XX:+PrintGCDetails",
                        "-XX:+PrintGCDetails",
                        "uid99",
                        1L,
                        100L,
                        "java -XX:+PrintGCDetails");
        JavaProcessInfo goodJps =
                new JavaProcessInfo(99L, "good-app.jar", "good-app.jar", null, 2L, null, "good-app.jar");
        List<JavaProcessInfo> rescued =
                JavaProcessService.mergeByPid(List.of(badProcNoJarInCmd), List.of(goodJps));
        assertEquals(1, rescued.size());
        assertEquals("good-app.jar", rescued.get(0).mainClassOrJar());
        assertEquals("uid99", rescued.get(0).user());
        assertTrue(JavaProcessService.isReportableJvm(rescued.get(0)));
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
    void parseJpsOutputJarRowsSurviveReportableFilter() {
        String stdout = """
                8228 C:\\apps\\java-diag-mcp-server.jar
                9000 java-diag-gateway.jar -Xmx512m
                42 C:\\Program Files\\Java\\jdk-17\\bin\\java.exe
                """;
        List<JavaProcessInfo> fromJps = JavaProcessService.parseJpsOutput(stdout);
        long reportable =
                fromJps.stream().filter(JavaProcessService::isReportableJvm).count();
        assertEquals(2, reportable, "jps lines with .jar must be included in apps");
        assertTrue(fromJps.stream().anyMatch(p -> p.mainClassOrJar().contains("java-diag-mcp-server.jar")));
        assertTrue(fromJps.stream().anyMatch(p -> "java-diag-gateway.jar".equals(p.mainClassOrJar())));
    }

    @Test
    void listProcessesWithoutToolchainFallsBackGracefully() {
        JavaProcessService service = new JavaProcessService(Optional.empty());
        // Should not throw; may return empty or ProcessHandle results depending on OS permissions.
        List<JavaProcessInfo> list = service.listProcesses(null, null);
        assertTrue(list != null);
    }
}

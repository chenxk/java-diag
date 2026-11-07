package com.javadiag.mcp.platform;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdkToolchainTest {

    @Test
    void parseMajorVersions() {
        assertEquals(8, JdkToolchain.parseMajor("1.8.0_402"));
        assertEquals(11, JdkToolchain.parseMajor("11.0.22"));
        assertEquals(17, JdkToolchain.parseMajor("17"));
        assertEquals(21, JdkToolchain.parseMajor("21.0.12.1"));
    }

    @Test
    void javaHomeFromJavaBinary() {
        Path javaHome = Path.of(System.getProperty("java.home"));
        Path javaBin = javaHome.resolve("bin").resolve(OsPlatform.executableName("java"));
        Optional<Path> home = JdkToolchain.javaHomeFromJavaBinary(javaBin);
        assertTrue(home.isPresent());
        // java.home may itself be the jre dir nested under a JDK; parent-of-bin is the home we return
        assertEquals(javaBin.getParent().getParent(), home.get());
    }

    @Test
    void resolveFallbackFromRunningJvm() {
        JdkToolchain toolchain = new JdkToolchain();
        JdkToolchain.Toolchain tc = toolchain.resolveFallback();
        assertTrue(OsPlatform.isRunnable(tc.jcmd()) || OsPlatform.isRunnable(tc.jps()));
        assertTrue(tc.majorVersion() >= 8);
    }
}

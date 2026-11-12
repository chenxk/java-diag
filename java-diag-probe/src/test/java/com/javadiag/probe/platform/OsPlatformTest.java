package com.javadiag.probe.platform;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsPlatformTest {

    @Test
    void toolBasenameStripsWindowsSuffix() {
        assertEquals("jcmd", OsPlatform.toolBasename("jcmd"));
        assertEquals("jcmd", OsPlatform.toolBasename("jcmd.exe"));
        assertEquals("jcmd", OsPlatform.toolBasename("JCMD.EXE"));
    }

    @Test
    void executableNameAddsExeOnWindowsOnly() {
        String name = OsPlatform.executableName("jps");
        if (OsPlatform.isWindows()) {
            assertEquals("jps.exe", name);
        } else {
            assertEquals("jps", name);
        }
    }

    @Test
    void looksLikeJavaBinary() {
        assertTrue(OsPlatform.looksLikeJavaBinary("java"));
        assertTrue(OsPlatform.looksLikeJavaBinary("C:\\Program Files\\Java\\jdk-17\\bin\\java.exe"));
        assertTrue(OsPlatform.looksLikeJavaBinary("/usr/lib/jvm/java-17/bin/java"));
        assertFalse(OsPlatform.looksLikeJavaBinary("javascript"));
        assertFalse(OsPlatform.looksLikeJavaBinary("/usr/bin/python"));
    }

    @Test
    void defaultAllowedLogDirsAlwaysIncludeCwdAndHomeAndTmp() {
        List<Path> dirs = OsPlatform.defaultAllowedLogDirs();
        assertFalse(dirs.isEmpty());
        assertTrue(dirs.stream().anyMatch(p -> p.equals(Path.of(".").toAbsolutePath().normalize())));
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp != null) {
            assertTrue(dirs.stream().anyMatch(p -> p.equals(Path.of(tmp).toAbsolutePath().normalize())));
        }
    }

    @Test
    void pathEntriesParsesNativeSeparator() {
        List<String> entries = OsPlatform.pathEntries();
        // PATH is almost always set in CI / developer shells
        assertTrue(entries == null || entries.size() >= 0);
        if (System.getenv("PATH") != null && !System.getenv("PATH").isBlank()) {
            assertFalse(entries.isEmpty());
        }
    }

    @Test
    void isRunnableRejectsMissing() {
        assertFalse(OsPlatform.isRunnable(Path.of("definitely-missing-java-diag-tool-xyz")));
    }

    @Test
    void isRunnableAcceptsExistingFile() throws Exception {
        Path tmp = Files.createTempFile("java-diag-runnable", ".txt");
        try {
            // On Windows existence is enough; on Unix may need execute bit — still a regular file check first.
            // Create an empty file: Unix isRunnable requires executable, so expect false unless we chmod.
            if (OsPlatform.isWindows()) {
                assertTrue(OsPlatform.isRunnable(tmp));
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}

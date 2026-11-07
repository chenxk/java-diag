package com.javadiag.mcp.platform;

import com.javadiag.mcp.model.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathGuardTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsDotDot() throws Exception {
        Path allowed = tempDir.resolve("logs");
        Files.createDirectories(allowed);
        PathGuard guard = new PathGuard(List.of(allowed));

        DiagException ex = assertThrows(DiagException.class,
                () -> guard.resolveAllowed(allowed.resolve("a/../secret.log").toString()));
        assertEquals(ErrorCode.PATH_NOT_ALLOWED, ex.code());
        assertTrue(ex.getMessage().contains(".."));
    }

    @Test
    void rejectsOutsideWhitelist() throws Exception {
        Path allowed = tempDir.resolve("logs");
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(allowed);
        Files.createDirectories(outside);
        Path file = Files.writeString(outside.resolve("app.log"), "x");

        PathGuard guard = new PathGuard(List.of(allowed));
        DiagException ex = assertThrows(DiagException.class, () -> guard.resolveAllowed(file.toString()));
        assertEquals(ErrorCode.PATH_NOT_ALLOWED, ex.code());
    }

    @Test
    void allowsWhitelistedFile() throws Exception {
        Path allowed = tempDir.resolve("logs");
        Files.createDirectories(allowed);
        Path file = Files.writeString(allowed.resolve("app.log"), "hello");

        PathGuard guard = new PathGuard(List.of(allowed));
        Path resolved = guard.resolveAllowed(file.toString());
        assertEquals(file.toRealPath(), resolved);
    }
}

package com.javadiag.mcp.platform;

import com.javadiag.mcp.model.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessRunnerTimeoutTest {

    @TempDir
    Path tempDir;

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void timesOutAndTruncatesOutput() throws Exception {
        Path fakeJcmd = tempDir.resolve("jcmd");
        Files.writeString(fakeJcmd, """
                #!/bin/sh
                # emit more than max bytes then sleep
                i=0
                while [ "$i" -lt 200 ]; do
                  printf 'xxxxxxxxxxxxxxxxxxxx'
                  i=$((i+1))
                done
                sleep 30
                """);
        Set<PosixFilePermission> perms = EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(fakeJcmd, perms);

        ProcessRunner runner = new ProcessRunner(64, 500);
        DiagException ex = assertThrows(DiagException.class,
                () -> runner.run(List.of(fakeJcmd.toString(), "1", "VM.version")));
        assertEquals(ErrorCode.TIMEOUT, ex.code());

        // separate truncation case: short-lived flood within timeout
        Path flood = tempDir.resolve("jstat");
        Files.writeString(flood, """
                #!/bin/sh
                i=0
                while [ "$i" -lt 500 ]; do
                  printf 'yyyyyyyyyyyyyyyyyyyy'
                  i=$((i+1))
                done
                """);
        Files.setPosixFilePermissions(flood, perms);
        CommandResult result = runner.run(List.of(flood.toString(), "1"), 5_000);
        assertTrue(result.truncated());
        assertTrue(result.stdout().getBytes().length <= 64);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void timesOutOnWindowsUsingCmdScript() throws Exception {
        Path fakeJcmd = tempDir.resolve("jcmd.cmd");
        Files.writeString(fakeJcmd, """
                @echo off
                :loop
                echo xxxxxxxxxxxxxxxxxxxx
                ping -n 2 127.0.0.1 >nul
                goto loop
                """);
        ProcessRunner runner = new ProcessRunner(64, 800);
        DiagException ex = assertThrows(DiagException.class,
                () -> runner.run(List.of(fakeJcmd.toString(), "1", "VM.version")));
        assertEquals(ErrorCode.TIMEOUT, ex.code());
    }

}

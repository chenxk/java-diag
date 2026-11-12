package com.javadiag.probe.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmOutputParserTest {

    @Test
    void parsesThreadStates() {
        String dump = """
                "main" #1 runnable
                "worker" #2 waiting
                "blocked" #3 blocked
                """;
        Map<String, Object> summary = JvmOutputParser.parseThreadDumpSummary(dump);
        assertEquals(3, summary.get("threadCount"));
        assertEquals(1, summary.get("runnableCount"));
        assertEquals(1, summary.get("waitingCount"));
        assertEquals(1, summary.get("blockedCount"));
        assertFalse((Boolean) summary.get("deadlockDetected"));
    }

    @Test
    void detectsDeadlockMention() {
        Map<String, Object> summary = JvmOutputParser.parseThreadDumpSummary("Found one Java-level deadlock");
        assertTrue((Boolean) summary.get("deadlockDetected"));
    }

    @Test
    void compareThreadDumpsComputesDelta() {
        String before = "\"a\" #1 runnable\n\"b\" #2 waiting\n";
        String after = "\"a\" #1 runnable\n";
        Map<String, Object> delta = JvmOutputParser.compareThreadDumps(before, after);
        assertEquals(2, delta.get("threadCountBefore"));
        assertEquals(1, delta.get("threadCountAfter"));
        assertEquals(-1, delta.get("threadCountDelta"));
    }
}
